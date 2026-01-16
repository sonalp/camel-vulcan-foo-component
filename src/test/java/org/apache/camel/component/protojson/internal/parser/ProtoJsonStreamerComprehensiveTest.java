package org.apache.camel.component.protojson.internal.parser;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.google.protobuf.Message;
import org.apache.camel.component.protojson.config.ParserConfig;
import org.apache.camel.component.protojson.internal.registry.MetaRegistry;
import org.apache.camel.component.protojson.test.proto.AllMapKeyTypes;
import org.apache.camel.component.protojson.test.proto.AllScalarTypes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.*;

/**
 * Comprehensive tests using AllScalarTypes and AllMapKeyTypes proto messages.
 * This approach is more maintainable than creating individual JSON strings for each test case.
 */
@DisplayName("ProtoJsonStreamer Comprehensive Type Tests")
class ProtoJsonStreamerComprehensiveTest {

    private JsonFactory jsonFactory;
    private MetaRegistry metaRegistry;
    private MessageTypeConverterRegistry converterRegistry;

    @BeforeEach
    void setUp() {
        jsonFactory = new JsonFactory();
        metaRegistry = new MetaRegistry();
        converterRegistry = MessageTypeConverterRegistry.withDefault();
    }

    @Nested
    @DisplayName("All Scalar Types - Type Mismatch Tests")
    class ScalarTypeMismatchTests {

        @Test
        @DisplayName("Should throw error when object provided for int64 field")
        void testObjectForInt64Field() {
            // Given: Object for int64 field (covers parseValue LONG type mismatch - line 274)
            String json = "{\"int64_field\":{\"nested\":\"value\"}}";

            // When/Then: Should throw type mismatch error
            assertThatThrownBy(() -> parseJson(json, AllScalarTypes.class))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Parse failed");
        }

        @Test
        @DisplayName("Should throw error when object provided for double field")
        void testObjectForDoubleField() {
            // Given: Object for double field (covers parseValue DOUBLE type mismatch - line 279)
            String json = "{\"double_field\":{\"nested\":\"value\"}}";

            // When/Then: Should throw type mismatch error
            assertThatThrownBy(() -> parseJson(json, AllScalarTypes.class))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Parse failed");
        }

        @Test
        @DisplayName("Should throw error when object provided for float field")
        void testObjectForFloatField() {
            // Given: Object for float field (covers parseValue FLOAT type mismatch - line 284)
            String json = "{\"float_field\":{\"nested\":\"value\"}}";

            // When/Then: Should throw type mismatch error
            assertThatThrownBy(() -> parseJson(json, AllScalarTypes.class))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Parse failed");
        }

        @Test
        @DisplayName("Should throw error when array provided for bytes field")
        void testArrayForBytesField() {
            // Given: Array for bytes field (covers parseValue BYTE_STRING type mismatch - line 292)
            String json = "{\"bytes_field\":[1,2,3]}";

            // When/Then: Should throw type mismatch error
            assertThatThrownBy(() -> parseJson(json, AllScalarTypes.class))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Parse failed");
        }

        @Test
        @DisplayName("Should parse all scalar types successfully with valid JSON")
        void testValidScalarTypes() throws Exception {
            // Given: Valid JSON for all scalar types
            String json = """
                    {
                        "string_field": "test",
                        "int32_field": 123,
                        "int64_field": 9223372036854775807,
                        "float_field": 3.14,
                        "double_field": 2.718281828,
                        "bool_field": true,
                        "bytes_field": "SGVsbG8="
                    }
                    """;

            // When
            AllScalarTypes result = parseJson(json, AllScalarTypes.class);

            // Then: All fields should be parsed correctly
            assertThat(result.getStringField()).isEqualTo("test");
            assertThat(result.getInt32Field()).isEqualTo(123);
            assertThat(result.getInt64Field()).isEqualTo(9223372036854775807L);
            assertThat(result.getFloatField()).isCloseTo(3.14f, within(0.01f));
            assertThat(result.getDoubleField()).isCloseTo(2.718281828, within(0.000001));
            assertThat(result.getBoolField()).isTrue();
            assertThat(result.getBytesField().toStringUtf8()).isEqualTo("Hello");
        }

        @Test
        @DisplayName("Should parse string representations of numeric types")
        void testStringToNumericConversion() throws Exception {
            // Given: String values for numeric fields (should be parsed)
            String json = """
                    {
                        "int32_field": "456",
                        "int64_field": "789",
                        "float_field": "1.5",
                        "double_field": "2.5"
                    }
                    """;

            // When
            AllScalarTypes result = parseJson(json, AllScalarTypes.class);

            // Then: String values should be converted to numbers
            assertThat(result.getInt32Field()).isEqualTo(456);
            assertThat(result.getInt64Field()).isEqualTo(789L);
            assertThat(result.getFloatField()).isCloseTo(1.5f, within(0.01f));
            assertThat(result.getDoubleField()).isCloseTo(2.5, within(0.01));
        }
    }

    @Nested
    @DisplayName("All Map Key Types - Conversion Tests")
    class MapKeyTypeTests {

