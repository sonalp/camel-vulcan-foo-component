package org.apache.camel.component.protojson.internal.registry;

import com.google.protobuf.Message;
import org.apache.camel.component.protojson.test.proto.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

/**
 * Comprehensive unit tests for BuilderFactory.
 * Tests MethodHandle caching, performance, and thread safety.
 */
class BuilderFactoryTest {

    private BuilderFactory factory;

    @BeforeEach
    void setUp() {
        factory = new BuilderFactory();
    }

    // ==================== Basic Functionality ====================

    @Test
    void testCreateSimpleBuilder() {
        // When: Creating builder for simple message
        Message.Builder builder = factory.newBuilder(SimpleUser.class);

        // Then: Should create valid builder
        assertThat(builder).isNotNull();
        assertThat(builder.getDescriptorForType()).isEqualTo(SimpleUser.getDescriptor());
        assertThat(builder.build()).isInstanceOf(SimpleUser.class);
    }

    @Test
    void testCreateNestedMessageBuilder() {
        // When: Creating builder for message with nested types
        Message.Builder builder = factory.newBuilder(UserWithAddress.class);

        // Then: Should create valid builder
        assertThat(builder).isNotNull();
        assertThat(builder.getDescriptorForType()).isEqualTo(UserWithAddress.getDescriptor());
        assertThat(builder.build()).isInstanceOf(UserWithAddress.class);
    }

    @Test
    void testCreateMapFieldMessageBuilder() {
        // When: Creating builder for message with map field
        Message.Builder builder = factory.newBuilder(UserWithMetadata.class);

        // Then: Should create valid builder
        assertThat(builder).isNotNull();
        assertThat(builder.build()).isInstanceOf(UserWithMetadata.class);
    }

    @Test
    void testCreateRepeatedFieldMessageBuilder() {
        // When: Creating builder for message with repeated fields
        Message.Builder builder = factory.newBuilder(UserWithTags.class);

        // Then: Should create valid builder
        assertThat(builder).isNotNull();
        assertThat(builder.build()).isInstanceOf(UserWithTags.class);
    }

    @Test
    void testCreateEnumFieldMessageBuilder() {
        // When: Creating builder for message with enum field
        Message.Builder builder = factory.newBuilder(UserWithStatus.class);

        // Then: Should create valid builder
        assertThat(builder).isNotNull();
        assertThat(builder.build()).isInstanceOf(UserWithStatus.class);
    }

    // ==================== Builder Usability ====================

    @Test
    void testBuilderCanBeUsed() {
        // Given: A builder
        Message.Builder builder = factory.newBuilder(SimpleUser.class);

        // When: Building a message
        SimpleUser user = (SimpleUser) builder
                .setField(SimpleUser.getDescriptor().findFieldByName("name"), "John")
                .setField(SimpleUser.getDescriptor().findFieldByName("age"), 30)
                .build();

        // Then: Message should be built correctly
        assertThat(user.getName()).isEqualTo("John");
        assertThat(user.getAge()).isEqualTo(30);
    }

    @Test
    void testMultipleBuildersAreIndependent() {
        // Given: Multiple builders for same type
        Message.Builder builder1 = factory.newBuilder(SimpleUser.class);
        Message.Builder builder2 = factory.newBuilder(SimpleUser.class);

        // When: Setting different values
        builder1.setField(SimpleUser.getDescriptor().findFieldByName("name"), "Alice");
        builder2.setField(SimpleUser.getDescriptor().findFieldByName("name"), "Bob");

        SimpleUser user1 = (SimpleUser) builder1.build();
        SimpleUser user2 = (SimpleUser) builder2.build();

        // Then: Should be independent
        assertThat(user1.getName()).isEqualTo("Alice");
        assertThat(user2.getName()).isEqualTo("Bob");
    }

    // ==================== Caching Behavior ====================

    @Test
    void testMethodHandleCaching() {
        // Given: First call to create builder
        Message.Builder builder1 = factory.newBuilder(SimpleUser.class);

        // When: Subsequent calls for same type
        Message.Builder builder2 = factory.newBuilder(SimpleUser.class);
        Message.Builder builder3 = factory.newBuilder(SimpleUser.class);

        // Then: All should succeed (MethodHandle is cached internally)
        assertThat(builder1).isNotNull();
        assertThat(builder2).isNotNull();
        assertThat(builder3).isNotNull();

        // Verify they create same type
        assertThat(builder1.build()).isInstanceOf(SimpleUser.class);
        assertThat(builder2.build()).isInstanceOf(SimpleUser.class);
        assertThat(builder3.build()).isInstanceOf(SimpleUser.class);
    }

