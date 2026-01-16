package org.apache.camel.component.protojson.converter.wellknown;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.google.protobuf.*;
import org.apache.camel.component.protojson.test.proto.EventMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Unit tests for WrapperConverters covering all 9 wrapper types.
 */
class WrapperConvertersTest {

    private JsonFactory jsonFactory;
    private Descriptors.FieldDescriptor stringValueField;
    private Descriptors.FieldDescriptor int32ValueField;
    private Descriptors.FieldDescriptor int64ValueField;
    private Descriptors.FieldDescriptor uint32ValueField;
    private Descriptors.FieldDescriptor uint64ValueField;
    private Descriptors.FieldDescriptor floatValueField;
    private Descriptors.FieldDescriptor doubleValueField;
    private Descriptors.FieldDescriptor boolValueField;
    private Descriptors.FieldDescriptor bytesValueField;
    private Descriptors.FieldDescriptor timestampField;

    @BeforeEach
    void setUp() {
        jsonFactory = new JsonFactory();
        Descriptors.Descriptor eventDesc = EventMessage.getDescriptor();

        stringValueField = eventDesc.findFieldByName("optional_note");
        int32ValueField = eventDesc.findFieldByName("optional_count");
        int64ValueField = eventDesc.findFieldByName("optional_long");
        uint32ValueField = eventDesc.findFieldByName("optional_uint");
        uint64ValueField = eventDesc.findFieldByName("optional_ulong");
        floatValueField = eventDesc.findFieldByName("optional_float");
        doubleValueField = eventDesc.findFieldByName("optional_double");
        boolValueField = eventDesc.findFieldByName("optional_bool");
        bytesValueField = eventDesc.findFieldByName("optional_bytes");
        timestampField = eventDesc.findFieldByName("created_at");
    }
    // ==================== WrapperConverters Tests ====================

    @Test
    void testWrapperConvertersSupportsStringValue() {
        // Given
        WrapperConverters converter = new WrapperConverters();

        // When/Then
        assertThat(converter.supports(stringValueField)).isTrue();
        assertThat(converter.supports(timestampField)).isFalse();
    }

    @Test
    void testWrapperConvertersSupportsInt32Value() {
        // Given
        WrapperConverters converter = new WrapperConverters();

        // When/Then
        assertThat(converter.supports(int32ValueField)).isTrue();
        assertThat(converter.supports(timestampField)).isFalse();
    }

    @Test
    void testStringValueRead() throws Exception {
        // Given
        WrapperConverters converter = new WrapperConverters();
        String json = "\"test value\"";

        EventMessage.Builder builder = EventMessage.newBuilder();

        // When
        try (JsonParser parser = jsonFactory.createParser(json.getBytes())) {
            parser.nextToken();
            converter.read(parser, builder, stringValueField);
        }

        // Then
        EventMessage event = builder.build();
        assertThat(event.hasOptionalNote()).isTrue();
        assertThat(event.getOptionalNote().getValue()).isEqualTo("test value");
    }

    @Test
    void testStringValueReadNull() throws Exception {
        // Given
        WrapperConverters converter = new WrapperConverters();
        String json = "null";

        EventMessage.Builder builder = EventMessage.newBuilder();

        // When
        try (JsonParser parser = jsonFactory.createParser(json.getBytes())) {
            parser.nextToken();
            converter.read(parser, builder, stringValueField);
        }

        // Then
        EventMessage event = builder.build();
        assertThat(event.hasOptionalNote()).isFalse();
    }

    @Test
    void testStringValueWrite() throws Exception {
        // Given
        WrapperConverters converter = new WrapperConverters();
        StringValue value = StringValue.of("test value");

        EventMessage message = EventMessage.newBuilder()
                .setOptionalNote(value)
                .build();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // When
        try (JsonGenerator gen = jsonFactory.createGenerator(baos)) {
            converter.write(gen, message, stringValueField);
            gen.flush();
        }

        // Then
        String json = baos.toString(StandardCharsets.UTF_8);
        assertThat(json).isEqualTo("\"test value\"");
    }

    @Test
    void testInt32ValueRead() throws Exception {
        // Given
        WrapperConverters converter = new WrapperConverters();
        String json = "42";

        EventMessage.Builder builder = EventMessage.newBuilder();

        // When
        try (JsonParser parser = jsonFactory.createParser(json.getBytes())) {
            parser.nextToken();
            converter.read(parser, builder, int32ValueField);
        }

        // Then
        EventMessage event = builder.build();
        assertThat(event.hasOptionalCount()).isTrue();
        assertThat(event.getOptionalCount().getValue()).isEqualTo(42);
    }

