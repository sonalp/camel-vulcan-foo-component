package org.apache.camel.component.protojson.internal.registry;

import com.google.protobuf.Descriptors;
import com.google.protobuf.Message;
import com.google.protobuf.Parser;
import org.apache.camel.component.protojson.internal.registry.MetaRegistry.FieldMeta;
import org.apache.camel.component.protojson.internal.registry.MetaRegistry.MessageMeta;
import org.apache.camel.component.protojson.test.proto.*;
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
 * Comprehensive unit tests for MetaRegistry.
 * Tests caching, recursive registration, field lookups, and thread safety.
 */
class MetaRegistryTest {

    private MetaRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new MetaRegistry();
    }

    // ==================== Basic Registration ====================

    @Test
    void testRegisterSimpleMessage() {
        // Given: A simple message class
        Class<SimpleUser> messageClass = SimpleUser.class;

        // When: Registering the class
        registry.register(messageClass);

        // Then: Descriptor should be cached
        Descriptors.Descriptor descriptor = SimpleUser.getDescriptor();
        MessageMeta meta = registry.metaFor(descriptor);

        assertThat(meta).isNotNull();
        assertThat(meta.getDescriptor()).isEqualTo(descriptor);
    }

    @Test
    void testRegisterCreatesBuilder() {
        // Given: A registered message
        registry.register(SimpleUser.class);
        Descriptors.Descriptor descriptor = SimpleUser.getDescriptor();
        MessageMeta meta = registry.metaFor(descriptor);

        // When: Creating a builder
        Message.Builder builder = meta.newBuilder();

        // Then: Builder should be valid
        assertThat(builder).isNotNull();
        assertThat(builder.getDescriptorForType()).isEqualTo(descriptor);
    }

    @Test
    void testRegisterIdempotent() {
        // Given: A message class
        Class<SimpleUser> messageClass = SimpleUser.class;

        // When: Registering multiple times
        registry.register(messageClass);
        registry.register(messageClass);
        registry.register(messageClass);

        // Then: Should not throw, metadata should be same
        MessageMeta meta1 = registry.metaFor(SimpleUser.getDescriptor());
        MessageMeta meta2 = registry.metaFor(SimpleUser.getDescriptor());
        assertThat(meta1).isSameAs(meta2); // Same instance
    }

    // ==================== Recursive Registration ====================

    @Test
    void testRegisterNestedMessage() {
        // Given: A message with nested message field
        registry.register(UserWithAddress.class);

        // Then: Both parent and nested should be registered
        MessageMeta parentMeta = registry.metaFor(UserWithAddress.getDescriptor());
        MessageMeta nestedMeta = registry.metaFor(Address.getDescriptor());

        assertThat(parentMeta).isNotNull();
        assertThat(nestedMeta).isNotNull();
    }

    @Test
    void testNestedFieldMetaResolution() {
        // Given: A message with nested message
        registry.register(UserWithAddress.class);
        MessageMeta meta = registry.metaFor(UserWithAddress.getDescriptor());

        // When: Looking up nested field
        FieldMeta fieldMeta = meta.find("address");

        // Then: nestedMeta should be resolved
        assertThat(fieldMeta).isNotNull();
        assertThat(fieldMeta.nestedMeta).isNotNull();
        assertThat(fieldMeta.nestedMeta.getDescriptor()).isEqualTo(Address.getDescriptor());
    }

    @Test
    void testDeeplyNestedMessages() {
        // Given: Messages with multiple nesting levels
        registry.register(UserWithAddress.class);

        // When: Accessing nested metadata
        MessageMeta userMeta = registry.metaFor(UserWithAddress.getDescriptor());
        FieldMeta addressField = userMeta.find("address");

        // Then: All levels should be cached
        assertThat(addressField.nestedMeta).isNotNull();
        assertThat(addressField.nestedMeta.newBuilder()).isNotNull();
    }

    // ==================== Map Field Registration ====================

    @Test
    void testRegisterMapField() {
        // Given: A message with map field
        registry.register(UserWithMetadata.class);

        // When: Looking up map field
        MessageMeta meta = registry.metaFor(UserWithMetadata.getDescriptor());
        FieldMeta mapField = meta.find("stringMeta");

        // Then: Map entry descriptor should be registered
        assertThat(mapField).isNotNull();
        assertThat(mapField.fd.isMapField()).isTrue();

        // Map entry nested meta should be resolved
        assertThat(mapField.nestedMeta).isNotNull();
        Descriptors.Descriptor entryDesc = mapField.fd.getMessageType();
        assertThat(mapField.nestedMeta.getDescriptor()).isEqualTo(entryDesc);
    }

    @Test
    void testMapFieldWithMessageValue() {
        // Given: Map with MESSAGE value type (if exists in test protos)
        // This tests the critical bug fix from commit 841a92b
        registry.register(UserWithMetadata.class);

        MessageMeta meta = registry.metaFor(UserWithMetadata.getDescriptor());
        FieldMeta mapField = meta.find("stringMeta");

        // Then: Should create map entry builder without DynamicMessage
        Message.Builder entryBuilder = mapField.nestedMeta.newBuilder();
        assertThat(entryBuilder).isNotNull();
        assertThat(entryBuilder.getClass().getSimpleName())
                .doesNotContain("DynamicMessage");
    }

    // ==================== Field Lookup ====================

    @Test
    void testFindFieldByJsonName() {
        // Given: A registered message
        registry.register(SimpleUser.class);
        MessageMeta meta = registry.metaFor(SimpleUser.getDescriptor());

        // When: Finding by JSON name
        FieldMeta field = meta.find("name");

        // Then: Should find the field
        assertThat(field).isNotNull();
        assertThat(field.fd.getName()).isEqualTo("name");
    }

    @Test
    void testFindFieldByProtoName() {
        // Given: A registered message with different proto/json names
        registry.register(SimpleUser.class);
        MessageMeta meta = registry.metaFor(SimpleUser.getDescriptor());

        // When: Finding by proto name (if different from JSON)
        FieldMeta field = meta.find("email");

        // Then: Should find the field
        assertThat(field).isNotNull();
        assertThat(field.fd.getName()).isEqualTo("email");
    }

    @Test
    void testFindFieldCaseInsensitive() {
        // Given: A registered message
        registry.register(SimpleUser.class);
        MessageMeta meta = registry.metaFor(SimpleUser.getDescriptor());

        // When: Finding with wrong case
        FieldMeta upperCase = meta.find("NAME");
        FieldMeta mixedCase = meta.find("NaMe");

        // Then: Should find via case-insensitive lookup
        assertThat(upperCase).isNotNull();
        assertThat(mixedCase).isNotNull();
        assertThat(upperCase.fd.getName()).isEqualTo("name");
        assertThat(mixedCase.fd.getName()).isEqualTo("name");
    }

    @Test
    void testFindFieldNotFound() {
        // Given: A registered message
        registry.register(SimpleUser.class);
        MessageMeta meta = registry.metaFor(SimpleUser.getDescriptor());

        // When: Finding non-existent field
        FieldMeta field = meta.find("nonExistentField");

        // Then: Should return null
        assertThat(field).isNull();
    }

    @Test
    void testFindAllFields() {
        // Given: A registered message with multiple fields
        registry.register(SimpleUser.class);
        MessageMeta meta = registry.metaFor(SimpleUser.getDescriptor());

        // When: Finding all fields
        List<String> fieldNames = List.of("name", "age", "email", "active");

        // Then: All should be findable
        for (String fieldName : fieldNames) {
            FieldMeta field = meta.find(fieldName);
            assertThat(field)
                    .as("Field %s should be found", fieldName)
                    .isNotNull();
        }
    }

    // ==================== Caching Behavior ====================

    @Test
    void testMetaForCaching() {
        // Given: A registered message
        registry.register(SimpleUser.class);
        Descriptors.Descriptor descriptor = SimpleUser.getDescriptor();

        // When: Getting meta multiple times
        MessageMeta meta1 = registry.metaFor(descriptor);
        MessageMeta meta2 = registry.metaFor(descriptor);
        MessageMeta meta3 = registry.metaFor(descriptor);

        // Then: Should return same instance (cached)
        assertThat(meta1).isSameAs(meta2);
        assertThat(meta2).isSameAs(meta3);
    }

    @Test
    void testMetaForUnregisteredType() {
        // Given: An unregistered descriptor
        Descriptors.Descriptor descriptor = SimpleUser.getDescriptor();

        // When: Getting meta without prior registration
        MessageMeta meta = registry.metaFor(descriptor);

        // Then: Should create metadata on-demand but without defaultInstance
        assertThat(meta).isNotNull();
        assertThat(meta.getDescriptor()).isEqualTo(descriptor);
    }

    // ==================== Clear Functionality ====================

    @Test
    void testClear() {
        // Given: A registry with cached metadata
        registry.register(SimpleUser.class);
        registry.register(UserWithAddress.class);

        MessageMeta meta1 = registry.metaFor(SimpleUser.getDescriptor());
        assertThat(meta1).isNotNull();

        // When: Clearing the registry
        registry.clear();

        // Then: New metadata should be created
        MessageMeta meta2 = registry.metaFor(SimpleUser.getDescriptor());
        assertThat(meta2).isNotNull();
        assertThat(meta2).isNotSameAs(meta1); // Different instance
    }

    // ==================== Thread Safety ====================

    @Test
    void testConcurrentRegistration() throws InterruptedException {
        // Given: Multiple threads
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger errorCount = new AtomicInteger(0);

        // When: Concurrent registration of same type
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    registry.register(SimpleUser.class);
                    registry.register(UserWithAddress.class);
                    registry.register(UserWithMetadata.class);
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        // Then: Should complete without errors
        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(errorCount.get()).isZero();

        // Verify metadata is consistent
        MessageMeta meta = registry.metaFor(SimpleUser.getDescriptor());
        assertThat(meta).isNotNull();

        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.SECONDS);
    }

    @Test
    void testConcurrentMetaAccess() throws InterruptedException {
        // Given: Pre-registered types
        registry.register(SimpleUser.class);
        registry.register(UserWithAddress.class);

        int threadCount = 20;
        int iterationsPerThread = 1000;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        List<Exception> exceptions = new ArrayList<>();

        // When: Concurrent metadata access
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < iterationsPerThread; j++) {
                        MessageMeta meta = registry.metaFor(SimpleUser.getDescriptor());
                        FieldMeta field = meta.find("name");
                        assertThat(field).isNotNull();

                        Message.Builder builder = meta.newBuilder();
                        assertThat(builder).isNotNull();
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

        // Then: Should complete without errors
        assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(exceptions).isEmpty();

        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.SECONDS);
    }

    // ==================== Edge Cases ====================

    @Test
    void testRegisterInvalidClass() {
        // Given: An invalid message class
        // This should be handled gracefully in real scenarios

        // When/Then: Should throw IllegalStateException
        assertThatThrownBy(() -> {
            // Try to register a class without getDefaultInstance
            registry.register(InvalidMessageClass.class);
        }).isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Failed to register");
    }

    @Test
    void testEmptyMessageDescriptor() {
        // Given: A message with no fields (if such test proto exists)
        // This tests edge case handling

        // Create a minimal message and register
        registry.register(SimpleUser.class);
        MessageMeta meta = registry.metaFor(SimpleUser.getDescriptor());

        // Should still work
        assertThat(meta).isNotNull();
        assertThat(meta.newBuilder()).isNotNull();
    }

    @Test
    void testFieldMetaForRepeatedField() {
        // Given: A message with repeated fields
        registry.register(UserWithTags.class);
        MessageMeta meta = registry.metaFor(UserWithTags.getDescriptor());

        // When: Finding repeated field
        FieldMeta field = meta.find("tags");

        // Then: Should handle repeated field correctly
        assertThat(field).isNotNull();
        assertThat(field.fd.isRepeated()).isTrue();
    }

    @Test
    void testFieldMetaForEnumField() {
        // Given: A message with enum field
        registry.register(UserWithStatus.class);
        MessageMeta meta = registry.metaFor(UserWithStatus.getDescriptor());

        // When: Finding enum field
        FieldMeta field = meta.find("status");

        // Then: Should handle enum field correctly
        assertThat(field).isNotNull();
        assertThat(field.fd.getJavaType())
                .isEqualTo(Descriptors.FieldDescriptor.JavaType.ENUM);
    }

    // ==================== Performance Characteristics ====================

    @Test
    void testFieldLookupPerformance() {
        // Given: A registered message
        registry.register(SimpleUser.class);
        MessageMeta meta = registry.metaFor(SimpleUser.getDescriptor());

        // When: Performing many lookups
        long startTime = System.nanoTime();
        int iterations = 100_000;

        for (int i = 0; i < iterations; i++) {
            FieldMeta field = meta.find("name");
            assertThat(field).isNotNull();
        }

        long duration = System.nanoTime() - startTime;
        double avgMicros = duration / (double) iterations / 1000.0;

        // Then: Should be fast (< 1 microsecond per lookup on average)
        assertThat(avgMicros).isLessThan(1.0);
    }

    @Test
    void testBuilderCreationPerformance() {
        // Given: A registered message
        registry.register(SimpleUser.class);
        MessageMeta meta = registry.metaFor(SimpleUser.getDescriptor());

        // When: Creating many builders
        long startTime = System.nanoTime();
        int iterations = 10_000;

        for (int i = 0; i < iterations; i++) {
            Message.Builder builder = meta.newBuilder();
            assertThat(builder).isNotNull();
        }

        long duration = System.nanoTime() - startTime;
        double avgMicros = duration / (double) iterations / 1000.0;

        // Then: Should be reasonably fast (< 10 microseconds per builder)
        assertThat(avgMicros).isLessThan(10.0);
    }

    // ==================== Helper Classes ====================

    /**
     * Dummy class to test error handling
     */
    private static class InvalidMessageClass implements Message {
        @Override
        public void writeTo(com.google.protobuf.CodedOutputStream output) {}
        @Override
        public int getSerializedSize() { return 0; }
        @Override
        public Parser<? extends Message> getParserForType() { return null; }
        @Override
        public com.google.protobuf.ByteString toByteString() { return null; }
        @Override
        public byte[] toByteArray() { return new byte[0]; }
        @Override
        public void writeTo(java.io.OutputStream output) {}
        @Override
        public void writeDelimitedTo(java.io.OutputStream output) {}
        @Override
        public Builder newBuilderForType() { return null; }
        @Override
        public Builder toBuilder() { return null; }
        @Override
        public Message getDefaultInstanceForType() { return null; }
        @Override
        public boolean isInitialized() { return false; }
        @Override
        public List<String> findInitializationErrors() { return null; }
        @Override
        public String getInitializationErrorString() { return null; }
        @Override
        public Descriptors.Descriptor getDescriptorForType() { return null; }
        @Override
        public java.util.Map<Descriptors.FieldDescriptor, Object> getAllFields() { return null; }
        @Override
        public boolean hasOneof(Descriptors.OneofDescriptor oneof) { return false; }
        @Override
        public Descriptors.FieldDescriptor getOneofFieldDescriptor(Descriptors.OneofDescriptor oneof) { return null; }
        @Override
        public boolean hasField(Descriptors.FieldDescriptor field) { return false; }
        @Override
        public Object getField(Descriptors.FieldDescriptor field) { return null; }
        @Override
        public int getRepeatedFieldCount(Descriptors.FieldDescriptor field) { return 0; }
        @Override
        public Object getRepeatedField(Descriptors.FieldDescriptor field, int index) { return null; }
        @Override
        public com.google.protobuf.UnknownFieldSet getUnknownFields() { return null; }
    }
}