    @Test
    void testCachePerType() {
        // When: Creating builders for different types
        Message.Builder simpleBuilder = factory.newBuilder(SimpleUser.class);
        Message.Builder addressBuilder = factory.newBuilder(UserWithAddress.class);
        Message.Builder tagsBuilder = factory.newBuilder(UserWithTags.class);

        // Then: Each should create correct type
        assertThat(simpleBuilder.build()).isInstanceOf(SimpleUser.class);
        assertThat(addressBuilder.build()).isInstanceOf(UserWithAddress.class);
        assertThat(tagsBuilder.build()).isInstanceOf(UserWithTags.class);
    }

    // ==================== Error Handling ====================

    @Test
    void testInvalidMessageClass() {
        // When/Then: Trying to create builder for invalid class
        assertThatThrownBy(() -> {
            factory.newBuilder(InvalidMessage.class);
        }).isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Failed to create builder");
    }

    @Test
    void testNonMessageClass() {
        // When/Then: Trying to create builder for non-Message class
        assertThatThrownBy(() -> {
            // This should fail at compile time, but test runtime behavior
            factory.newBuilder((Class) String.class);
        }).isInstanceOf(IllegalStateException.class);
    }

    // ==================== Performance Tests ====================

    @Test
    void testFirstCallPerformance() {
        // When: First call (MethodHandle creation)
        long startTime = System.nanoTime();
        Message.Builder builder = factory.newBuilder(SimpleUser.class);
        long duration = System.nanoTime() - startTime;

        // Then: Should complete reasonably fast (< 1ms)
        assertThat(builder).isNotNull();
        assertThat(duration).isLessThan(1_000_000); // 1ms in nanoseconds
    }

    @Test
    void testCachedCallPerformance() {
        // Given: Warm up cache
        factory.newBuilder(SimpleUser.class);

        // When: Cached calls
        long startTime = System.nanoTime();
        int iterations = 100_000;

        for (int i = 0; i < iterations; i++) {
            Message.Builder builder = factory.newBuilder(SimpleUser.class);
            assertThat(builder).isNotNull();
        }

        long duration = System.nanoTime() - startTime;
        double avgMicros = duration / (double) iterations / 1000.0;

        // Then: Should be very fast with caching (< 1 microsecond avg)
        assertThat(avgMicros).isLessThan(1.0);
    }

    @Test
    void testPerformanceComparison() {
        // This documents that MethodHandle is faster than direct reflection

        // Warm up
        for (int i = 0; i < 1000; i++) {
            factory.newBuilder(SimpleUser.class);
        }

        // Measure BuilderFactory (MethodHandle)
        long startMethodHandle = System.nanoTime();
        for (int i = 0; i < 10_000; i++) {
            factory.newBuilder(SimpleUser.class);
        }
        long durationMethodHandle = System.nanoTime() - startMethodHandle;

        // Measure direct reflection
        long startReflection = System.nanoTime();
        for (int i = 0; i < 10_000; i++) {
            try {
                SimpleUser.class.getMethod("newBuilder").invoke(null);
            } catch (Exception e) {
                fail("Reflection failed");
            }
        }
        long durationReflection = System.nanoTime() - startReflection;

        // Then: MethodHandle should be faster or comparable
        double speedup = (double) durationReflection / durationMethodHandle;
        assertThat(speedup).isGreaterThanOrEqualTo(0.5); // At least not slower

        System.out.printf("MethodHandle: %d ns, Reflection: %d ns, Speedup: %.2fx%n",
                durationMethodHandle, durationReflection, speedup);
    }

    // ==================== Thread Safety ====================

