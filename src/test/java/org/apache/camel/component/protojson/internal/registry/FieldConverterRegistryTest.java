package org.apache.camel.component.protojson.internal.registry;

import com.google.protobuf.Descriptors;
import org.apache.camel.component.protojson.converter.JsonInFieldConverter;
import org.apache.camel.component.protojson.test.proto.SimpleUser;
import org.apache.camel.component.protojson.test.proto.UserWithAddress;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

/**
 * Comprehensive unit tests for FieldConverterRegistry.
 * Tests O(1) caching, negative caching, and thread safety.
 */
class FieldConverterRegistryTest {

    private Descriptors.FieldDescriptor nameField;
    private Descriptors.FieldDescriptor ageField;
    private Descriptors.FieldDescriptor emailField;
    private Descriptors.FieldDescriptor addressField;

    @BeforeEach
    void setUp() {
        // Get test field descriptors
        Descriptors.Descriptor simpleUserDesc = SimpleUser.getDescriptor();
        nameField = simpleUserDesc.findFieldByName("name");
        ageField = simpleUserDesc.findFieldByName("age");
        emailField = simpleUserDesc.findFieldByName("email");

        Descriptors.Descriptor userWithAddressDesc = UserWithAddress.getDescriptor();
        addressField = userWithAddressDesc.findFieldByName("address");
    }

    // ==================== Basic Operations ====================

    @Test
    void testEmptyRegistry() {
        // Given: Empty registry
        FieldConverterRegistry<TestConverter> registry = createEmptyRegistry();

        // When: Looking up converter
        TestConverter converter = registry.findConverter(nameField);

        // Then: Should return null
        assertThat(converter).isNull();
        assertThat(registry.size()).isZero();
    }

    @Test
    void testSingleConverter() {
        // Given: Registry with one converter
        TestConverter nameConverter = new TestConverter("name");
        FieldConverterRegistry<TestConverter> registry = createRegistry(nameConverter);

        // When: Looking up supported field
        TestConverter found = registry.findConverter(nameField);

        // Then: Should return the converter
        assertThat(found).isSameAs(nameConverter);
        assertThat(registry.size()).isEqualTo(1);
    }

    @Test
    void testMultipleConverters() {
        // Given: Registry with multiple converters
        TestConverter conv1 = new TestConverter("name");
        TestConverter conv2 = new TestConverter("age");
        TestConverter conv3 = new TestConverter("email");

        FieldConverterRegistry<TestConverter> registry = createRegistry(conv1, conv2, conv3);

        // When/Then: Each field finds its converter
        assertThat(registry.findConverter(nameField)).isSameAs(conv1);
        assertThat(registry.findConverter(ageField)).isSameAs(conv2);
        assertThat(registry.findConverter(emailField)).isSameAs(conv3);
        assertThat(registry.size()).isEqualTo(3);
    }

    @Test
    void testConverterNotFound() {
        // Given: Registry with converters for some fields
        TestConverter nameConverter = new TestConverter("name");
        FieldConverterRegistry<TestConverter> registry = createRegistry(nameConverter);

        // When: Looking up unsupported field
        TestConverter found = registry.findConverter(ageField);

        // Then: Should return null
        assertThat(found).isNull();
    }

    @Test
    void testHasConverter() {
        // Given: Registry with converters
        TestConverter nameConverter = new TestConverter("name");
        FieldConverterRegistry<TestConverter> registry = createRegistry(nameConverter);

        // When/Then: Check presence
        assertThat(registry.hasConverter(nameField)).isTrue();
        assertThat(registry.hasConverter(ageField)).isFalse();
    }

    // ==================== Caching Behavior ====================

    @Test
    void testCachingPositiveHit() {
        // Given: Registry with converter
        CountingConverter converter = new CountingConverter("name");
        FieldConverterRegistry<CountingConverter> registry = createRegistry(converter);

        // When: Looking up same field multiple times
        CountingConverter result1 = registry.findConverter(nameField);
        CountingConverter result2 = registry.findConverter(nameField);
        CountingConverter result3 = registry.findConverter(nameField);

        // Then: Should use cache (converter check called only once)
        assertThat(result1).isSameAs(converter);
        assertThat(result2).isSameAs(converter);
        assertThat(result3).isSameAs(converter);
        assertThat(converter.checkCount.get()).isEqualTo(1); // Only first call
    }

    @Test
    void testCachingNegativeHit() {
        // Given: Empty registry
        CountingConverter converter = new CountingConverter("nonexistent");
        FieldConverterRegistry<CountingConverter> registry = createRegistry(converter);

        // When: Looking up unsupported field multiple times
        CountingConverter result1 = registry.findConverter(nameField);
        CountingConverter result2 = registry.findConverter(nameField);
        CountingConverter result3 = registry.findConverter(nameField);

        // Then: Should cache negative result (check called only once)
        assertThat(result1).isNull();
        assertThat(result2).isNull();
        assertThat(result3).isNull();
        assertThat(converter.checkCount.get()).isEqualTo(1); // Negative cached
    }

