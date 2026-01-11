package org.apache.camel.component.protojson.internal;

import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.Message;
import org.apache.camel.component.protojson.internal.parser.GeneratedMessageHelper;
import org.apache.camel.component.protojson.test.proto.Address;
import org.apache.camel.component.protojson.test.proto.UserWithMetadata;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for GeneratedMessageHelper - verifies MethodHandle optimization works correctly.
 */
@DisplayName("GeneratedMessageHelper Tests")
class GeneratedMessageHelperTest {

    @Test
    @DisplayName("Should set single field using MethodHandle")
    void shouldSetSingleFieldUsingMethodHandle() throws Throwable {
        // Given
        UserWithMetadata.Builder builder = UserWithMetadata.newBuilder();
        Descriptors.Descriptor desc = UserWithMetadata.getDescriptor();
        Descriptors.FieldDescriptor nameField = desc.findFieldByName("name");

        // When - use MethodHandle optimization
        boolean success = GeneratedMessageHelper.setField(builder, nameField, "John Doe");

        // Then
        assertThat(success)
                .as("MethodHandle should successfully set field")
                .isTrue();

        UserWithMetadata user = builder.build();
        assertThat(user.getName()).isEqualTo("John Doe");
    }

    @Test
    @DisplayName("Should add repeated field using MethodHandle")
    void shouldAddRepeatedFieldUsingMethodHandle() {
        // Given - note: UserWithMetadata might not have repeated fields
        // This test demonstrates the API, even if it returns false
        UserWithMetadata.Builder builder = UserWithMetadata.newBuilder();
        Descriptors.Descriptor desc = UserWithMetadata.getDescriptor();
        Descriptors.FieldDescriptor nameField = desc.findFieldByName("name");

        // When - try to add (will fail for non-repeated field)
        boolean success = GeneratedMessageHelper.addRepeated(builder, nameField, "value");

        // Then - should return false for single field (no addName method)
        assertThat(success)
                .as("Should return false for non-repeated field")
                .isFalse();
    }

    @Test
    @DisplayName("Should put map entry using MethodHandle")
    void shouldPutMapEntryUsingMethodHandle() {
        // Given
        UserWithMetadata.Builder builder = UserWithMetadata.newBuilder();
        Descriptors.Descriptor desc = UserWithMetadata.getDescriptor();
        Descriptors.FieldDescriptor stringMetaField = desc.findFieldByName("string_meta");

        // When - use MethodHandle optimization
        boolean success = GeneratedMessageHelper.putMap(builder, stringMetaField, "key1", "value1");

        // Then
        assertThat(success)
                .as("MethodHandle should successfully put map entry")
                .isTrue();

        UserWithMetadata user = builder.build();
        assertThat(user.getStringMetaMap())
                .containsEntry("key1", "value1");
    }

    @Test
    @DisplayName("Should put int key map using MethodHandle")
    void shouldPutIntKeyMapUsingMethodHandle() {
        // Given
        UserWithMetadata.Builder builder = UserWithMetadata.newBuilder();
        Descriptors.Descriptor desc = UserWithMetadata.getDescriptor();
        Descriptors.FieldDescriptor intKeyMetaField = desc.findFieldByName("int_key_meta");

        // When - use MethodHandle optimization
        boolean success = GeneratedMessageHelper.putMap(builder, intKeyMetaField, 42, "answer");

        // Then
        assertThat(success)
                .as("MethodHandle should successfully put int key map entry")
                .isTrue();

        UserWithMetadata user = builder.build();
        assertThat(user.getIntKeyMetaMap())
                .containsEntry(42, "answer");
    }

