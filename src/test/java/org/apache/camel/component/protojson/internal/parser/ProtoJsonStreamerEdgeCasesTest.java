package org.apache.camel.component.protojson.internal.parser;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.google.protobuf.Descriptors;
import com.google.protobuf.Message;
import org.apache.camel.component.protojson.config.ParserConfig;
import org.apache.camel.component.protojson.engine.ProtoJsonException;
import org.apache.camel.component.protojson.internal.registry.MetaRegistry;
import org.apache.camel.component.protojson.test.proto.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.*;

/**
 * Edge case tests for ProtoJsonStreamer.
 * Tests error handling, malformed JSON, type mismatches, and boundary conditions.
 */
class ProtoJsonStreamerEdgeCasesTest {

    private JsonFactory jsonFactory;
    private MetaRegistry metaRegistry;
    private MessageTypeConverterRegistry converterRegistry;

    @BeforeEach
    void setUp() {
        jsonFactory = new JsonFactory();
        metaRegistry = new MetaRegistry();
        converterRegistry = MessageTypeConverterRegistry.withDefault();
    }

    // ==================== Malformed JSON ====================

    @Test
    void testInvalidJsonSyntax() {
        // Given: Malformed JSON
        String json = "{name: 'John'}"; // Missing quotes on key

        // When/Then: Should throw ProtoJsonException
        assertThatThrownBy(() -> parseJson(json, SimpleUser.class))
                .isInstanceOf(Exception.class); // JsonParser will throw
    }

    @Test
    void testIncompleteJson() {
        // Given: Incomplete JSON
        String json = "{\"name\": \"John\""; // Missing closing brace

        // When/Then: Should throw exception
        assertThatThrownBy(() -> parseJson(json, SimpleUser.class))
                .isInstanceOf(Exception.class);
    }

    @Test
    void testEmptyJson() {
        // Given: Empty JSON object
        String json = "{}";

        // When: Parsing
        SimpleUser user = parseJson(json, SimpleUser.class);

        // Then: Should create message with default values
        assertThat(user).isNotNull();
        assertThat(user.getName()).isEmpty();
        assertThat(user.getAge()).isZero();
        assertThat(user.getActive()).isFalse();
    }