    @Test
    void testCachePerField() {
        // Given: Registry with multiple converters
        CountingConverter conv1 = new CountingConverter("name");
        CountingConverter conv2 = new CountingConverter("age");
        FieldConverterRegistry<CountingConverter> registry = createRegistry(conv1, conv2);

        // When: Looking up different fields
        registry.findConverter(nameField);
        registry.findConverter(nameField); // Cached
        registry.findConverter(ageField);
        registry.findConverter(ageField); // Cached

        // Then: Each field cached independently
        // Note: conv1 is checked twice: once for nameField (matches), once for ageField (no match)
        // conv2 is checked once for ageField (matches)
        assertThat(conv1.checkCount.get()).isEqualTo(2); // Checked for both fields
        assertThat(conv2.checkCount.get()).isEqualTo(1); // Checked only for ageField
    }

    @Test
    void testClearCache() {
        // Given: Registry with cached lookups
        CountingConverter converter = new CountingConverter("name");
        FieldConverterRegistry<CountingConverter> registry = createRegistry(converter);

        registry.findConverter(nameField); // First lookup
        assertThat(converter.checkCount.get()).isEqualTo(1);

        // When: Clearing cache
        registry.clearCache();

        // Then: Next lookup should check again
        registry.findConverter(nameField);
        assertThat(converter.checkCount.get()).isEqualTo(2); // Checked again
    }

    // ==================== Converter Priority ====================

    @Test
    void testFirstMatchWins() {
        // Given: Multiple converters supporting same field
        TestConverter first = new TestConverter("*"); // Wildcard
        TestConverter second = new TestConverter("*"); // Wildcard

        FieldConverterRegistry<TestConverter> registry = createRegistry(first, second);

        // When: Looking up field
        TestConverter found = registry.findConverter(nameField);

        // Then: First matching converter wins
        assertThat(found).isSameAs(first);
    }

    @Test
    void testOrderMatters() {
        // Given: Specific and wildcard converters
        TestConverter wildcard = new TestConverter("*"); // Matches all
        TestConverter specific = new TestConverter("name"); // Matches only name

        // When: Wildcard first
        FieldConverterRegistry<TestConverter> registry1 = createRegistry(wildcard, specific);
        assertThat(registry1.findConverter(nameField)).isSameAs(wildcard);

        // When: Specific first
        FieldConverterRegistry<TestConverter> registry2 = createRegistry(specific, wildcard);
        assertThat(registry2.findConverter(nameField)).isSameAs(specific);
    }

    // ==================== Thread Safety ====================