    @Test
    void testInt32ValueWrite() throws Exception {
        // Given
        WrapperConverters converter = new WrapperConverters();
        Int32Value value = Int32Value.of(42);

        EventMessage message = EventMessage.newBuilder()
                .setOptionalCount(value)
                .build();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // When
        try (JsonGenerator gen = jsonFactory.createGenerator(baos)) {
            converter.write(gen, message, int32ValueField);
            gen.flush();
        }

        // Then
        String json = baos.toString(StandardCharsets.UTF_8);
        assertThat(json).isEqualTo("42");
    }

    @Test
    void testInt64ValueRead() throws Exception {
        // Given
        WrapperConverters converter = new WrapperConverters();
        String json = "9223372036854775807"; // Long.MAX_VALUE

        EventMessage.Builder builder = EventMessage.newBuilder();

        // When
        try (JsonParser parser = jsonFactory.createParser(json.getBytes())) {
            parser.nextToken();
            converter.read(parser, builder, int64ValueField);
        }

        // Then
        EventMessage event = builder.build();
        assertThat(event.hasOptionalLong()).isTrue();
        assertThat(event.getOptionalLong().getValue()).isEqualTo(9223372036854775807L);
    }

    @Test
    void testInt64ValueWrite() throws Exception {
        // Given
        WrapperConverters converter = new WrapperConverters();
        Int64Value value = Int64Value.of(9223372036854775807L);

        EventMessage message = EventMessage.newBuilder()
                .setOptionalLong(value)
                .build();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // When
        try (JsonGenerator gen = jsonFactory.createGenerator(baos)) {
            converter.write(gen, message, int64ValueField);
            gen.flush();
        }

        // Then
        String json = baos.toString(StandardCharsets.UTF_8);
        assertThat(json).isEqualTo("9223372036854775807");
    }

    @Test
    void testUInt32ValueRead() throws Exception {
        // Given
        WrapperConverters converter = new WrapperConverters();
        String json = "100"; // Use a value within signed int range

        EventMessage.Builder builder = EventMessage.newBuilder();

        // When
        try (JsonParser parser = jsonFactory.createParser(json.getBytes())) {
            parser.nextToken();
            converter.read(parser, builder, uint32ValueField);
        }

        // Then
        EventMessage event = builder.build();
        assertThat(event.hasOptionalUint()).isTrue();
        assertThat(event.getOptionalUint().getValue()).isEqualTo(100);
    }

    @Test
    void testUInt32ValueWrite() throws Exception {
        // Given
        WrapperConverters converter = new WrapperConverters();
        UInt32Value value = UInt32Value.of(100);

        EventMessage message = EventMessage.newBuilder()
                .setOptionalUint(value)
                .build();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // When
        try (JsonGenerator gen = jsonFactory.createGenerator(baos)) {
            converter.write(gen, message, uint32ValueField);
            gen.flush();
        }

        // Then
        String json = baos.toString(StandardCharsets.UTF_8);
        assertThat(json).isEqualTo("100");
    }

    @Test
    void testUInt64ValueRead() throws Exception {
        // Given
        WrapperConverters converter = new WrapperConverters();
        String json = "1000"; // Use a value within signed long range

        EventMessage.Builder builder = EventMessage.newBuilder();

        // When
        try (JsonParser parser = jsonFactory.createParser(json.getBytes())) {
            parser.nextToken();
            converter.read(parser, builder, uint64ValueField);
        }

        // Then
        EventMessage event = builder.build();
        assertThat(event.hasOptionalUlong()).isTrue();
        assertThat(event.getOptionalUlong().getValue()).isEqualTo(1000L);
    }

    @Test
    void testUInt64ValueWrite() throws Exception {
        // Given
        WrapperConverters converter = new WrapperConverters();
        UInt64Value value = UInt64Value.of(1000L);

        EventMessage message = EventMessage.newBuilder()
                .setOptionalUlong(value)
                .build();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // When
        try (JsonGenerator gen = jsonFactory.createGenerator(baos)) {
            converter.write(gen, message, uint64ValueField);
            gen.flush();
        }

        // Then
        String json = baos.toString(StandardCharsets.UTF_8);
        assertThat(json).isEqualTo("1000");
    }

