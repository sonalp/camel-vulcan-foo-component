package org.apache.camel.component.protojson.converter.wellknown;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.google.protobuf.Descriptors;
import com.google.protobuf.ListValue;
import com.google.protobuf.NullValue;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import org.apache.camel.component.protojson.test.proto.EventMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
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

    @Test
    void testStructReadWithFalseValue() throws Exception {
        // Given: Test VALUE_FALSE branch (line 96)
        StructConverter converter = new StructConverter();
        String json = "{\"isActive\": false, \"isDeleted\": false}";

        EventMessage.Builder builder = EventMessage.newBuilder();

        // When
        try (JsonParser parser = jsonFactory.createParser(json.getBytes())) {
            parser.nextToken();
            converter.read(parser, builder, structField);
        }

        // Then
        EventMessage event = builder.build();
        Struct struct = event.getMetadata();
        assertThat(struct.getFieldsMap().get("isActive").getBoolValue()).isFalse();
        assertThat(struct.getFieldsMap().get("isDeleted").getBoolValue()).isFalse();
    }

    @Test
    void testStructReadMixedTypes() throws Exception {
        // Given: Test all value types in one struct
        StructConverter converter = new StructConverter();
        String json = "{\"str\":\"text\",\"num\":123.45,\"bool\":true,\"nil\":null,\"arr\":[1,2],\"obj\":{\"x\":1}}";

        EventMessage.Builder builder = EventMessage.newBuilder();

        // When
        try (JsonParser parser = jsonFactory.createParser(json.getBytes())) {
            parser.nextToken();
            converter.read(parser, builder, structField);
        }

        // Then
        EventMessage event = builder.build();
        Struct struct = event.getMetadata();
        assertThat(struct.getFieldsMap().get("str").getStringValue()).isEqualTo("text");
        assertThat(struct.getFieldsMap().get("num").getNumberValue()).isEqualTo(123.45);
        assertThat(struct.getFieldsMap().get("bool").getBoolValue()).isTrue();
        assertThat(struct.getFieldsMap().get("nil").getNullValue()).isEqualTo(NullValue.NULL_VALUE);
        assertThat(struct.getFieldsMap().get("arr").getListValue().getValuesCount()).isEqualTo(2);
        assertThat(struct.getFieldsMap().get("obj").getStructValue().getFieldsMap()).containsKey("x");
    }

    @Test
    void testStructReadInvalidToken() throws Exception {
        // Given: Non-object for Struct (covers line 70-72)
        StructConverter converter = new StructConverter();
        String json = "\"string instead of object\"";

        EventMessage.Builder builder = EventMessage.newBuilder();

        // When/Then: Should throw IOException
        try (JsonParser parser = jsonFactory.createParser(json.getBytes())) {
            parser.nextToken();
            assertThat(org.assertj.core.api.Assertions.catchThrowable(() ->
                    converter.read(parser, builder, structField)))
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("Struct must be a JSON object");
        }
    }

    @Test
    void testStructWriteWithBoolValue() throws Exception {
        // Given: Test bool_value branch in write (line 194)
        StructConverter converter = new StructConverter();
        Struct struct = Struct.newBuilder()
                .putFields("active", Value.newBuilder().setBoolValue(true).build())
                .putFields("deleted", Value.newBuilder().setBoolValue(false).build())
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
        assertThat(json).contains("\"active\":true");
        assertThat(json).contains("\"deleted\":false");
    }

    @Test
    void testStructWriteWithListValue() throws Exception {
        // Given: Test list_value branch in write (line 197)
        StructConverter converter = new StructConverter();
        ListValue list = ListValue.newBuilder()
                .addValues(Value.newBuilder().setNumberValue(1).build())
                .addValues(Value.newBuilder().setNumberValue(2).build())
                .addValues(Value.newBuilder().setNumberValue(3).build())
                .build();

        Struct struct = Struct.newBuilder()
                .putFields("numbers", Value.newBuilder().setListValue(list).build())
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
        assertThat(json).contains("\"numbers\"");
        assertThat(json).contains("[1");
        assertThat(json).contains("2");
        assertThat(json).contains("0]");
    }

    @Test
    void testStructWriteWithNestedStruct() throws Exception {
        // Given: Test struct_value branch in write (line 198)
        StructConverter converter = new StructConverter();
        Struct innerStruct = Struct.newBuilder()
                .putFields("inner", Value.newBuilder().setStringValue("value").build())
                .build();

        Struct struct = Struct.newBuilder()
                .putFields("nested", Value.newBuilder().setStructValue(innerStruct).build())
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
        assertThat(json).contains("\"nested\"");
        assertThat(json).contains("\"inner\"");
        assertThat(json).contains("\"value\"");
    }

    @Test
    void testStructWriteWithNullValue() throws Exception {
        // Given: Test null_value branch in write (line 193)
        StructConverter converter = new StructConverter();
        Struct struct = Struct.newBuilder()
                .putFields("nullField", Value.newBuilder().setNullValue(NullValue.NULL_VALUE).build())
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
        assertThat(json).contains("\"nullField\":null");
    }

    @Test
    void testStructWriteComplexNested() throws Exception {
        // Given: Complex nested structure to cover multiple write branches
        StructConverter converter = new StructConverter();

        ListValue innerList = ListValue.newBuilder()
                .addValues(Value.newBuilder().setStringValue("a").build())
                .addValues(Value.newBuilder().setStringValue("b").build())
                .build();

        Struct innerStruct = Struct.newBuilder()
                .putFields("list", Value.newBuilder().setListValue(innerList).build())
                .putFields("flag", Value.newBuilder().setBoolValue(true).build())
                .build();

        Struct struct = Struct.newBuilder()
                .putFields("nested", Value.newBuilder().setStructValue(innerStruct).build())
                .putFields("count", Value.newBuilder().setNumberValue(42).build())
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
        assertThat(json).contains("\"nested\"");
        assertThat(json).contains("\"list\"");
        assertThat(json).contains("\"flag\":true");
        assertThat(json).contains("\"count\":42");
    }
}