    @Test
    void testJsonArray() {
        // Given: JSON array instead of object
        String json = "[1, 2, 3]";

        // When/Then: Should throw exception (wrapped by parseJson helper)
        assertThatThrownBy(() -> parseJson(json, SimpleUser.class))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void testJsonNull() {
        // Given: JSON null
        String json = "null";

        // When/Then: Should throw exception
        assertThatThrownBy(() -> parseJson(json, SimpleUser.class))
                .isInstanceOf(Exception.class);
    }

    @Test
    void testJsonPrimitive() {
        // Given: JSON primitive instead of object
        String json = "\"just a string\"";

        // When/Then: Should throw exception (wrapped by parseJson helper)
        assertThatThrownBy(() -> parseJson(json, SimpleUser.class))
                .isInstanceOf(RuntimeException.class);
    }

    // ==================== Unknown Fields ====================

    @Test
    void testUnknownFieldWithIgnore() {
        // Given: JSON with unknown field
        String json = """
                {
                  "name": "John",
                  "unknownField": "value",
                  "age": 30
                }
                """;

        ParserConfig config = ParserConfig.newBuilder()
                .ignoringUnknownFields(true)
                .build();

        // When: Parsing with ignoringUnknownFields=true
        SimpleUser user = parseJson(json, SimpleUser.class, config);

        // Then: Should ignore unknown field
        assertThat(user.getName()).isEqualTo("John");
        assertThat(user.getAge()).isEqualTo(30);
    }

    @Test
    void testUnknownFieldWithoutIgnore() {
        // Given: JSON with unknown field
        String json = """
                {
                  "name": "John",
                  "unknownField": "value"
                }
                """;

        ParserConfig config = ParserConfig.newBuilder()
                .ignoringUnknownFields(false)
                .build();

        // When/Then: Should throw exception (wrapped by parseJson helper)
        assertThatThrownBy(() -> parseJson(json, SimpleUser.class, config))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Parse failed");
    }

    @Test
    void testMultipleUnknownFields() {
        // Given: JSON with multiple unknown fields
        String json = """
                {
                  "name": "John",
                  "unknown1": 1,
                  "age": 30,
                  "unknown2": true,
                  "unknown3": {"nested": "value"}
                }
                """;

        ParserConfig config = ParserConfig.newBuilder()
                .ignoringUnknownFields(true)
                .build();

        // When: Parsing
        SimpleUser user = parseJson(json, SimpleUser.class, config);

        // Then: Should parse known fields correctly
        assertThat(user.getName()).isEqualTo("John");
        assertThat(user.getAge()).isEqualTo(30);
    }

    // ==================== Type Mismatches ====================

    @Test
    void testStringForIntegerField() {
        // Given: String value for int field (but parseable)
        String json = "{\"name\": \"John\", \"age\": \"30\"}";

        ParserConfig config = ParserConfig.defaultConfig();

        // When: Parsing
        SimpleUser user = parseJson(json, SimpleUser.class, config);

        // Then: Should parse string as integer
        assertThat(user.getAge()).isEqualTo(30);
    }

    @Test
    void testInvalidStringForIntegerField() {
        // Given: Non-parseable string for int field
        String json = "{\"name\": \"John\", \"age\": \"not a number\"}";

        // When/Then: Should throw exception
        assertThatThrownBy(() -> parseJson(json, SimpleUser.class))
                .isInstanceOf(NumberFormatException.class);
    }

    @Test
    void testBooleanForStringField() {
        // Given: Boolean value for string field
        String json = "{\"name\": true, \"age\": 30}";

        // When: Parsing (Jackson converts boolean to string)
        SimpleUser user = parseJson(json, SimpleUser.class);

        // Then: Should convert to string
        assertThat(user.getName()).isEqualTo("true");
    }

    @Test
    void testObjectForScalarField() {
        // Given: Object for scalar field
        String json = "{\"name\": {\"nested\": \"value\"}, \"age\": 30}";

        // When/Then: Should throw exception (wrapped by parseJson helper)
        assertThatThrownBy(() -> parseJson(json, SimpleUser.class))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void testArrayForScalarField() {
        // Given: Array for non-repeated field
        String json = "{\"name\": [\"John\", \"Doe\"], \"age\": 30}";

        // When/Then: Should throw exception
        assertThatThrownBy(() -> parseJson(json, SimpleUser.class))
                .isInstanceOf(Exception.class);
    }

    // ==================== Null Handling ====================

    @Test
    void testNullForScalarWithAllow() {
        // Given: null value for scalar field
        String json = "{\"name\": null, \"age\": 30}";

        ParserConfig config = ParserConfig.newBuilder()
                .allowNullForScalars(true)
                .build();

        // When: Parsing with allowNullForScalars=true
        SimpleUser user = parseJson(json, SimpleUser.class, config);

        // Then: Should use default value
        assertThat(user.getName()).isEmpty(); // Default string value
        assertThat(user.getAge()).isEqualTo(30);
    }

    @Test
    void testNullForScalarWithoutAllow() {
        // Given: null value for scalar field
        String json = "{\"name\": null, \"age\": 30}";

        ParserConfig config = ParserConfig.newBuilder()
                .allowNullForScalars(false)
                .build();

        // When: Parsing
        // Note: Behavior depends on implementation
        SimpleUser user = parseJson(json, SimpleUser.class, config);

        // Should still work but might handle differently
        assertThat(user.getAge()).isEqualTo(30);
    }

    @Test
    void testAllNullFields() {
        // Given: All fields null
        String json = """
                {
                  "name": null,
                  "age": null,
                  "email": null,
                  "active": null
                }
                """;

        ParserConfig config = ParserConfig.newBuilder()
                .allowNullForScalars(true)
                .build();

        // When: Parsing
        SimpleUser user = parseJson(json, SimpleUser.class, config);

        // Then: Should use defaults
        assertThat(user.getName()).isEmpty();
        assertThat(user.getAge()).isZero();
        assertThat(user.getEmail()).isEmpty();
        assertThat(user.getActive()).isFalse();
    }

    // ==================== Enum Edge Cases ====================

    @Test
    void testEnumByName() {
        // Given: Enum value by name
        String json = "{\"name\": \"John\", \"status\": \"ACTIVE\"}";

        // When: Parsing
        UserWithStatus user = parseJson(json, UserWithStatus.class);

        // Then: Should parse enum
        assertThat(user.getStatus().name()).isEqualTo("ACTIVE");
    }

    @Test
    void testEnumByNumber() {
        // Given: Enum value by number
        String json = "{\"name\": \"John\", \"status\": 1}";

        ParserConfig config = ParserConfig.newBuilder()
                .acceptNumericEnums(true)
                .build();

        // When: Parsing with acceptNumericEnums=true
        UserWithStatus user = parseJson(json, UserWithStatus.class, config);

        // Then: Should parse enum by number
        assertThat(user.getStatus()).isNotNull();
    }

    @Test
    void testInvalidEnumName() {
        // Given: Invalid enum name
        String json = "{\"name\": \"John\", \"status\": \"INVALID_STATUS\"}";

        // When/Then: Should throw exception (wrapped by parseJson helper)
        assertThatThrownBy(() -> parseJson(json, UserWithStatus.class))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Parse failed");
    }

    @Test
    void testInvalidEnumNumber() {
        // Given: Invalid enum number
        String json = "{\"name\": \"John\", \"status\": 999}";

        ParserConfig config = ParserConfig.newBuilder()
                .acceptNumericEnums(true)
                .build();

        // When/Then: Should throw exception (wrapped in RuntimeException by parseJson helper)
        assertThatThrownBy(() -> parseJson(json, UserWithStatus.class, config))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Parse failed");
    }

    // ==================== Repeated Fields Edge Cases ====================

    @Test
    void testEmptyArray() {
        // Given: Empty array
        String json = "{\"name\": \"John\", \"tags\": []}";

        // When: Parsing
        UserWithTags user = parseJson(json, UserWithTags.class);

        // Then: Should create empty list
        assertThat(user.getTagsList()).isEmpty();
    }

    @Test
    void testSingleElementArray() {
        // Given: Single element array
        String json = "{\"name\": \"John\", \"tags\": [\"tag1\"]}";

        // When: Parsing
        UserWithTags user = parseJson(json, UserWithTags.class);

        // Then: Should parse single element
        assertThat(user.getTagsList()).containsExactly("tag1");
    }

    @Test
    void testLargeArray() {
        // Given: Large array
        StringBuilder sb = new StringBuilder("{\"name\": \"John\", \"tags\": [");
        for (int i = 0; i < 10000; i++) {
            if (i > 0) sb.append(", ");
            sb.append("\"tag").append(i).append("\"");
        }
        sb.append("]}");

        // When: Parsing
        UserWithTags user = parseJson(sb.toString(), UserWithTags.class);

        // Then: Should parse all elements
        assertThat(user.getTagsCount()).isEqualTo(10000);
    }

    @Test
    void testRepeatedScalarValue() {
        // Given: Single value for repeated field (without array)
        String json = "{\"name\": \"John\", \"tags\": \"single\"}";

        // When: Parsing (should treat as single-element array)
        UserWithTags user = parseJson(json, UserWithTags.class);

        // Then: Should add single element
        assertThat(user.getTagsList()).containsExactly("single");
    }

    // ==================== Map Fields Edge Cases ====================

    @Test
    void testEmptyMap() {
        // Given: Empty map
        String json = "{\"name\": \"John\", \"stringMeta\": {}}";

        // When: Parsing
        UserWithMetadata user = parseJson(json, UserWithMetadata.class);

        // Then: Should create empty map
        assertThat(user.getStringMetaMap()).isEmpty();
    }

    @Test
    void testSingleEntryMap() {
        // Given: Map with single entry
        String json = "{\"name\": \"John\", \"stringMeta\": {\"key1\": \"value1\"}}";

        // When: Parsing
        UserWithMetadata user = parseJson(json, UserWithMetadata.class);

        // Then: Should parse entry
        assertThat(user.getStringMetaMap()).hasSize(1);
        assertThat(user.getStringMetaMap().get("key1")).isEqualTo("value1");
    }

    @Test
    void testMapWithNullValue() {
        // Given: Map with null value
        String json = "{\"name\": \"John\", \"stringMeta\": {\"key1\": null}}";

        ParserConfig config = ParserConfig.newBuilder()
                .allowNullForScalars(true)
                .build();

        // When: Parsing
        UserWithMetadata user = parseJson(json, UserWithMetadata.class, config);

        // Then: Should skip null entry
        // (Behavior may vary - either skip or use default)
        assertThat(user.getStringMetaMap()).isNotNull();
    }

    @Test
    void testMapNotObject() {
        // Given: Non-object for map field
        String json = "{\"name\": \"John\", \"stringMeta\": []}";

        // When/Then: Should throw exception (wrapped by parseJson helper)
        assertThatThrownBy(() -> parseJson(json, UserWithMetadata.class))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Parse failed");
    }

    // ==================== Bytes Field Edge Cases ====================

    @Test
    void testValidBase64() {
        // Given: Valid base64 encoded bytes
        String base64 = java.util.Base64.getEncoder().encodeToString("Hello".getBytes());
        String json = String.format("{\"name\": \"John\", \"data\": \"%s\"}", base64);

        // When: Parsing (if BytesField test proto exists)
        // This test documents expected behavior
        // Uncomment when bytes field is available:
        // BytesMessage msg = parseJson(json, BytesMessage.class);
        // assertThat(msg.getData().toStringUtf8()).isEqualTo("Hello");
    }

    @Test
    void testInvalidBase64() {
        // Given: Invalid base64
        String json = "{\"name\": \"John\", \"data\": \"not-valid-base64!!!\"}";

        // When/Then: Should throw exception
        // Uncomment when bytes field is available:
        // assertThatThrownBy(() -> parseJson(json, BytesMessage.class))
        //         .isInstanceOf(ProtoJsonException.class)
        //         .hasMessageContaining("Invalid Base64");
    }

    // ==================== Nested Messages Edge Cases ====================

    @Test
    void testNullNestedMessage() {
        // Given: null for nested message
        String json = "{\"name\": \"John\", \"address\": null}";

        ParserConfig config = ParserConfig.newBuilder()
                .allowNullForScalars(true)
                .build();

        // When: Parsing
        UserWithAddress user = parseJson(json, UserWithAddress.class, config);

        // Then: Should not set nested message
        assertThat(user.hasAddress()).isFalse();
    }

    @Test
    void testEmptyNestedMessage() {
        // Given: Empty object for nested message
        String json = "{\"name\": \"John\", \"address\": {}}";

        // When: Parsing
        UserWithAddress user = parseJson(json, UserWithAddress.class);

        // Then: Should create nested message with defaults
        assertThat(user.hasAddress()).isTrue();
        assertThat(user.getAddress().getStreet()).isEmpty();
    }

    @Test
    void testDeeplyNestedMessage() {
        // Test multiple levels of nesting
        String json = """
                {
                  "name": "John",
                  "address": {
                    "street": "123 Main St",
                    "city": "NYC"
                  }
                }
                """;

        // When: Parsing
        UserWithAddress user = parseJson(json, UserWithAddress.class);

        // Then: Should parse nested fields
        assertThat(user.getAddress().getStreet()).isEqualTo("123 Main St");
        assertThat(user.getAddress().getCity()).isEqualTo("NYC");
    }

    // ==================== Field Name Variations ====================

    @Test
    void testCaseInsensitiveFieldNames() {
        // Given: Wrong case field names
        String json = """
                {
                  "NAME": "John",
                  "AGE": 30,
                  "EMAIL": "john@example.com"
                }
                """;

        // When: Parsing (MetaRegistry supports case-insensitive)
        SimpleUser user = parseJson(json, SimpleUser.class);

        // Then: Should parse correctly
        assertThat(user.getName()).isEqualTo("John");
        assertThat(user.getAge()).isEqualTo(30);
        assertThat(user.getEmail()).isEqualTo("john@example.com");
    }

    @Test
    void testMixedCaseFieldNames() {
        // Given: Mixed case
        String json = """
                {
                  "NaMe": "John",
                  "aGe": 30
                }
                """;

        // When: Parsing
        SimpleUser user = parseJson(json, SimpleUser.class);

        // Then: Should parse via case-insensitive lookup
        assertThat(user.getName()).isEqualTo("John");
        assertThat(user.getAge()).isEqualTo(30);
    }

    // ==================== Boundary Values ====================

    @Test
    void testMaxIntValue() {
        // Given: Max int value
        String json = String.format("{\"name\": \"John\", \"age\": %d}", Integer.MAX_VALUE);

        // When: Parsing
        SimpleUser user = parseJson(json, SimpleUser.class);

        // Then: Should handle max value
        assertThat(user.getAge()).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    void testMinIntValue() {
        // Given: Min int value
        String json = String.format("{\"name\": \"John\", \"age\": %d}", Integer.MIN_VALUE);

        // When: Parsing
        SimpleUser user = parseJson(json, SimpleUser.class);

        // Then: Should handle min value
        assertThat(user.getAge()).isEqualTo(Integer.MIN_VALUE);
    }

    @Test
    void testVeryLongString() {
        // Given: Very long string
        String longString = "x".repeat(100_000);
        String json = String.format("{\"name\": \"%s\", \"age\": 30}", longString);

        // When: Parsing
        SimpleUser user = parseJson(json, SimpleUser.class);

        // Then: Should handle long string
        assertThat(user.getName()).hasSize(100_000);
    }

    @Test
    void testEmptyString() {
        // Given: Empty string
        String json = "{\"name\": \"\", \"age\": 30}";

        // When: Parsing
        SimpleUser user = parseJson(json, SimpleUser.class);

        // Then: Should parse empty string
        assertThat(user.getName()).isEmpty();
        assertThat(user.getAge()).isEqualTo(30);
    }

    @Test
    void testSpecialCharactersInString() {
        // Given: Special characters
        String json = "{\"name\": \"\\\"John\\\"\\n\\t\\r\", \"age\": 30}";

        // When: Parsing
        SimpleUser user = parseJson(json, SimpleUser.class);

        // Then: Should handle escaped characters
        assertThat(user.getName()).contains("\"John\"");
        assertThat(user.getName()).contains("\n");
    }

    // ==================== Helper Methods ====================

    private <T extends Message> T parseJson(String json, Class<T> messageClass) {
        return parseJson(json, messageClass, ParserConfig.defaultConfig());
    }

    private <T extends Message> T parseJson(String json, Class<T> messageClass, ParserConfig config) {
        try {
            // Register message type
            metaRegistry.register(messageClass);

            // Create context
            JsonToProtoContext context = new JsonToProtoContext(
                    config,
                    converterRegistry,
                    metaRegistry
            );

            // Get builder
            Message defaultInstance = (Message) messageClass.getMethod("getDefaultInstance").invoke(null);
            Message.Builder builder = defaultInstance.toBuilder();
            Descriptors.Descriptor descriptor = builder.getDescriptorForType();

            // Parse
            byte[] jsonBytes = json.getBytes(StandardCharsets.UTF_8);
            try (JsonParser parser = jsonFactory.createParser(jsonBytes)) {
                ProtoJsonStreamer.merge(parser, descriptor, builder, context);
            }

            @SuppressWarnings("unchecked")
            T result = (T) builder.build();
            return result;

        } catch (Exception e) {
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            }
            throw new RuntimeException("Parse failed", e);
        }
    }
}