    @Test
    void testFloatValueRead() throws Exception {
        // Given
        WrapperConverters converter = new WrapperConverters();
        String json = "3.14159";

        EventMessage.Builder builder = EventMessage.newBuilder();

        // When
        try (JsonParser parser = jsonFactory.createParser(json.getBytes())) {
            parser.nextToken();
            converter.read(parser, builder, floatValueField);
        }

        // Then
        EventMessage event = builder.build();
        assertThat(event.hasOptionalFloat()).isTrue();
        assertThat(event.getOptionalFloat().getValue()).isCloseTo(3.14159f, within(0.00001f));
    }

    @Test
    void testFloatValueWrite() throws Exception {
        // Given
        WrapperConverters converter = new WrapperConverters();
        FloatValue value = FloatValue.of(3.14159f);

        EventMessage message = EventMessage.newBuilder()
                .setOptionalFloat(value)
                .build();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // When
        try (JsonGenerator gen = jsonFactory.createGenerator(baos)) {
            converter.write(gen, message, floatValueField);
            gen.flush();
        }

        // Then
        String json = baos.toString(StandardCharsets.UTF_8);
        assertThat(json).contains("3.14159");
    }

    @Test
    void testDoubleValueRead() throws Exception {
        // Given
        WrapperConverters converter = new WrapperConverters();
        String json = "2.718281828459045";

        EventMessage.Builder builder = EventMessage.newBuilder();

        // When
        try (JsonParser parser = jsonFactory.createParser(json.getBytes())) {
            parser.nextToken();
            converter.read(parser, builder, doubleValueField);
        }

        // Then
        EventMessage event = builder.build();
        assertThat(event.hasOptionalDouble()).isTrue();
        assertThat(event.getOptionalDouble().getValue()).isCloseTo(2.718281828459045, within(0.000000000000001));
    }

    @Test
    void testDoubleValueWrite() throws Exception {
        // Given
        WrapperConverters converter = new WrapperConverters();
        DoubleValue value = DoubleValue.of(2.718281828459045);

        EventMessage message = EventMessage.newBuilder()
                .setOptionalDouble(value)
                .build();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // When
        try (JsonGenerator gen = jsonFactory.createGenerator(baos)) {
            converter.write(gen, message, doubleValueField);
            gen.flush();
        }

        // Then
        String json = baos.toString(StandardCharsets.UTF_8);
        assertThat(json).contains("2.718281828459045");
    }

    // ==================== BoolValue Tests ====================

    @Test
    void testBoolValueReadTrue() throws Exception {
        // Given: Test VALUE_TRUE branch (line 93-94)
        WrapperConverters converter = new WrapperConverters();
        String json = "true";

        EventMessage.Builder builder = EventMessage.newBuilder();

        // When
        try (JsonParser parser = jsonFactory.createParser(json.getBytes())) {
            parser.nextToken();
            converter.read(parser, builder, boolValueField);
        }

        // Then
        EventMessage event = builder.build();
        assertThat(event.hasOptionalBool()).isTrue();
        assertThat(event.getOptionalBool().getValue()).isTrue();
    }

    @Test
    void testBoolValueReadFalse() throws Exception {
        // Given: Test VALUE_FALSE branch (line 95-96)
        WrapperConverters converter = new WrapperConverters();
        String json = "false";

        EventMessage.Builder builder = EventMessage.newBuilder();

        // When
        try (JsonParser parser = jsonFactory.createParser(json.getBytes())) {
            parser.nextToken();
            converter.read(parser, builder, boolValueField);
        }

        // Then
        EventMessage event = builder.build();
        assertThat(event.hasOptionalBool()).isTrue();
        assertThat(event.getOptionalBool().getValue()).isFalse();
    }

    @Test
    void testBoolValueReadFromString() throws Exception {
        // Given: Test isScalarValue branch (line 97-98)
        WrapperConverters converter = new WrapperConverters();
        String json = "\"true\"";

        EventMessage.Builder builder = EventMessage.newBuilder();

        // When
        try (JsonParser parser = jsonFactory.createParser(json.getBytes())) {
            parser.nextToken();
            converter.read(parser, builder, boolValueField);
        }

        // Then
        EventMessage event = builder.build();
        assertThat(event.hasOptionalBool()).isTrue();
        assertThat(event.getOptionalBool().getValue()).isTrue();
    }

    @Test
    void testBoolValueWrite() throws Exception {
        // Given: Test bool write branch (line 172)
        WrapperConverters converter = new WrapperConverters();
        BoolValue value = BoolValue.of(true);

        EventMessage message = EventMessage.newBuilder()
                .setOptionalBool(value)
                .build();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // When
        try (JsonGenerator gen = jsonFactory.createGenerator(baos)) {
            converter.write(gen, message, boolValueField);
            gen.flush();
        }

        // Then
        String json = baos.toString(StandardCharsets.UTF_8);
        assertThat(json).isEqualTo("true");
    }