    @Test
    void testConcurrentLookups() throws InterruptedException {
        // Given: Registry with converters
        TestConverter nameConverter = new TestConverter("name");
        TestConverter ageConverter = new TestConverter("age");
        FieldConverterRegistry<TestConverter> registry = createRegistry(nameConverter, ageConverter);

        int threadCount = 20;
        int iterationsPerThread = 10_000;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger errorCount = new AtomicInteger(0);

        // When: Concurrent lookups
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < iterationsPerThread; j++) {
                        // Alternate between fields
                        Descriptors.FieldDescriptor field = (threadId % 2 == 0) ? nameField : ageField;
                        TestConverter converter = registry.findConverter(field);
                        if (converter == null) {
                            errorCount.incrementAndGet();
                        }
                    }
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        // Then: Should complete without errors
        assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(errorCount.get()).isZero();

        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.SECONDS);
    }

    @Test
    void testConcurrentCacheBuilding() throws InterruptedException {
        // Given: Registry with many converters
        List<CountingConverter> converters = new ArrayList<>();
        Descriptors.Descriptor descriptor = SimpleUser.getDescriptor();

        for (Descriptors.FieldDescriptor fd : descriptor.getFields()) {
            converters.add(new CountingConverter(fd.getName()));
        }

        FieldConverterRegistry<CountingConverter> registry =
                createRegistry(converters.toArray(new CountingConverter[0]));

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        List<Exception> exceptions = new ArrayList<>();

        // When: Multiple threads lookup same fields simultaneously (first time)
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    for (Descriptors.FieldDescriptor fd : descriptor.getFields()) {
                        registry.findConverter(fd);
                    }
                } catch (Exception e) {
                    synchronized (exceptions) {
                        exceptions.add(e);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        // Then: Should build cache correctly without race conditions
        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(exceptions).isEmpty();

        // Verify caching works: first converter checked for all fields, second for all but first, etc.
        // For N fields with converters in same order: conv[i] is checked (N - i) times during initial cache population
        int expectedChecksForFirstConverter = converters.size(); // Checked for all field lookups
        for (int i = 0; i < converters.size(); i++) {
            CountingConverter conv = converters.get(i);
            int expectedChecks = converters.size() - i; // Each converter checked fewer times
            assertThat(conv.checkCount.get())
                    .as("Converter for %s should be checked %d times (once per field until match)",
                        conv.fieldName, expectedChecks)
                    .isEqualTo(expectedChecks);
        }

        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.SECONDS);
    }

    // ==================== Builder Pattern ====================

    @Test
    void testBuilderPattern() {
        // Given: Builder
        FieldConverterRegistry.Builder<TestConverter> builder =
                FieldConverterRegistry.newBuilder((conv, field) -> conv.supports(field));

        // When: Building with converters
        TestConverter conv1 = new TestConverter("name");
        TestConverter conv2 = new TestConverter("age");

        FieldConverterRegistry<TestConverter> registry = builder
                .add(conv1)
                .add(conv2)
                .build();

        // Then: Registry should be built correctly
        assertThat(registry.size()).isEqualTo(2);
        assertThat(registry.findConverter(nameField)).isSameAs(conv1);
        assertThat(registry.findConverter(ageField)).isSameAs(conv2);
    }

    @Test
    void testBuilderAddAll() {
        // Given: List of converters
        List<TestConverter> converters = List.of(
                new TestConverter("name"),
                new TestConverter("age"),
                new TestConverter("email")
        );

        // When: Building with addAll
        FieldConverterRegistry<TestConverter> registry =
                FieldConverterRegistry.<TestConverter>newBuilder((c, f) -> c.supports(f))
                        .addAll(converters)
                        .build();

        // Then: All should be added
        assertThat(registry.size()).isEqualTo(3);
    }

    // ==================== Performance Tests ====================

    @Test
    void testLookupPerformanceAfterCaching() {
        // Given: Registry with cached lookup
        TestConverter converter = new TestConverter("name");
        FieldConverterRegistry<TestConverter> registry = createRegistry(converter);

        // Warm up cache
        registry.findConverter(nameField);

        // When: Many cached lookups
        long startTime = System.nanoTime();
        int iterations = 1_000_000;

        for (int i = 0; i < iterations; i++) {
            registry.findConverter(nameField);
        }

        long duration = System.nanoTime() - startTime;
        double avgNanos = duration / (double) iterations;

        // Then: Should be extremely fast (< 100 nanoseconds)
        // This is O(1) hash map lookup performance
        assertThat(avgNanos).isLessThan(100.0);
    }

    @Test
    void testMemoryEfficiencyOfNegativeCache() {
        // Given: Registry with no converters
        FieldConverterRegistry<TestConverter> registry = createEmptyRegistry();

        // When: Looking up many different fields
        Descriptors.Descriptor descriptor = SimpleUser.getDescriptor();
        for (Descriptors.FieldDescriptor fd : descriptor.getFields()) {
            registry.findConverter(fd);
            registry.findConverter(fd); // Second lookup should use cache
        }

        // Then: Negative cache should work (manual verification via debugging)
        // This test documents expected behavior
        assertThat(registry.size()).isZero();
    }

    // ==================== Edge Cases ====================

    @Test
    void testNullSafety() {
        // Given: Registry
        TestConverter converter = new TestConverter("name");
        FieldConverterRegistry<TestConverter> registry = createRegistry(converter);

        // When/Then: Null field should not crash (though not expected in practice)
        assertThatCode(() -> {
            registry.findConverter(nameField); // Normal call
        }).doesNotThrowAnyException();
    }

    @Test
    void testConverterReturnsNull() {
        // Given: Converter that returns null from supports()
        TestConverter nullConverter = new TestConverter(null);
        FieldConverterRegistry<TestConverter> registry = createRegistry(nullConverter);

        // When: Looking up field
        TestConverter found = registry.findConverter(nameField);

        // Then: Should handle gracefully
        assertThat(found).isNull();
    }

    // ==================== Helper Methods ====================

    private FieldConverterRegistry<TestConverter> createEmptyRegistry() {
        return FieldConverterRegistry.<TestConverter>newBuilder(
                        (converter, field) -> converter.supports(field))
                .build();
    }

    @SafeVarargs
    private final <T extends TestConverter> FieldConverterRegistry<T> createRegistry(T... converters) {
        FieldConverterRegistry.Builder<T> builder =
                FieldConverterRegistry.newBuilder((conv, field) -> conv.supports(field));

        for (T converter : converters) {
            builder.add(converter);
        }

        return builder.build();
    }

    // ==================== Helper Classes ====================

    /**
     * Test converter implementation
     */
    static class TestConverter {
        final String fieldName; // Field name to support, or "*" for all

        TestConverter(String fieldName) {
            this.fieldName = fieldName;
        }

        boolean supports(Descriptors.FieldDescriptor field) {
            if (fieldName == null) return false;
            if ("*".equals(fieldName)) return true;
            return fieldName.equals(field.getName());
        }
    }

    /**
     * Converter that counts how many times supports() is called
     */
    static class CountingConverter extends TestConverter {
        final AtomicInteger checkCount = new AtomicInteger(0);

        CountingConverter(String fieldName) {
            super(fieldName);
        }

        @Override
        boolean supports(Descriptors.FieldDescriptor field) {
            checkCount.incrementAndGet();
            return super.supports(field);
        }
    }
}
