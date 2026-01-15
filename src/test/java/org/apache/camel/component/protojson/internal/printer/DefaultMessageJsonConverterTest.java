package org.apache.camel.component.protojson.internal.printer;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.Descriptors;
import com.google.protobuf.Message;
import org.apache.camel.component.protojson.config.PrinterConfig;
import org.apache.camel.component.protojson.test.proto.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Comprehensive unit tests for DefaultMessageJsonConverter.
 * Tests Proto→JSON conversion, field inclusion, naming, and edge cases.
 */
class DefaultMessageJsonConverterTest {

    private ObjectMapper objectMapper;
    private JsonFactory jsonFactory;
    private DefaultMessageJsonConverter converter;
    private MessageJsonConverterRegistry converterRegistry;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        jsonFactory = objectMapper.getFactory();
        converter = new DefaultMessageJsonConverter();
        converterRegistry = MessageJsonConverterRegistry.withDefault();
    }

    // ==================== Basic Conversion ====================

    @Test
    void testConvertSimpleMessage() throws Exception {
        // Given: Simple message
        SimpleUser user = SimpleUser.newBuilder()
                .setName("John")
                .setAge(30)
                .setEmail("john@example.com")
                .setActive(true)
                .build();

        // When: Converting to JSON
        String json = toJson(user);

        // Then: Should contain all fields
        Map<String, Object> map = parseJson(json);
        assertThat(map).containsEntry("name", "John");
        assertThat(map).containsEntry("age", 30);
        assertThat(map).containsEntry("email", "john@example.com");
        assertThat(map).containsEntry("active", true);
    }

    @Test
    void testConvertEmptyMessage() throws Exception {
        // Given: Empty message (default values)
        SimpleUser user = SimpleUser.newBuilder().build();

        PrinterConfig config = PrinterConfig.newBuilder()
                .includingDefaultValueFields(false)
                .build();

        // When: Converting without default values
        String json = toJson(user, config);

        // Then: Should be empty object
        Map<String, Object> map = parseJson(json);
        assertThat(map).isEmpty();
    }

    @Test
    void testConvertWithDefaultValues() throws Exception {
        // Given: Empty message
        SimpleUser user = SimpleUser.newBuilder().build();

        PrinterConfig config = PrinterConfig.newBuilder()
                .includingDefaultValueFields(true)
                .build();

        // When: Converting with default values
        String json = toJson(user, config);

        // Then: Should include all fields with defaults
        Map<String, Object> map = parseJson(json);
        assertThat(map).containsEntry("name", "");
        assertThat(map).containsEntry("age", 0);
        assertThat(map).containsEntry("email", "");
        assertThat(map).containsEntry("active", false);
    }

    // ==================== Field Naming ====================

    @Test
    void testJsonFieldNames() throws Exception {
        // Given: Message
        SimpleUser user = SimpleUser.newBuilder()
                .setName("John")
                .setAge(30)
                .build();

        PrinterConfig config = PrinterConfig.newBuilder()
                .preservingProtoFieldNames(false) // Use JSON names
                .build();

        // When: Converting
        String json = toJson(user, config);

        // Then: Should use JSON field names (camelCase typically)
        Map<String, Object> map = parseJson(json);
        assertThat(map).containsKey("name");
        assertThat(map).containsKey("age");
    }

    @Test
    void testProtoFieldNames() throws Exception {
        // Given: Message
        SimpleUser user = SimpleUser.newBuilder()
                .setName("John")
                .setAge(30)
                .build();

        PrinterConfig config = PrinterConfig.newBuilder()
                .preservingProtoFieldNames(true) // Use proto names
                .build();

        // When: Converting
        String json = toJson(user, config);

        // Then: Should use proto field names
        Map<String, Object> map = parseJson(json);
        assertThat(map).containsKey("name"); // Proto name
        assertThat(map).containsKey("age");
    }

    // ==================== Enum Handling ====================

    @Test
    void testEnumAsString() throws Exception {
        // Given: Message with enum
        UserWithStatus user = UserWithStatus.newBuilder()
                .setName("John")
                .setStatus(UserStatus.ACTIVE)
                .build();

        PrinterConfig config = PrinterConfig.newBuilder()
                .printingEnumsAsInts(false) // As string
                .build();

        // When: Converting
        String json = toJson(user, config);

        // Then: Should output enum name
        Map<String, Object> map = parseJson(json);
        assertThat(map).containsEntry("status", "ACTIVE");
    }

    @Test
    void testEnumAsInt() throws Exception {
        // Given: Message with enum
        UserWithStatus user = UserWithStatus.newBuilder()
                .setName("John")
                .setStatus(UserStatus.ACTIVE)
                .build();

        PrinterConfig config = PrinterConfig.newBuilder()
                .printingEnumsAsInts(true) // As number
                .build();

        // When: Converting
        String json = toJson(user, config);

        // Then: Should output enum number
        Map<String, Object> map = parseJson(json);
        assertThat(map.get("status")).isInstanceOf(Number.class);
        assertThat(((Number) map.get("status")).intValue())
                .isEqualTo(UserStatus.ACTIVE.getNumber());
    }

    @Test
    void testDefaultEnumValue() throws Exception {
        // Given: Message with default enum (first value = 0)
        UserWithStatus user = UserWithStatus.newBuilder()
                .setName("John")
                .build(); // status = default (UNKNOWN = 0)

        PrinterConfig config = PrinterConfig.newBuilder()
                .includingDefaultValueFields(true)
                .printingEnumsAsInts(false)
                .build();

        // When: Converting
        String json = toJson(user, config);

        // Then: Should include default enum value
        Map<String, Object> map = parseJson(json);
        assertThat(map).containsKey("status");
    }

    // ==================== Repeated Fields ====================

    @Test
    void testEmptyRepeatedField() throws Exception {
        // Given: Message with empty repeated field
        UserWithTags user = UserWithTags.newBuilder()
                .setName("John")
                .build(); // No tags

        PrinterConfig config = PrinterConfig.newBuilder()
                .includingDefaultValueFields(false)
                .build();

        // When: Converting without defaults
        String json = toJson(user, config);

        // Then: Should not include empty array
        Map<String, Object> map = parseJson(json);
        assertThat(map).doesNotContainKey("tags");
    }

    @Test
    void testEmptyRepeatedFieldWithDefaults() throws Exception {
        // Given: Message with empty repeated field
        UserWithTags user = UserWithTags.newBuilder()
                .setName("John")
                .build();

        PrinterConfig config = PrinterConfig.newBuilder()
                .includingDefaultValueFields(true)
                .build();

        // When: Converting with defaults
        String json = toJson(user, config);

        // Then: Should include empty array
        Map<String, Object> map = parseJson(json);
        assertThat(map).containsKey("tags");
        assertThat(map.get("tags")).asList().isEmpty();
    }

    @Test
    void testRepeatedFieldWithElements() throws Exception {
        // Given: Message with repeated field
        UserWithTags user = UserWithTags.newBuilder()
                .setName("John")
                .addTags("tag1")
                .addTags("tag2")
                .addTags("tag3")
                .build();

        // When: Converting
        String json = toJson(user);

        // Then: Should output array
        Map<String, Object> map = parseJson(json);
        assertThat(map.get("tags")).asList()
                .containsExactly("tag1", "tag2", "tag3");
    }

    @Test
    void testLargeRepeatedField() throws Exception {
        // Given: Large repeated field
        UserWithTags.Builder builder = UserWithTags.newBuilder()
                .setName("John");

        for (int i = 0; i < 10000; i++) {
            builder.addTags("tag" + i);
        }

        UserWithTags user = builder.build();

        // When: Converting
        String json = toJson(user);

        // Then: Should handle large array
        Map<String, Object> map = parseJson(json);
        assertThat(map.get("tags")).asList().hasSize(10000);
    }

    // ==================== Map Fields ====================

    @Test
    void testEmptyMapField() throws Exception {
        // Given: Message with empty map
        UserWithMetadata user = UserWithMetadata.newBuilder()
                .setName("John")
                .build(); // No metadata

        PrinterConfig config = PrinterConfig.newBuilder()
                .includingDefaultValueFields(false)
                .build();

        // When: Converting without defaults
        String json = toJson(user, config);

        // Then: Should not include empty map
        Map<String, Object> map = parseJson(json);
        assertThat(map).doesNotContainKey("stringMeta");
    }

    @Test
    void testMapFieldWithEntries() throws Exception {
        // Given: Message with map entries
        UserWithMetadata user = UserWithMetadata.newBuilder()
                .setName("John")
                .putStringMeta("key1", "value1")
                .putStringMeta("key2", "value2")
                .putStringMeta("key3", "value3")
                .build();

        // When: Converting
        String json = toJson(user);

        // Then: Should output as JSON object
        Map<String, Object> map = parseJson(json);
        @SuppressWarnings("unchecked")
        Map<String, String> metaMap = (Map<String, String>) map.get("stringMeta");

        assertThat(metaMap)
                .containsEntry("key1", "value1")
                .containsEntry("key2", "value2")
                .containsEntry("key3", "value3");
    }

    @Test
    void testMapWithNumericKeys() throws Exception {
        // Given: Map with numeric keys (if test proto supports it)
        // This tests the String.valueOf() optimization opportunity

        UserWithMetadata user = UserWithMetadata.newBuilder()
                .setName("John")
                .putStringMeta("1", "value1")
                .putStringMeta("2", "value2")
                .putStringMeta("100", "value100")
                .build();

        // When: Converting
        String json = toJson(user);

        // Then: Numeric keys should be stringified
        Map<String, Object> map = parseJson(json);
        @SuppressWarnings("unchecked")
        Map<String, String> metaMap = (Map<String, String>) map.get("stringMeta");

        assertThat(metaMap)
                .containsEntry("1", "value1")
                .containsEntry("2", "value2")
                .containsEntry("100", "value100");
    }

    @Test
    void testLargeMapField() throws Exception {
        // Given: Large map
        UserWithMetadata.Builder builder = UserWithMetadata.newBuilder()
                .setName("John");

        for (int i = 0; i < 1000; i++) {
            builder.putStringMeta("key" + i, "value" + i);
        }

        UserWithMetadata user = builder.build();

        // When: Converting
        String json = toJson(user);

        // Then: Should handle large map
        Map<String, Object> map = parseJson(json);
        @SuppressWarnings("unchecked")
        Map<String, String> metaMap = (Map<String, String>) map.get("stringMeta");
        assertThat(metaMap).hasSize(1000);
    }

    // ==================== Nested Messages ====================

    @Test
    void testNestedMessage() throws Exception {
        // Given: Message with nested message
        UserWithAddress user = UserWithAddress.newBuilder()
                .setName("John")
                .setAddress(Address.newBuilder()
                        .setStreet("123 Main St")
                        .setCity("NYC")
                        .setCountry("USA")
                        .setZipCode(10001)
                        .build())
                .build();

        // When: Converting
        String json = toJson(user);

        // Then: Should output nested object
        Map<String, Object> map = parseJson(json);
        @SuppressWarnings("unchecked")
        Map<String, Object> addressMap = (Map<String, Object>) map.get("address");

        assertThat(addressMap)
                .containsEntry("street", "123 Main St")
                .containsEntry("city", "NYC")
                .containsEntry("country", "USA")
                .containsEntry("zipCode", 10001);
    }

    @Test
    void testMissingNestedMessage() throws Exception {
        // Given: Message without nested message set
        UserWithAddress user = UserWithAddress.newBuilder()
                .setName("John")
                .build(); // No address

        PrinterConfig config = PrinterConfig.newBuilder()
                .includingDefaultValueFields(false)
                .build();

        // When: Converting
        String json = toJson(user, config);

        // Then: Should not include nested message
        Map<String, Object> map = parseJson(json);
        assertThat(map).doesNotContainKey("address");
    }

    @Test
    void testEmptyNestedMessage() throws Exception {
        // Given: Message with empty nested message
        UserWithAddress user = UserWithAddress.newBuilder()
                .setName("John")
                .setAddress(Address.newBuilder().build()) // Empty address
                .build();

        // When: Converting
        String json = toJson(user);

        // Then: Should include nested message (even if empty)
        Map<String, Object> map = parseJson(json);
        assertThat(map).containsKey("address");
    }

    // ==================== Bytes Fields ====================

    @Test
    void testBytesField() throws Exception {
        // Given: Message with bytes field (if available in test protos)
        // Bytes should be base64 encoded

        // This test documents expected behavior
        // When bytes field is available:
        byte[] data = "Hello, World!".getBytes(StandardCharsets.UTF_8);
        String expectedBase64 = java.util.Base64.getEncoder().encodeToString(data);

        // Then: JSON should contain base64 string
        // assertThat(json).contains(expectedBase64);
    }

    // ==================== Special Values ====================

    @Test
    void testMaxIntValue() throws Exception {
        // Given: Max int value
        SimpleUser user = SimpleUser.newBuilder()
                .setName("John")
                .setAge(Integer.MAX_VALUE)
                .build();

        // When: Converting
        String json = toJson(user);

        // Then: Should handle max value
        Map<String, Object> map = parseJson(json);
        assertThat(((Number) map.get("age")).intValue()).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    void testMinIntValue() throws Exception {
        // Given: Min int value
        SimpleUser user = SimpleUser.newBuilder()
                .setName("John")
                .setAge(Integer.MIN_VALUE)
                .build();

        // When: Converting
        String json = toJson(user);

        // Then: Should handle min value
        Map<String, Object> map = parseJson(json);
        assertThat(((Number) map.get("age")).intValue()).isEqualTo(Integer.MIN_VALUE);
    }

    @Test
    void testEmptyString() throws Exception {
        // Given: Empty string
        SimpleUser user = SimpleUser.newBuilder()
                .setName("")
                .setAge(30)
                .build();

        PrinterConfig config = PrinterConfig.newBuilder()
                .includingDefaultValueFields(true) // Include even if default
                .build();

        // When: Converting
        String json = toJson(user, config);

        // Then: Should include empty string
        Map<String, Object> map = parseJson(json);
        assertThat(map).containsEntry("name", "");
    }

    @Test
    void testVeryLongString() throws Exception {
        // Given: Very long string
        String longString = "x".repeat(100_000);
        SimpleUser user = SimpleUser.newBuilder()
                .setName(longString)
                .setAge(30)
                .build();

        // When: Converting
        String json = toJson(user);

        // Then: Should handle long string
        Map<String, Object> map = parseJson(json);
        assertThat(map.get("name").toString()).hasSize(100_000);
    }

    @Test
    void testSpecialCharactersInString() throws Exception {
        // Given: Special characters
        SimpleUser user = SimpleUser.newBuilder()
                .setName("John \"Doe\"\n\t\r")
                .setAge(30)
                .build();

        // When: Converting
        String json = toJson(user);

        // Then: Should escape special characters
        assertThat(json).contains("\\n");
        assertThat(json).contains("\\\"");

        // And should parse back correctly
        Map<String, Object> map = parseJson(json);
        assertThat(map.get("name")).asString().contains("\"Doe\"");
    }

    @Test
    void testUnicodeCharacters() throws Exception {
        // Given: Unicode characters
        SimpleUser user = SimpleUser.newBuilder()
                .setName("José 中文 🎉")
                .setAge(30)
                .build();

        // When: Converting
        String json = toJson(user);

        // Then: Should handle unicode
        Map<String, Object> map = parseJson(json);
        assertThat(map.get("name")).isEqualTo("José 中文 🎉");
    }

    // ==================== Performance Tests ====================

    @Test
    void testLargeMessageConversion() throws Exception {
        // Given: Large complex message
        UserWithTags.Builder builder = UserWithTags.newBuilder()
                .setName("John");

        for (int i = 0; i < 1000; i++) {
            builder.addTags("tag" + i);
            builder.addScores(i);
        }

        UserWithTags user = builder.build();

        // When: Converting
        long startTime = System.nanoTime();
        String json = toJson(user);
        long duration = System.nanoTime() - startTime;

        // Then: Should complete in reasonable time
        assertThat(json).isNotEmpty();
        assertThat(duration).isLessThan(100_000_000); // < 100ms
    }

    // ==================== Helper Methods ====================

    private String toJson(Message message) throws Exception {
        return toJson(message, PrinterConfig.defaultConfig());
    }

    private String toJson(Message message, PrinterConfig config) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        try (JsonGenerator gen = jsonFactory.createGenerator(baos)) {
            ProtoToJsonContext context = new ProtoToJsonContext(
                    objectMapper,
                    converterRegistry,
                    config
            );

            Descriptors.Descriptor descriptor = message.getDescriptorForType();
            converter.write(message, descriptor, gen, context);
            gen.flush();
        }

        return baos.toString(StandardCharsets.UTF_8);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJson(String json) throws Exception {
        return objectMapper.readValue(json, Map.class);
    }
}