    @Test
    @DisplayName("Should put message value map using MethodHandle")
    void shouldPutMessageValueMapUsingMethodHandle() {
        // Given
        UserWithMetadata.Builder builder = UserWithMetadata.newBuilder();
        Descriptors.Descriptor desc = UserWithMetadata.getDescriptor();
        Descriptors.FieldDescriptor addressMapField = desc.findFieldByName("address_map");

        Address address = Address.newBuilder()
                .setCity("Boston")
                .setStreet("Main St")
                .build();

        // When - use MethodHandle optimization
        boolean success = GeneratedMessageHelper.putMap(builder, addressMapField, "home", address);

        // Then
        assertThat(success)
                .as("MethodHandle should successfully put message value map entry")
                .isTrue();

        UserWithMetadata user = builder.build();
        assertThat(user.getAddressMapMap())
                .containsKey("home");
        assertThat(user.getAddressMapMap().get("home").getCity())
                .isEqualTo("Boston");
    }

    @Test
    @DisplayName("Should return false for DynamicMessage builder")
    void shouldReturnFalseForDynamicMessageBuilder() {
        // Given
        Descriptors.Descriptor desc = UserWithMetadata.getDescriptor();
        Message.Builder dynamicBuilder = DynamicMessage.newBuilder(desc);
        Descriptors.FieldDescriptor nameField = desc.findFieldByName("name");

        // When - DynamicMessage has no generated methods
        boolean success = GeneratedMessageHelper.setField(dynamicBuilder, nameField, "test");

        // Then
        assertThat(success)
                .as("DynamicMessage should return false (no MethodHandle)")
                .isFalse();
    }

    @Test
    @DisplayName("Should use fallback for DynamicMessage map")
    void shouldUseFallbackForDynamicMessageMap() {
        // Given
        Descriptors.Descriptor desc = UserWithMetadata.getDescriptor();
        Message.Builder dynamicBuilder = DynamicMessage.newBuilder(desc);
        Descriptors.FieldDescriptor stringMetaField = desc.findFieldByName("string_meta");

        // When - DynamicMessage has no putXxx methods
        boolean success = GeneratedMessageHelper.putMap(dynamicBuilder, stringMetaField, "key", "value");

        // Then
        assertThat(success)
                .as("DynamicMessage map should return false (no MethodHandle)")
                .isFalse();
    }

    @Test
    @DisplayName("Should clear all caches")
    void shouldClearAllCaches() {
        // Given - populate caches by using methods
        UserWithMetadata.Builder builder = UserWithMetadata.newBuilder();
        Descriptors.Descriptor desc = UserWithMetadata.getDescriptor();
        Descriptors.FieldDescriptor nameField = desc.findFieldByName("name");
        Descriptors.FieldDescriptor stringMetaField = desc.findFieldByName("string_meta");

        GeneratedMessageHelper.setField(builder, nameField, "test");
        GeneratedMessageHelper.putMap(builder, stringMetaField, "key", "value");

        // When
        GeneratedMessageHelper.clearCaches();

        // Then - should still work after clear (repopulates cache)
        boolean success = GeneratedMessageHelper.setField(builder, nameField, "test2");
        assertThat(success)
                .as("Should still work after cache clear")
                .isTrue();
    }

    @Test
    @DisplayName("Should handle multiple calls efficiently (caching)")
    void shouldHandleMultipleCallsEfficiently() {
        // Given
        UserWithMetadata.Builder builder = UserWithMetadata.newBuilder();
        Descriptors.Descriptor desc = UserWithMetadata.getDescriptor();
        Descriptors.FieldDescriptor stringMetaField = desc.findFieldByName("string_meta");

        // When - multiple calls should use cached MethodHandle
        boolean success1 = GeneratedMessageHelper.putMap(builder, stringMetaField, "key1", "value1");
        boolean success2 = GeneratedMessageHelper.putMap(builder, stringMetaField, "key2", "value2");
        boolean success3 = GeneratedMessageHelper.putMap(builder, stringMetaField, "key3", "value3");

        // Then - all should succeed using cached MethodHandle
        assertThat(success1).isTrue();
        assertThat(success2).isTrue();
        assertThat(success3).isTrue();

        UserWithMetadata user = builder.build();
        assertThat(user.getStringMetaMap())
                .hasSize(3)
                .containsEntry("key1", "value1")
                .containsEntry("key2", "value2")
                .containsEntry("key3", "value3");
    }
}