        @Test
        @DisplayName("Should parse string key map successfully")
        void testStringKeyMap() throws Exception {
            // Given
            String json = "{\"string_key_map\":{\"key1\":\"value1\",\"key2\":\"value2\"}}";

            // When
            AllMapKeyTypes result = parseJson(json, AllMapKeyTypes.class);

            // Then
            assertThat(result.getStringKeyMapMap()).containsEntry("key1", "value1");
            assertThat(result.getStringKeyMapMap()).containsEntry("key2", "value2");
        }

        @Test
        @DisplayName("Should parse int32 key map successfully")
        void testInt32KeyMap() throws Exception {
            // Given
            String json = "{\"int32_key_map\":{\"123\":\"value1\",\"456\":\"value2\"}}";

            // When
            AllMapKeyTypes result = parseJson(json, AllMapKeyTypes.class);

            // Then
            assertThat(result.getInt32KeyMapMap()).containsEntry(123, "value1");
            assertThat(result.getInt32KeyMapMap()).containsEntry(456, "value2");
        }

        @Test
        @DisplayName("Should parse int64 key map successfully")
        void testInt64KeyMap() throws Exception {
            // Given: Tests LONG map key conversion (covers convertMapKey line 363-372)
            String json = "{\"int64_key_map\":{\"9223372036854775807\":\"max\",\"-9223372036854775808\":\"min\"}}";

            // When
            AllMapKeyTypes result = parseJson(json, AllMapKeyTypes.class);

            // Then
            assertThat(result.getInt64KeyMapMap()).containsEntry(9223372036854775807L, "max");
            assertThat(result.getInt64KeyMapMap()).containsEntry(-9223372036854775808L, "min");
        }

        @Test
        @DisplayName("Should parse bool key map successfully")
        void testBoolKeyMap() throws Exception {
            // Given: Tests BOOLEAN map key conversion (covers convertMapKey line 374-382)
            String json = "{\"bool_key_map\":{\"true\":\"yes\",\"false\":\"no\"}}";

            // When
            AllMapKeyTypes result = parseJson(json, AllMapKeyTypes.class);

            // Then
            assertThat(result.getBoolKeyMapMap()).containsEntry(true, "yes");
            assertThat(result.getBoolKeyMapMap()).containsEntry(false, "no");
        }

        @Test
        @DisplayName("Should throw error for invalid int64 map key")
        void testInvalidInt64MapKey() {
            // Given: Invalid int64 key (covers convertMapKey error handling - line 366-371)
            String json = "{\"int64_key_map\":{\"not-a-number\":\"value\"}}";

            // When/Then: Should throw error
            assertThatThrownBy(() -> parseJson(json, AllMapKeyTypes.class))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Parse failed");
        }

        @Test
        @DisplayName("Should throw error for invalid bool map key")
        void testInvalidBoolMapKey() {
            // Given: Invalid bool key (covers convertMapKey error handling - line 378-382)
            String json = "{\"bool_key_map\":{\"not-a-bool\":\"value\"}}";

            // When/Then: Should throw error
            assertThatThrownBy(() -> parseJson(json, AllMapKeyTypes.class))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Parse failed");
        }

        @Test
        @DisplayName("Should handle case insensitive bool keys")
        void testCaseInsensitiveBoolKeys() throws Exception {
            // Given: Case variations of bool keys
            String json = "{\"bool_key_map\":{\"TRUE\":\"upper\",\"False\":\"mixed\"}}";

            // When
            AllMapKeyTypes result = parseJson(json, AllMapKeyTypes.class);

            // Then: Should parse case-insensitively
            assertThat(result.getBoolKeyMapMap()).containsEntry(true, "upper");
            assertThat(result.getBoolKeyMapMap()).containsEntry(false, "mixed");
        }

        @Test
        @DisplayName("Should parse all map key types in one message")
        void testAllMapKeyTypes() throws Exception {
            // Given: Comprehensive test with all map key types
            String json = """
                    {
                        "name": "test",
                        "string_key_map": {"s1": "sv1", "s2": "sv2"},
                        "int32_key_map": {"1": "i1", "2": "i2"},
                        "int64_key_map": {"1000000": "l1", "2000000": "l2"},
                        "bool_key_map": {"true": "t", "false": "f"}
                    }
                    """;

            // When
            AllMapKeyTypes result = parseJson(json, AllMapKeyTypes.class);

            // Then: All maps should be parsed correctly
            assertThat(result.getName()).isEqualTo("test");
            assertThat(result.getStringKeyMapMap()).hasSize(2);
            assertThat(result.getInt32KeyMapMap()).hasSize(2);
            assertThat(result.getInt64KeyMapMap()).hasSize(2);
            assertThat(result.getBoolKeyMapMap()).hasSize(2);
        }
    }

    // Helper method
    private <T extends Message> T parseJson(String json, Class<T> messageClass) throws Exception {
        // Register message type
        metaRegistry.register(messageClass);

        // Create context
        JsonToProtoContext ctx = new JsonToProtoContext(
                ParserConfig.defaultConfig(),
                converterRegistry,
                metaRegistry
        );

        try (JsonParser parser = jsonFactory.createParser(
                new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)))) {

            T defaultInstance = (T) messageClass.getMethod("getDefaultInstance").invoke(null);
            Message.Builder builder = defaultInstance.toBuilder();

            ProtoJsonStreamer.merge(parser, defaultInstance.getDescriptorForType(), builder, ctx);

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
