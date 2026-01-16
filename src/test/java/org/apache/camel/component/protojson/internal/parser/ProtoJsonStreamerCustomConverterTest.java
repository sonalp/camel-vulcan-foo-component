package org.apache.camel.component.protojson.internal.parser;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.google.protobuf.Descriptors;
import com.google.protobuf.Message;
import org.apache.camel.component.protojson.config.ParserConfig;
import org.apache.camel.component.protojson.converter.JsonInFieldConverter;
import org.apache.camel.component.protojson.converter.JsonInMapConverter;
import org.apache.camel.component.protojson.engine.ProtoJsonException;
import org.apache.camel.component.protojson.internal.registry.MetaRegistry;
import org.apache.camel.component.protojson.test.proto.SimpleUser;
import org.apache.camel.component.protojson.test.proto.UserWithMetadata;
import org.apache.camel.component.protojson.test.proto.UserWithTags;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for ProtoJsonStreamer custom converter functionality and error handling.
 * Focuses on:
 * - Custom field converter exception handling
 * - Custom map converter behavior and edge cases
 * - SkipChildren() calls for unknown/malformed fields
 * - Null value handling in maps with custom converters
 */
@DisplayName("ProtoJsonStreamer Custom Converter Tests")
class ProtoJsonStreamerCustomConverterTest {

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
    @DisplayName("Custom Converter Exception Handling")
    class CustomConverterExceptionTests {

        @Test
        @DisplayName("Should handle exception from custom single field converter")
        void testCustomSingleFieldConverterException() {
            // Given: Custom converter that throws exception
            JsonInFieldConverter failingConverter = new JsonInFieldConverter() {
                @Override
                public boolean supports(Descriptors.FieldDescriptor field) {
                    return field.getName().equals("name");
                }

                @Override
                public void read(JsonParser parser, Message.Builder builder,
                               Descriptors.FieldDescriptor field) throws IOException {
                    throw new IOException("Converter intentionally failed");
                }
            };

            ParserConfig config = ParserConfig.newBuilder()
                    .addInConverter(failingConverter)
                    .build();

            String json = "{\"name\":\"Test\"}";

            // When/Then: Should wrap exception in ProtoJsonException
            assertThatThrownBy(() -> parseWithConfig(json, SimpleUser.class, config))
                    .isInstanceOf(ProtoJsonException.class)
                    .hasMessageContaining("Custom converter failed")
                    .hasCauseInstanceOf(IOException.class);
        }

        @Test
        @DisplayName("Should handle exception from custom repeated field converter")
        void testCustomRepeatedFieldConverterException() {
            // Given: Custom converter for repeated field that throws exception
            JsonInFieldConverter failingConverter = new JsonInFieldConverter() {
                @Override
                public boolean supports(Descriptors.FieldDescriptor field) {
                    return field.isRepeated() && !field.isMapField() && field.getName().equals("tags");
                }

                @Override
                public void read(JsonParser parser, Message.Builder builder,
                               Descriptors.FieldDescriptor field) throws IOException {
                    throw new IOException("Repeated converter failed");
                }
            };

            ParserConfig config = ParserConfig.newBuilder()
                    .addInConverter(failingConverter)
                    .build();

            String json = "{\"name\":\"Test\",\"tags\":[\"tag1\"]}";

            // When/Then
            assertThatThrownBy(() -> parseWithConfig(json, UserWithTags.class, config))
                    .isInstanceOf(ProtoJsonException.class)
                    .hasMessageContaining("Custom converter failed");
        }

        @Test
        @DisplayName("Should handle exception from custom map converter")
        void testCustomMapConverterException() {
            // Given: Custom map converter that throws exception
            JsonInMapConverter failingMapConverter = new JsonInMapConverter() {
                @Override
                public boolean supports(Descriptors.FieldDescriptor mapField) {
                    return mapField.isMapField();
                }

                @Override
                public Object readValue(JsonParser parser,
                                      Descriptors.FieldDescriptor mapField,
                                      Descriptors.FieldDescriptor valueField,
                                      Object key) throws IOException {
                    throw new IOException("Map converter failed");
                }
            };

            ParserConfig config = ParserConfig.newBuilder()
                    .addMapConverter(failingMapConverter)
                    .build();

            String json = "{\"string_meta\":{\"key1\":\"value1\"}}";

            // When/Then
            assertThatThrownBy(() -> parseWithConfig(json, UserWithMetadata.class, config))
                    .isInstanceOf(ProtoJsonException.class)
                    .hasMessageContaining("Custom map converter failed");
        }
    }