    // ==================== BytesValue Tests ====================

    @Test
    void testBytesValueRead() throws Exception {
        // Given: Test BytesValue branch (line 86-89)
        WrapperConverters converter = new WrapperConverters();
        String base64 = java.util.Base64.getEncoder().encodeToString("Hello World".getBytes());
        String json = "\"" + base64 + "\"";

        EventMessage.Builder builder = EventMessage.newBuilder();

        // When
        try (JsonParser parser = jsonFactory.createParser(json.getBytes())) {
            parser.nextToken();
            converter.read(parser, builder, bytesValueField);
        }

        // Then
        EventMessage event = builder.build();
        assertThat(event.hasOptionalBytes()).isTrue();
        assertThat(event.getOptionalBytes().getValue().toStringUtf8()).isEqualTo("Hello World");
    }

    @Test
    void testBytesValueWrite() throws Exception {
        // Given: Test BytesValue write branch (line 168-171)
        WrapperConverters converter = new WrapperConverters();
        BytesValue value = BytesValue.of(ByteString.copyFromUtf8("Hello World"));

        EventMessage message = EventMessage.newBuilder()
                .setOptionalBytes(value)
                .build();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // When
        try (JsonGenerator gen = jsonFactory.createGenerator(baos)) {
            converter.write(gen, message, bytesValueField);
            gen.flush();
        }

        // Then
        String json = baos.toString(StandardCharsets.UTF_8);
        String expectedBase64 = java.util.Base64.getEncoder().encodeToString("Hello World".getBytes());
        assertThat(json).isEqualTo("\"" + expectedBase64 + "\"");
    }

    // ==================== String-to-Numeric Conversion Tests ====================

    @Test
    void testInt32ValueReadFromString() throws Exception {
        // Given: Test isScalarValue branch for Int32Value (line 105-106)
        WrapperConverters converter = new WrapperConverters();
        String json = "\"123\"";

        EventMessage.Builder builder = EventMessage.newBuilder();

        // When
        try (JsonParser parser = jsonFactory.createParser(json.getBytes())) {
            parser.nextToken();
            converter.read(parser, builder, int32ValueField);
        }

        // Then
        EventMessage event = builder.build();
        assertThat(event.hasOptionalCount()).isTrue();
        assertThat(event.getOptionalCount().getValue()).isEqualTo(123);
    }

    @Test
    void testInt64ValueReadFromString() throws Exception {
        // Given: Test isScalarValue branch for Int64Value (line 113-114)
        WrapperConverters converter = new WrapperConverters();
        String json = "\"9223372036854775807\"";

        EventMessage.Builder builder = EventMessage.newBuilder();

        // When
        try (JsonParser parser = jsonFactory.createParser(json.getBytes())) {
            parser.nextToken();
            converter.read(parser, builder, int64ValueField);
        }

        // Then
        EventMessage event = builder.build();
        assertThat(event.hasOptionalLong()).isTrue();
        assertThat(event.getOptionalLong().getValue()).isEqualTo(9223372036854775807L);
    }

    @Test
    void testUInt32ValueReadFromString() throws Exception {
        // Given: Test isScalarValue branch for UInt32Value (line 121-122)
        WrapperConverters converter = new WrapperConverters();
        String json = "\"4294967295\""; // Max unsigned int32

        EventMessage.Builder builder = EventMessage.newBuilder();

        // When
        try (JsonParser parser = jsonFactory.createParser(json.getBytes())) {
            parser.nextToken();
            converter.read(parser, builder, uint32ValueField);
        }

        // Then
        EventMessage event = builder.build();
        assertThat(event.hasOptionalUint()).isTrue();
        // Note: Proto stores as signed int, so -1 represents max unsigned
        assertThat(event.getOptionalUint().getValue()).isEqualTo(-1);
    }

    @Test
    void testUInt64ValueReadFromString() throws Exception {
        // Given: Test isScalarValue branch for UInt64Value (line 129-130)
        WrapperConverters converter = new WrapperConverters();
        String json = "\"1000000\"";

        EventMessage.Builder builder = EventMessage.newBuilder();

        // When
        try (JsonParser parser = jsonFactory.createParser(json.getBytes())) {
            parser.nextToken();
            converter.read(parser, builder, uint64ValueField);
        }

        // Then
        EventMessage event = builder.build();
        assertThat(event.hasOptionalUlong()).isTrue();
        assertThat(event.getOptionalUlong().getValue()).isEqualTo(1000000L);
    }

