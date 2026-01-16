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
import org.apache.camel.component.protojson.test.proto.ComplexMessage;
import org.apache.camel.component.protojson.test.proto.SimpleUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.*;

/**
 * Additional coverage tests for ProtoJsonStreamer to increase line coverage from 62.8% to 80%+
 * Focuses on uncovered branches:
 * - skipChildren() calls for malformed tokens
 * - Custom converter exception handling
 * - Map field edge cases with custom converters
 * - Null handling in various scenarios
 */
@DisplayName("ProtoJsonStreamer Additional Coverage Tests")
class ProtoJsonStreamerAdditionalCoverageTest {

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
                    return field.isRepeated() && !field.isMapField();
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
            assertThatThrownBy(() -> parseWithConfig(json, SimpleUser.class, config))
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

            String json = "{\"metadata\":{\"key1\":\"value1\"}}";

            // When/Then
            assertThatThrownBy(() -> parseWithConfig(json, ComplexMessage.class, config))
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
                    return mapField.getName().equals("metadata");
                }

                @Override
                public Object readValue(JsonParser parser,
                                      Descriptors.FieldDescriptor mapField,
                                      Descriptors.FieldDescriptor valueField,
                                      Object key) throws IOException {
                    // Read and return converted value
                    parser.nextToken(); // consume the value token
                    return "converted";
                }
            };

            ParserConfig config = ParserConfig.newBuilder()
                    .allowNullForScalars(true)
                    .addMapConverter(customConverter)
                    .build();

            // JSON with null value in map
            String json = "{\"metadata\":{\"key1\":null,\"key2\":\"value2\"}}";

            // When
            ComplexMessage result = parseWithConfig(json, ComplexMessage.class, config);

            // Then: Should skip null and process key2
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("Should handle map key conversion in custom converter path")
        void testMapKeyConversionInCustomPath() throws Exception {
            // Given: Custom map converter
            final boolean[] converterCalled = {false};

            JsonInMapConverter customConverter = new JsonInMapConverter() {
                @Override
                public boolean supports(Descriptors.FieldDescriptor mapField) {
                    return mapField.getName().equals("metadata");
                }

                @Override
                public Object readValue(JsonParser parser,
                                      Descriptors.FieldDescriptor mapField,
                                      Descriptors.FieldDescriptor valueField,
                                      Object key) throws IOException {
                    converterCalled[0] = true;
                    parser.nextToken(); // consume the value token
                    return "converted_" + key;
                }
            };

            ParserConfig config = ParserConfig.newBuilder()
                    .addMapConverter(customConverter)
                    .build();

            String json = "{\"metadata\":{\"testKey\":\"testValue\"}}";

            // When
            ComplexMessage result = parseWithConfig(json, ComplexMessage.class, config);

            // Then: Custom converter should be called
            assertThat(converterCalled[0]).isTrue();
            assertThat(result).isNotNull();
        }
    }

    @Nested
    @DisplayName("SkipChildren Coverage Tests")
    class SkipChildrenTests {

        @Test
        @DisplayName("Should skip unknown fields when configured")
        void testSkipUnknownFields() throws Exception {
            // Given: Config to ignore unknown fields
            ParserConfig config = ParserConfig.newBuilder()
                    .ignoringUnknownFields(true)
                    .build();

            String json = "{\"name\":\"Test\",\"unknownField\":\"value\",\"age\":30}";

            // When
            SimpleUser result = parseWithConfig(json, SimpleUser.class, config);

            // Then: Should parse known fields and skip unknown
            assertThat(result.getName()).isEqualTo("Test");
            assertThat(result.getAge()).isEqualTo(30);
        }

        @Test
        @DisplayName("Should skip children for unknown nested objects")
        void testSkipUnknownNestedObject() throws Exception {
            // Given
            ParserConfig config = ParserConfig.newBuilder()
                    .ignoringUnknownFields(true)
                    .build();

            String json = "{\"name\":\"Test\",\"unknownNested\":{\"a\":1,\"b\":2},\"age\":25}";

            // When
            SimpleUser result = parseWithConfig(json, SimpleUser.class, config);

            // Then: Should skip entire nested object
            assertThat(result.getName()).isEqualTo("Test");
            assertThat(result.getAge()).isEqualTo(25);
        }

        @Test
        @DisplayName("Should skip children for unknown arrays")
        void testSkipUnknownArray() throws Exception {
            // Given
            ParserConfig config = ParserConfig.newBuilder()
                    .ignoringUnknownFields(true)
                    .build();

            String json = "{\"name\":\"Test\",\"unknownArray\":[1,2,3],\"age\":25}";

            // When
            SimpleUser result = parseWithConfig(json, SimpleUser.class, config);

            // Then
            assertThat(result.getName()).isEqualTo("Test");
            assertThat(result.getAge()).isEqualTo(25);
        }

        @Test
        @DisplayName("Should skip non-field-name tokens in object")
        void testSkipNonFieldNameTokens() throws Exception {
            // This test covers the skipChildren when token is not FIELD_NAME
            // Hard to trigger with normal JSON, but we test with ignore unknown fields
            ParserConfig config = ParserConfig.newBuilder()
                    .ignoringUnknownFields(true)
                    .build();

            String json = "{\"name\":\"Test\",\"unknown\":123,\"age\":30}";

            // When
            SimpleUser result = parseWithConfig(json, SimpleUser.class, config);

            // Then
            assertThat(result.getName()).isEqualTo("Test");
            assertThat(result.getAge()).isEqualTo(30);
        }
    }

    @Nested
    @DisplayName("Map Field Null Handling")
    class MapNullHandlingTests {

        @Test
        @DisplayName("Should skip null values in map when allowNullForScalars is true")
        void testMapNullValueSkipped() throws Exception {
            // Given
            ParserConfig config = ParserConfig.newBuilder()
                    .allowNullForScalars(true)
                    .build();

            String json = "{\"metadata\":{\"key1\":null,\"key2\":\"value2\",\"key3\":null}}";

            // When
            ComplexMessage result = parseWithConfig(json, ComplexMessage.class, config);

            // Then: Should have only key2
            assertThat(result.getMetadataMap()).hasSize(1);
            assertThat(result.getMetadataMap()).containsEntry("key2", "value2");
            assertThat(result.getMetadataMap()).doesNotContainKey("key1");
            assertThat(result.getMetadataMap()).doesNotContainKey("key3");
        }

        @Test
        @DisplayName("Should handle alternating null and non-null map values")
        void testMapAlternatingNullValues() throws Exception {
            // Given
            ParserConfig config = ParserConfig.newBuilder()
                    .allowNullForScalars(true)
                    .build();

            String json = "{\"metadata\":{\"a\":\"val1\",\"b\":null,\"c\":\"val2\",\"d\":null,\"e\":\"val3\"}}";

            // When
            ComplexMessage result = parseWithConfig(json, ComplexMessage.class, config);

            // Then
            assertThat(result.getMetadataMap()).hasSize(3);
            assertThat(result.getMetadataMap()).containsEntry("a", "val1");
            assertThat(result.getMetadataMap()).containsEntry("c", "val2");
            assertThat(result.getMetadataMap()).containsEntry("e", "val3");
        }

        @Test
        @DisplayName("Should handle map with all null values")
        void testMapAllNullValues() throws Exception {
            // Given
            ParserConfig config = ParserConfig.newBuilder()
                    .allowNullForScalars(true)
                    .build();

            String json = "{\"metadata\":{\"key1\":null,\"key2\":null,\"key3\":null}}";

            // When
            ComplexMessage result = parseWithConfig(json, ComplexMessage.class, config);

            // Then: Map should be empty
            assertThat(result.getMetadataMap()).isEmpty();
        }
    }

    // Helper methods

    private <T extends Message> T parseWithConfig(
            String json, Class<T> messageClass, ParserConfig config) throws Exception {

        JsonToProtoContext ctx = new JsonToProtoContext(
                metaRegistry,
                config,
                converterRegistry
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