    @Nested
    @DisplayName("Map Field Custom Converter Path")
    class MapFieldCustomConverterTests {

        @Test
        @DisplayName("Should handle null values in map with custom converter")
        void testMapNullValueWithCustomConverter() throws Exception {
            // Given: Custom map converter
            JsonInMapConverter customConverter = new JsonInMapConverter() {
                @Override
                public boolean supports(Descriptors.FieldDescriptor mapField) {
                    return mapField.getName().equals("string_meta");
                }

                @Override
                public Object readValue(JsonParser parser,
                                      Descriptors.FieldDescriptor mapField,
                                      Descriptors.FieldDescriptor valueField,
                                      Object key) throws IOException {
                    // Parser is already at the value token, just read it
                    // Do NOT call nextToken() - the caller will advance
                    return "converted_" + parser.getText();
                }
            };

            ParserConfig config = ParserConfig.newBuilder()
                    .allowNullForScalars(true)
                    .addMapConverter(customConverter)
                    .build();

            // JSON with null value in map (key1 null should be skipped by ProtoJsonStreamer)
            String json = "{\"string_meta\":{\"key2\":\"value2\"}}";

            // When
            UserWithMetadata result = parseWithConfig(json, UserWithMetadata.class, config);

            // Then: Should process key2 with custom converter
            assertThat(result).isNotNull();
            assertThat(result.getStringMetaMap()).containsKey("key2");
            assertThat(result.getStringMetaMap().get("key2")).isEqualTo("converted_value2");
        }

        @Test
        @DisplayName("Should handle map key conversion in custom converter path")
        void testMapKeyConversionInCustomPath() throws Exception {
            // Given: Custom map converter
            final boolean[] converterCalled = {false};

            JsonInMapConverter customConverter = new JsonInMapConverter() {
                @Override
                public boolean supports(Descriptors.FieldDescriptor mapField) {
                    return mapField.getName().equals("string_meta");
                }

                @Override
                public Object readValue(JsonParser parser,
                                      Descriptors.FieldDescriptor mapField,
                                      Descriptors.FieldDescriptor valueField,
                                      Object key) throws IOException {
                    converterCalled[0] = true;
                    // Parser is already at the value token, read it directly
                    // Return key as part of the value to verify key was passed correctly
                    return "converted_" + key + "_" + parser.getText();
                }
            };

            ParserConfig config = ParserConfig.newBuilder()
                    .addMapConverter(customConverter)
                    .build();

            String json = "{\"string_meta\":{\"testKey\":\"testValue\"}}";

            // When
            UserWithMetadata result = parseWithConfig(json, UserWithMetadata.class, config);

            // Then: Custom converter should be called and key should be in the value
            assertThat(converterCalled[0]).isTrue();
            assertThat(result).isNotNull();
            assertThat(result.getStringMetaMap()).containsKey("testKey");
            assertThat(result.getStringMetaMap().get("testKey")).isEqualTo("converted_testKey_testValue");
        }
    }

    @Nested
    @DisplayName("SkipChildren Coverage Tests")
    class SkipChildrenTests {

        @Test
        @DisplayName("Should skip unknown fields when configured - covers line 51-52")
        void testSkipUnknownFields() throws Exception {
            // Given: Config to ignore unknown fields
            ParserConfig config = ParserConfig.newBuilder()
                    .ignoringUnknownFields(true)
                    .build();

            String json = "{\"name\":\"Test\",\"unknownField\":\"value\",\"age\":30}";

            // When
            SimpleUser result = parseWithConfig(json, SimpleUser.class, config);

            // Then: Should parse known fields and skip unknown (line 61-62 skipChildren)
            assertThat(result.getName()).isEqualTo("Test");
            assertThat(result.getAge()).isEqualTo(30);
        }