    @Test
    void testFloatValueReadFromString() throws Exception {
        // Given: Test isScalarValue branch for FloatValue (line 137-138)
        WrapperConverters converter = new WrapperConverters();
        String json = "\"3.14\"";

        EventMessage.Builder builder = EventMessage.newBuilder();

        // When
        try (JsonParser parser = jsonFactory.createParser(json.getBytes())) {
            parser.nextToken();
            converter.read(parser, builder, floatValueField);
        }

        // Then
        EventMessage event = builder.build();
        assertThat(event.hasOptionalFloat()).isTrue();
        assertThat(event.getOptionalFloat().getValue()).isCloseTo(3.14f, within(0.01f));
    }

    @Test
    void testDoubleValueReadFromString() throws Exception {
        // Given: Test isScalarValue branch for DoubleValue (line 145-146)
        WrapperConverters converter = new WrapperConverters();
        String json = "\"2.718\"";

        EventMessage.Builder builder = EventMessage.newBuilder();

        // When
        try (JsonParser parser = jsonFactory.createParser(json.getBytes())) {
            parser.nextToken();
            converter.read(parser, builder, doubleValueField);
        }

        // Then
        EventMessage event = builder.build();
        assertThat(event.hasOptionalDouble()).isTrue();
        assertThat(event.getOptionalDouble().getValue()).isCloseTo(2.718, within(0.001));
    }

    // ==================== Error Case Tests ====================

    @Test
    void testStringValueReadInvalidToken() throws Exception {
        // Given: Object for StringValue (line 80-83)
        WrapperConverters converter = new WrapperConverters();
        String json = "{\"nested\":\"object\"}";

        EventMessage.Builder builder = EventMessage.newBuilder();

        // When/Then: Should throw IOException
        try (JsonParser parser = jsonFactory.createParser(json.getBytes())) {
            parser.nextToken();
            assertThat(org.assertj.core.api.Assertions.catchThrowable(() ->
                    converter.read(parser, builder, stringValueField)))
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("Expected string for StringValue");
        }
    }

    @Test
    void testBytesValueReadInvalidToken() throws Exception {
        // Given: Non-string for BytesValue (line 86-90)
        WrapperConverters converter = new WrapperConverters();
        String json = "123";

        EventMessage.Builder builder = EventMessage.newBuilder();

        // When/Then: Should throw IOException
        try (JsonParser parser = jsonFactory.createParser(json.getBytes())) {
            parser.nextToken();
            assertThat(org.assertj.core.api.Assertions.catchThrowable(() ->
                    converter.read(parser, builder, bytesValueField)))
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("Expected base64 string for BytesValue");
        }
    }

    @Test
    void testBoolValueReadInvalidToken() throws Exception {
        // Given: Object for BoolValue (line 100)
        WrapperConverters converter = new WrapperConverters();
        String json = "{\"nested\":\"object\"}";

        EventMessage.Builder builder = EventMessage.newBuilder();

        // When/Then: Should throw IOException
        try (JsonParser parser = jsonFactory.createParser(json.getBytes())) {
            parser.nextToken();
            assertThat(org.assertj.core.api.Assertions.catchThrowable(() ->
                    converter.read(parser, builder, boolValueField)))
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("Expected boolean for BoolValue");
        }
    }

    @Test
    void testInt32ValueReadInvalidToken() throws Exception {
        // Given: Object for Int32Value (line 108)
        WrapperConverters converter = new WrapperConverters();
        String json = "{}";

        EventMessage.Builder builder = EventMessage.newBuilder();

        // When/Then: Should throw IOException
        try (JsonParser parser = jsonFactory.createParser(json.getBytes())) {
            parser.nextToken();
            assertThat(org.assertj.core.api.Assertions.catchThrowable(() ->
                    converter.read(parser, builder, int32ValueField)))
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("Expected integer for Int32Value");
        }
    }

    @Test
    void testFloatValueReadInvalidToken() throws Exception {
        // Given: Array for FloatValue (line 140)
        WrapperConverters converter = new WrapperConverters();
        String json = "[1,2,3]";

        EventMessage.Builder builder = EventMessage.newBuilder();

        // When/Then: Should throw IOException
        try (JsonParser parser = jsonFactory.createParser(json.getBytes())) {
            parser.nextToken();
            assertThat(org.assertj.core.api.Assertions.catchThrowable(() ->
                    converter.read(parser, builder, floatValueField)))
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("Expected float for FloatValue");
        }
    }
}