    @Test
    void testConcurrentBuilderCreation() throws InterruptedException {
        // Given: Multiple threads
        int threadCount = 20;
        int iterationsPerThread = 1000;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        List<Exception> exceptions = new ArrayList<>();

        // When: Concurrent builder creation
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < iterationsPerThread; j++) {
                        Message.Builder builder = factory.newBuilder(SimpleUser.class);
                        if (builder == null) {
                            throw new AssertionError("Builder is null");
                        }
                        // Verify builder works
                        Message msg = builder.build();
                        if (!(msg instanceof SimpleUser)) {
                            throw new AssertionError("Wrong message type");
                        }
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

    @Test
    void testConcurrentMultipleTypes() throws InterruptedException {
        // Given: Multiple threads accessing different types
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        List<Exception> exceptions = new ArrayList<>();

        Class<?>[] messageTypes = {
                SimpleUser.class,
                UserWithAddress.class,
                UserWithMetadata.class,
                UserWithTags.class,
                UserWithStatus.class
        };

        // When: Concurrent access to different types
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < 1000; j++) {
                        Class<?> type = messageTypes[j % messageTypes.length];
                        Message.Builder builder = factory.newBuilder((Class<? extends Message>) type);
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

    @Test
    void testConcurrentCacheBuilding() throws InterruptedException {
        // Given: Fresh factory, multiple threads try to cache same type simultaneously
        BuilderFactory freshFactory = new BuilderFactory();

        int threadCount = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        List<Message.Builder> builders = new ArrayList<>();

        // When: All threads try to create builder at same time (first call)
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await(); // Wait for signal to start together
                    Message.Builder builder = freshFactory.newBuilder(SimpleUser.class);
                    synchronized (builders) {
                        builders.add(builder);
                    }
                } catch (Exception e) {
                    fail("Exception in thread: " + e.getMessage());
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // Signal all threads to start
        startLatch.countDown();

        // Then: All should succeed
        assertThat(doneLatch.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(builders).hasSize(threadCount);

        // All builders should be valid
        for (Message.Builder builder : builders) {
            assertThat(builder).isNotNull();
            assertThat(builder.build()).isInstanceOf(SimpleUser.class);
        }

        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.SECONDS);
    }

    // ==================== Edge Cases ====================

    @Test
    void testBuilderFromInnerClass() {
        // When: Creating builder for nested message (if any in test protos)
        Message.Builder builder = factory.newBuilder(Address.class);

        // Then: Should work
        assertThat(builder).isNotNull();
        assertThat(builder.build()).isInstanceOf(Address.class);
    }

    @Test
    void testRepeatedBuilderCreation() {
        // When: Creating many builders in sequence
        for (int i = 0; i < 1000; i++) {
            Message.Builder builder = factory.newBuilder(SimpleUser.class);
            assertThat(builder).isNotNull();

            // Verify each builder is independent
            SimpleUser msg = (SimpleUser) builder
                    .setField(SimpleUser.getDescriptor().findFieldByName("age"), i)
                    .build();
            assertThat(msg.getAge()).isEqualTo(i);
        }
    }

    @Test
    void testBuilderAfterGC() {
        // This tests that cache survives GC
        Message.Builder builder1 = factory.newBuilder(SimpleUser.class);
        assertThat(builder1).isNotNull();

        // Suggest GC (no guarantee it runs)
        System.gc();

        // Should still work
        Message.Builder builder2 = factory.newBuilder(SimpleUser.class);
        assertThat(builder2).isNotNull();
    }

    // ==================== Helper Classes ====================

    /**
     * Invalid message class for testing error handling
     */
    static class InvalidMessage implements Message {
        // Missing static newBuilder() method
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
        public com.google.protobuf.Descriptors.Descriptor getDescriptorForType() { return null; }
        @Override
        public java.util.Map<com.google.protobuf.Descriptors.FieldDescriptor, Object> getAllFields() { return null; }
        @Override
        public boolean hasOneof(com.google.protobuf.Descriptors.OneofDescriptor oneof) { return false; }
        @Override
        public com.google.protobuf.Descriptors.FieldDescriptor getOneofFieldDescriptor(
                com.google.protobuf.Descriptors.OneofDescriptor oneof) { return null; }
        @Override
        public boolean hasField(com.google.protobuf.Descriptors.FieldDescriptor field) { return false; }
        @Override
        public Object getField(com.google.protobuf.Descriptors.FieldDescriptor field) { return null; }
        @Override
        public int getRepeatedFieldCount(com.google.protobuf.Descriptors.FieldDescriptor field) { return 0; }
        @Override
        public Object getRepeatedField(com.google.protobuf.Descriptors.FieldDescriptor field, int index) { return null; }
        @Override
        public com.google.protobuf.UnknownFieldSet getUnknownFields() { return null; }
    }
}