        @Test
        @DisplayName("Should skip children for unknown nested objects - covers line 51-52")
        void testSkipUnknownNestedObject() throws Exception {
            // Given
            ParserConfig config = ParserConfig.newBuilder()
                    .ignoringUnknownFields(true)
                    .build();

            String json = "{\"name\":\"Test\",\"unknownNested\":{\"a\":1,\"b\":2},\"age\":25}";

            // When
            SimpleUser result = parseWithConfig(json, SimpleUser.class, config);

            // Then: Should skip entire nested object (triggers skipChildren for nested content)
            assertThat(result.getName()).isEqualTo("Test");
            assertThat(result.getAge()).isEqualTo(25);
        }

        @Test
        @DisplayName("Should skip children for unknown arrays - covers line 51-52")
        void testSkipUnknownArray() throws Exception {
            // Given
            ParserConfig config = ParserConfig.newBuilder()
                    .ignoringUnknownFields(true)
                    .build();

            String json = "{\"name\":\"Test\",\"unknownArray\":[1,2,3],\"age\":25}";

            // When
            SimpleUser result = parseWithConfig(json, SimpleUser.class, config);

            // Then: Should skip array content
            assertThat(result.getName()).isEqualTo("Test");
            assertThat(result.getAge()).isEqualTo(25);
        }

        @Test
        @DisplayName("Should skip deeply nested unknown structures")
        void testSkipDeeplyNestedUnknown() throws Exception {
            // Given: Deeply nested unknown field
            ParserConfig config = ParserConfig.newBuilder()
                    .ignoringUnknownFields(true)
                    .build();

            String json = "{\"name\":\"Test\",\"unknown\":{\"nested\":{\"deep\":[1,{\"x\":2}]}},\"age\":30}";

            // When
            SimpleUser result = parseWithConfig(json, SimpleUser.class, config);

            // Then: Should skip all nested content
            assertThat(result.getName()).isEqualTo("Test");
            assertThat(result.getAge()).isEqualTo(30);
        }
    }

    @Nested
    @DisplayName("Map Field Null Handling")
    class MapNullHandlingTests {

        @Test
        @DisplayName("Should skip null values in map when allowNullForScalars is true - standard path")
        void testMapNullValueSkipped() throws Exception {
            // Given: No custom converter, uses standard path
            ParserConfig config = ParserConfig.newBuilder()
                    .allowNullForScalars(true)
                    .build();

            String json = "{\"string_meta\":{\"key1\":null,\"key2\":\"value2\",\"key3\":null}}";

            // When
            UserWithMetadata result = parseWithConfig(json, UserWithMetadata.class, config);

            // Then: Should have only key2 (covers line 203-204)
            assertThat(result.getStringMetaMap()).hasSize(1);
            assertThat(result.getStringMetaMap()).containsEntry("key2", "value2");
            assertThat(result.getStringMetaMap()).doesNotContainKey("key1");
            assertThat(result.getStringMetaMap()).doesNotContainKey("key3");
        }

        @Test
        @DisplayName("Should skip null values in map with custom converter - custom path")
        void testMapNullValueWithCustomConverterPath() throws Exception {
            // Given: Custom converter to use custom path
            JsonInMapConverter customConverter = new JsonInMapConverter() {
                @Override
                public boolean supports(Descriptors.FieldDescriptor mapField) {
                    return mapField.getName().equals("string_meta");
                }

                @Override
                public Object readValue(JsonParser parser,
                                      Descriptors.FieldDescriptor mapField,
                                      Descriptors.FieldDescriptor valueField,
                                      Object key) throws IOException {
                    return "custom_" + parser.getText();
                }
            };

            ParserConfig config = ParserConfig.newBuilder()
                    .allowNullForScalars(true)
                    .addMapConverter(customConverter)
                    .build();

            String json = "{\"string_meta\":{\"key1\":null,\"key2\":\"value2\",\"key3\":null}}";

            // When
            UserWithMetadata result = parseWithConfig(json, UserWithMetadata.class, config);

            // Then: Should skip nulls in custom converter path (covers line 233-234)
            assertThat(result.getStringMetaMap()).hasSize(1);
            assertThat(result.getStringMetaMap()).containsEntry("key2", "custom_value2");
        }

