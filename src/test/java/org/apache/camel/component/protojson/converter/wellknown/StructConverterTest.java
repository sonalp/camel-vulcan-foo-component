package org.apache.camel.component.protojson.converter.wellknown;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.google.protobuf.Descriptors;
import com.google.protobuf.NullValue;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import org.apache.camel.component.protojson.test.proto.EventMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for StructConverter.
 */
class StructConverterTest {

    private JsonFactory jsonFactory;
    private Descriptors.FieldDescriptor structField;
    private Descriptors.FieldDescriptor timestampField;

    @BeforeEach
    void setUp() {
        jsonFactory = new JsonFactory();
        Descriptors.Descriptor eventDesc = EventMessage.getDescriptor();
        structField = eventDesc.findFieldByName("metadata");
        timestampField = eventDesc.findFieldByName("created_at");
    }

    @Test
    void testStructConverterSupports() {
        // Given
        StructConverter converter = new StructConverter();

        // When/Then
        assertThat(converter.supports(structField)).isTrue();
        assertThat(converter.supports(timestampField)).isFalse();
    }

    @Test
    void testStructRead() throws Exception {
        // Given
        StructConverter converter = new StructConverter();
        String json = "{\"key1\": \"value1\", \"key2\": 42, \"key3\": true}";

        EventMessage.Builder builder = EventMessage.newBuilder();

        // When
        try (JsonParser parser = jsonFactory.createParser(json.getBytes())) {
            parser.nextToken();
            converter.read(parser, builder, structField);
        }

        // Then
        EventMessage event = builder.build();
        Struct struct = event.getMetadata();
        assertThat(struct.getFieldsMap()).containsKey("key1");
        assertThat(struct.getFieldsMap().get("key1").getStringValue()).isEqualTo("value1");
        assertThat(struct.getFieldsMap().get("key2").getNumberValue()).isEqualTo(42.0);
        assertThat(struct.getFieldsMap().get("key3").getBoolValue()).isTrue();
    }

    @Test
    void testStructReadNested() throws Exception {
        // Given
        StructConverter converter = new StructConverter();
        String json = "{\"nested\": {\"inner\": \"value\"}}";

        EventMessage.Builder builder = EventMessage.newBuilder();

        // When
        try (JsonParser parser = jsonFactory.createParser(json.getBytes())) {
            parser.nextToken();
            converter.read(parser, builder, structField);
        }

        // Then
        EventMessage event = builder.build();
        Struct struct = event.getMetadata();
        assertThat(struct.getFieldsMap()).containsKey("nested");
        assertThat(struct.getFieldsMap().get("nested").getStructValue().getFieldsMap())
                .containsKey("inner");
    }

    @Test
    void testStructWrite() throws Exception {
        // Given
        StructConverter converter = new StructConverter();
        Struct struct = Struct.newBuilder()
                .putFields("key1", Value.newBuilder().setStringValue("value1").build())
                .putFields("key2", Value.newBuilder().setNumberValue(42).build())
                .build();

        EventMessage message = EventMessage.newBuilder()
                .setMetadata(struct)
                .build();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // When
        try (JsonGenerator gen = jsonFactory.createGenerator(baos)) {
            converter.write(gen, message, structField);
            gen.flush();
        }

        // Then
        String json = baos.toString(StandardCharsets.UTF_8);
        assertThat(json).contains("\"key1\"");
        assertThat(json).contains("\"value1\"");
        assertThat(json).contains("\"key2\"");
        assertThat(json).contains("42");
    }

    @Test
    void testStructReadEmpty() throws Exception {
        // Given
        StructConverter converter = new StructConverter();
        String json = "{}";

        EventMessage.Builder builder = EventMessage.newBuilder();

        // When
        try (JsonParser parser = jsonFactory.createParser(json.getBytes())) {
            parser.nextToken();
            converter.read(parser, builder, structField);
        }

        // Then
        EventMessage event = builder.build();
        Struct struct = event.getMetadata();
        assertThat(struct.getFieldsMap()).isEmpty();
    }

    @Test
    void testStructReadNull() throws Exception {
        // Given
        StructConverter converter = new StructConverter();
        String json = "null";

        EventMessage.Builder builder = EventMessage.newBuilder();

        // When
        try (JsonParser parser = jsonFactory.createParser(json.getBytes())) {
            parser.nextToken();
            converter.read(parser, builder, structField);
        }

        // Then
        EventMessage event = builder.build();
        assertThat(event.hasMetadata()).isFalse();
    }

    @Test
    void testStructWriteEmpty() throws Exception {
        // Given
        StructConverter converter = new StructConverter();
        Struct struct = Struct.newBuilder().build();

        EventMessage message = EventMessage.newBuilder()
                .setMetadata(struct)
                .build();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // When
        try (JsonGenerator gen = jsonFactory.createGenerator(baos)) {
            converter.write(gen, message, structField);
            gen.flush();
        }

        // Then
        String json = baos.toString(StandardCharsets.UTF_8);
        assertThat(json).isEqualTo("{}");
    }

    @Test
    void testStructWithNullValue() throws Exception {
        // Given
        StructConverter converter = new StructConverter();
        String json = "{\"key1\": null}";

        EventMessage.Builder builder = EventMessage.newBuilder();

        // When
        try (JsonParser parser = jsonFactory.createParser(json.getBytes())) {
            parser.nextToken();
            converter.read(parser, builder, structField);
        }

        // Then
        EventMessage event = builder.build();
        Struct struct = event.getMetadata();
        assertThat(struct.getFieldsMap()).containsKey("key1");
        assertThat(struct.getFieldsMap().get("key1").getNullValue())
                .isEqualTo(NullValue.NULL_VALUE);
    }

    @Test
    void testStructWithArray() throws Exception {
        // Given
        StructConverter converter = new StructConverter();
        String json = "{\"array\": [1, 2, 3]}";

        EventMessage.Builder builder = EventMessage.newBuilder();

        // When
        try (JsonParser parser = jsonFactory.createParser(json.getBytes())) {
            parser.nextToken();
            converter.read(parser, builder, structField);
        }

        // Then
        EventMessage event = builder.build();
        Struct struct = event.getMetadata();
        assertThat(struct.getFieldsMap()).containsKey("array");
        assertThat(struct.getFieldsMap().get("array").getListValue().getValuesCount())
                .isEqualTo(3);
    }
}