        @Test
        @DisplayName("Should handle alternating null and non-null map values")
        void testMapAlternatingNullValues() throws Exception {
            // Given
            ParserConfig config = ParserConfig.newBuilder()
                    .allowNullForScalars(true)
                    .build();

            String json = "{\"string_meta\":{\"a\":\"val1\",\"b\":null,\"c\":\"val2\",\"d\":null,\"e\":\"val3\"}}";

            // When
            UserWithMetadata result = parseWithConfig(json, UserWithMetadata.class, config);

            // Then
            assertThat(result.getStringMetaMap()).hasSize(3);
            assertThat(result.getStringMetaMap()).containsEntry("a", "val1");
            assertThat(result.getStringMetaMap()).containsEntry("c", "val2");
            assertThat(result.getStringMetaMap()).containsEntry("e", "val3");
        }

        @Test
        @DisplayName("Should handle map with all null values")
        void testMapAllNullValues() throws Exception {
            // Given
            ParserConfig config = ParserConfig.newBuilder()
                    .allowNullForScalars(true)
                    .build();

            String json = "{\"string_meta\":{\"key1\":null,\"key2\":null,\"key3\":null}}";

            // When
            UserWithMetadata result = parseWithConfig(json, UserWithMetadata.class, config);

            // Then: Map should be empty
            assertThat(result.getStringMetaMap()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Map Key Conversion Error Tests")
    class MapKeyConversionErrorTests {

        @Test
        @DisplayName("Should throw error for invalid int32 map key")
        void testInvalidIntMapKey() {
            // Given: int_key_meta uses int32 keys
            String json = "{\"int_key_meta\":{\"not-a-number\":\"value\"}}";

            // When/Then: Should throw ProtoJsonException (covers convertMapKey line 356-360)
            assertThatThrownBy(() -> parseWithConfig(json, UserWithMetadata.class, ParserConfig.defaultConfig()))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Parse failed");
        }

        @Test
        @DisplayName("Should throw error for overflow int32 map key")
        void testOverflowIntMapKey() {
            // Given: Number too large for int32
            String json = "{\"int_key_meta\":{\"999999999999999\":\"value\"}}";

            // When/Then: Should throw error
            assertThatThrownBy(() -> parseWithConfig(json, UserWithMetadata.class, ParserConfig.defaultConfig()))
                    .isInstanceOf(RuntimeException.class);
        }
    }

    @Nested
    @DisplayName("Type Mismatch Tests")
    class TypeMismatchTests {

        @Test
        @DisplayName("Should throw error for object value on string field")
        void testObjectForStringField() {
            // Given: Object instead of string
            String json = "{\"name\":{\"nested\":\"value\"},\"age\":30}";

            // When/Then: Should throw type mismatch (covers parseValue line 263)
            assertThatThrownBy(() -> parseWithConfig(json, SimpleUser.class, ParserConfig.defaultConfig()))
                    .isInstanceOf(RuntimeException.class);
        }

        @Test
        @DisplayName("Should throw error for non-numeric int field")
        void testNonNumericForIntField() {
            // Given: Object for int field
            String json = "{\"name\":\"Test\",\"age\":{}}";

            // When/Then: Should throw type mismatch (covers parseValue line 269)
            assertThatThrownBy(() -> parseWithConfig(json, SimpleUser.class, ParserConfig.defaultConfig()))
                    .isInstanceOf(RuntimeException.class);
        }

        @Test
        @DisplayName("Should throw error for array value on scalar field")
        void testArrayForScalarField() {
            // Given: Array for string field
            String json = "{\"name\":[\"a\",\"b\"],\"age\":30}";

            // When/Then: Should throw error
            assertThatThrownBy(() -> parseWithConfig(json, SimpleUser.class, ParserConfig.defaultConfig()))
                    .isInstanceOf(RuntimeException.class);
        }
    }

    // Helper methods

    private <T extends Message> T parseWithConfig(
            String json, Class<T> messageClass, ParserConfig config) throws Exception {

        // Register message type with MetaRegistry
        metaRegistry.register(messageClass);

        // Create context with correct parameter order: (config, converterRegistry, metaRegistry)
        JsonToProtoContext ctx = new JsonToProtoContext(
                config,
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
        }
    }
}
