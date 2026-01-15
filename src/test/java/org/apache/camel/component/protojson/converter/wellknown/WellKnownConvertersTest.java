package org.apache.camel.component.protojson.converter.wellknown;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.google.protobuf.*;
import org.apache.camel.component.protojson.test.proto.EventMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

/**
 * Comprehensive tests for well-known type converters.
 * Tests Timestamp, Duration, Struct, Any, and Wrapper converters.
 */
class WellKnownConvertersTest {

    private JsonFactory jsonFactory;
    private Descriptors.FieldDescriptor timestampField;
    private Descriptors.FieldDescriptor durationField;
    private Descriptors.FieldDescriptor structField;
    private Descriptors.FieldDescriptor anyField;
    private Descriptors.FieldDescriptor stringValueField;
    private Descriptors.FieldDescriptor int32ValueField;
    private Descriptors.FieldDescriptor int64ValueField;
    private Descriptors.FieldDescriptor uint32ValueField;
    private Descriptors.FieldDescriptor uint64ValueField;
    private Descriptors.FieldDescriptor floatValueField;
    private Descriptors.FieldDescriptor doubleValueField;
    private Descriptors.FieldDescriptor boolValueField;
    private Descriptors.FieldDescriptor bytesValueField;

    @BeforeEach
    void setUp() {
        jsonFactory = new JsonFactory();
        Descriptors.Descriptor eventDesc = EventMessage.getDescriptor();

        timestampField = eventDesc.findFieldByName("created_at");
        durationField = eventDesc.findFieldByName("duration");
        structField = eventDesc.findFieldByName("metadata");
        anyField = eventDesc.findFieldByName("payload");
        stringValueField = eventDesc.findFieldByName("optional_note");
        int32ValueField = eventDesc.findFieldByName("optional_count");
        int64ValueField = eventDesc.findFieldByName("optional_long");
        uint32ValueField = eventDesc.findFieldByName("optional_uint");
        uint64ValueField = eventDesc.findFieldByName("optional_ulong");
        floatValueField = eventDesc.findFieldByName("optional_float");
        doubleValueField = eventDesc.findFieldByName("optional_double");
        boolValueField = eventDesc.findFieldByName("optional_bool");
        bytesValueField = eventDesc.findFieldByName("optional_bytes");
    }

    // ==================== TimestampConverter Tests ====================

    @Test
    void testTimestampConverterSupports() {
        // Given
        TimestampConverter converter = new TimestampConverter();

        // When/Then
        assertThat(converter.supports(timestampField)).isTrue();
        assertThat(converter.supports(durationField)).isFalse();
    }

    @Test
    void testTimestampRead() throws Exception {
        // Given
        TimestampConverter converter = new TimestampConverter();
        String json = "\"2024-01-15T10:30:00Z\"";

        EventMessage.Builder builder = EventMessage.newBuilder();

        // When
        try (JsonParser parser = jsonFactory.createParser(json.getBytes())) {
            parser.nextToken(); // Move to value
            converter.read(parser, builder, timestampField);
        }

        // Then
        EventMessage event = builder.build();
        assertThat(event.hasCreatedAt()).isTrue();
        Timestamp timestamp = event.getCreatedAt();
        assertThat(timestamp.getSeconds()).isGreaterThan(0);
    }

    @Test
    void testTimestampReadRFC3339() throws Exception {
        // Given
        TimestampConverter converter = new TimestampConverter();
        String json = "\"2024-01-15T10:30:00.123456Z\"";

        EventMessage.Builder builder = EventMessage.newBuilder();

        // When
        try (JsonParser parser = jsonFactory.createParser(json.getBytes())) {
            parser.nextToken();
            converter.read(parser, builder, timestampField);
        }

        // Then
        EventMessage event = builder.build();
        Timestamp timestamp = event.getCreatedAt();
        assertThat(timestamp.getNanos()).isEqualTo(123456000);
    }

    @Test
    void testTimestampReadInvalid() throws Exception {
        // Given
        TimestampConverter converter = new TimestampConverter();
        String json = "\"not-a-timestamp\"";

        EventMessage.Builder builder = EventMessage.newBuilder();

        // When/Then
        try (JsonParser parser = jsonFactory.createParser(json.getBytes())) {
            parser.nextToken();
            assertThatThrownBy(() -> converter.read(parser, builder, timestampField))
                    .isInstanceOf(Exception.class);
        }
    }

    @Test
    void testTimestampWrite() throws Exception {
        // Given
        TimestampConverter converter = new TimestampConverter();
        Timestamp timestamp = Timestamp.newBuilder()
                .setSeconds(1705315800)
                .setNanos(123456000)
                .build();

        EventMessage message = EventMessage.newBuilder()
                .setCreatedAt(timestamp)
                .build();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // When
        try (JsonGenerator gen = jsonFactory.createGenerator(baos)) {
            converter.write(gen, message, timestampField);
            gen.flush();
        }

        // Then
        String json = baos.toString(StandardCharsets.UTF_8);
        assertThat(json).contains("2024-01-15");
        assertThat(json).contains("Z"); // UTC timezone
    }

    @Test
    void testTimestampEpochZero() throws Exception {
        // Given
        TimestampConverter converter = new TimestampConverter();
        Timestamp timestamp = Timestamp.newBuilder()
                .setSeconds(0)
                .setNanos(0)
                .build();

        EventMessage message = EventMessage.newBuilder()
                .setCreatedAt(timestamp)
                .build();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // When
        try (JsonGenerator gen = jsonFactory.createGenerator(baos)) {
            converter.write(gen, message, timestampField);
            gen.flush();
        }

        // Then
        String json = baos.toString(StandardCharsets.UTF_8);
        assertThat(json).contains("1970-01-01");
    }

    // ==================== DurationConverter Tests ====================

    @Test
    void testDurationConverterSupports() {
        // Given
        DurationConverter converter = new DurationConverter();

        // When/Then
        assertThat(converter.supports(durationField)).isTrue();
        assertThat(converter.supports(timestampField)).isFalse();
    }

    @Test
    void testDurationRead() throws Exception {
        // Given
        DurationConverter converter = new DurationConverter();
        String json = "\"123.456s\"";

        EventMessage.Builder builder = EventMessage.newBuilder();

        // When
        try (JsonParser parser = jsonFactory.createParser(json.getBytes())) {
            parser.nextToken();
            converter.read(parser, builder, durationField);
        }

        // Then
        EventMessage event = builder.build();
        Duration duration = event.getDuration();
        assertThat(duration.getSeconds()).isEqualTo(123);
        assertThat(duration.getNanos()).isEqualTo(456000000);
    }

    @Test
    void testDurationReadNegative() throws Exception {
        // Given
        DurationConverter converter = new DurationConverter();
        String json = "\"-10.5s\"";

        EventMessage.Builder builder = EventMessage.newBuilder();

        // When
        try (JsonParser parser = jsonFactory.createParser(json.getBytes())) {
            parser.nextToken();
            converter.read(parser, builder, durationField);
        }

        // Then
        EventMessage event = builder.build();
        Duration duration = event.getDuration();
        assertThat(duration.getSeconds()).isEqualTo(-10);
    }

    @Test
    void testDurationReadInvalid() throws Exception {
        // Given
        DurationConverter converter = new DurationConverter();
        String json = "\"not-a-duration\"";

        EventMessage.Builder builder = EventMessage.newBuilder();

        // When/Then
        try (JsonParser parser = jsonFactory.createParser(json.getBytes())) {
            parser.nextToken();
            assertThatThrownBy(() -> converter.read(parser, builder, durationField))
                    .isInstanceOf(Exception.class);
        }
    }

    @Test
    void testDurationWrite() throws Exception {
        // Given
        DurationConverter converter = new DurationConverter();
        Duration duration = Duration.newBuilder()
                .setSeconds(123)
                .setNanos(456789000)
                .build();

        EventMessage message = EventMessage.newBuilder()
                .setDuration(duration)
                .build();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // When
        try (JsonGenerator gen = jsonFactory.createGenerator(baos)) {
            converter.write(gen, message, durationField);
            gen.flush();
        }

        // Then
        String json = baos.toString(StandardCharsets.UTF_8);
        assertThat(json).isEqualTo("\"123.456789s\"");
    }

    @Test
    void testDurationZero() throws Exception {
        // Given
        DurationConverter converter = new DurationConverter();
        Duration duration = Duration.newBuilder()
                .setSeconds(0)
                .setNanos(0)
                .build();

        EventMessage message = EventMessage.newBuilder()
                .setDuration(duration)
                .build();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // When
        try (JsonGenerator gen = jsonFactory.createGenerator(baos)) {
            converter.write(gen, message, durationField);
            gen.flush();
        }

        // Then
        String json = baos.toString(StandardCharsets.UTF_8);
        assertThat(json).isEqualTo("\"0s\"");
    }

    // ==================== StructConverter Tests ====================

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

    // ==================== AnyConverter Tests ====================

    @Test
    void testAnyConverterSupports() {
        // Given
        AnyConverter converter = AnyConverter.create();

        // When/Then
        assertThat(converter.supports(anyField)).isTrue();
        assertThat(converter.supports(timestampField)).isFalse();
    }

    @Test
    void testAnyConverterRegister() {
        // Given
        AnyConverter converter = AnyConverter.create();

        // When
        converter.register(org.apache.camel.component.protojson.test.proto.SimpleUser.class);

        // Then - Should not throw exception
        assertThat(converter).isNotNull();
    }

    @Test
    void testAnyConverterReadWithRegisteredType() throws Exception {
        // Given: AnyConverter with registered SimpleUser
        AnyConverter converter = AnyConverter.create()
                .register(org.apache.camel.component.protojson.test.proto.SimpleUser.class);

        String json = """
                {
                    "@type": "type.googleapis.com/test.proto.SimpleUser",
                    "name": "John Doe",
                    "age": 30,
                    "email": "john@example.com",
                    "active": true
                }
                """;

        EventMessage.Builder builder = EventMessage.newBuilder();

        // When
        try (JsonParser parser = jsonFactory.createParser(json.getBytes())) {
            parser.nextToken();
            converter.read(parser, builder, anyField);
        }

        // Then
        EventMessage event = builder.build();
        assertThat(event.hasPayload()).isTrue();
        Any any = event.getPayload();
        assertThat(any.getTypeUrl()).contains("SimpleUser");
    }

    @Test
    void testAnyConverterReadNull() throws Exception {
        // Given
        AnyConverter converter = AnyConverter.create();
        String json = "null";

        EventMessage.Builder builder = EventMessage.newBuilder();

        // When
        try (JsonParser parser = jsonFactory.createParser(json.getBytes())) {
            parser.nextToken();
            converter.read(parser, builder, anyField);
        }

        // Then
        EventMessage event = builder.build();
        assertThat(event.hasPayload()).isFalse();
    }

    @Test
    void testAnyConverterReadMissingTypeField() throws Exception {
        // Given: JSON object without @type field
        AnyConverter converter = AnyConverter.create();
        String json = """
                {
                    "name": "John",
                    "age": 30
                }
                """;

        EventMessage.Builder builder = EventMessage.newBuilder();

        // When/Then: Should throw IOException
        try (JsonParser parser = jsonFactory.createParser(json.getBytes())) {
            parser.nextToken();
            assertThatThrownBy(() -> converter.read(parser, builder, anyField))
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("@type");
        }
    }

    @Test
    void testAnyConverterReadUnregisteredType() throws Exception {
        // Given: Type not registered
        AnyConverter converter = AnyConverter.create();
        String json = """
                {
                    "@type": "type.googleapis.com/unknown.Type",
                    "field": "value"
                }
                """;

        EventMessage.Builder builder = EventMessage.newBuilder();

        // When/Then: Should throw IOException
        try (JsonParser parser = jsonFactory.createParser(json.getBytes())) {
            parser.nextToken();
            assertThatThrownBy(() -> converter.read(parser, builder, anyField))
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("Cannot resolve type");
        }
    }

    @Test
    void testAnyConverterReadWithAllFieldTypes() throws Exception {
        // Given: JSON with all field types (STRING, INT, LONG, FLOAT, DOUBLE, BOOLEAN)
        AnyConverter converter = AnyConverter.create()
                .register(org.apache.camel.component.protojson.test.proto.SimpleUser.class);

        String json = """
                {
                    "@type": "type.googleapis.com/test.proto.SimpleUser",
                    "name": "Test",
                    "age": 25,
                    "email": "test@example.com",
                    "active": true
                }
                """;

        EventMessage.Builder builder = EventMessage.newBuilder();

        // When
        try (JsonParser parser = jsonFactory.createParser(json.getBytes())) {
            parser.nextToken();
            converter.read(parser, builder, anyField);
        }

        // Then: Tests convertSingleValue() for STRING, INT, BOOLEAN
        EventMessage event = builder.build();
        assertThat(event.hasPayload()).isTrue();
    }

    @Test
    void testAnyConverterReadWithArrayField() throws Exception {
        // Given: JSON with array (tests readJsonValue START_ARRAY branch)
        AnyConverter converter = AnyConverter.create()
                .register(org.apache.camel.component.protojson.test.proto.UserWithTags.class);

        String json = """
                {
                    "@type": "type.googleapis.com/test.proto.UserWithTags",
                    "name": "John",
                    "tags": ["tag1", "tag2", "tag3"]
                }
                """;

        EventMessage.Builder builder = EventMessage.newBuilder();

        // When
        try (JsonParser parser = jsonFactory.createParser(json.getBytes())) {
            parser.nextToken();
            converter.read(parser, builder, anyField);
        }

        // Then: Tests readJsonValue() START_ARRAY branch
        EventMessage event = builder.build();
        assertThat(event.hasPayload()).isTrue();
    }

    @Test
    void testAnyConverterReadWithNestedObject() throws Exception {
        // Given: JSON with nested object (tests readJsonValue START_OBJECT branch)
        AnyConverter converter = AnyConverter.create()
                .register(org.apache.camel.component.protojson.test.proto.UserWithAddress.class);

        String json = """
                {
                    "@type": "type.googleapis.com/test.proto.UserWithAddress",
                    "name": "John",
                    "address": {
                        "street": "Main St",
                        "city": "NYC"
                    }
                }
                """;

        EventMessage.Builder builder = EventMessage.newBuilder();

        // When
        try (JsonParser parser = jsonFactory.createParser(json.getBytes())) {
            parser.nextToken();
            converter.read(parser, builder, anyField);
        }

        // Then: Tests readJsonValue() START_OBJECT and convertSingleValue() MESSAGE branches
        EventMessage event = builder.build();
        assertThat(event.hasPayload()).isTrue();
    }

    @Test
    void testAnyConverterReadWithNumberValues() throws Exception {
        // Given: JSON with int and float numbers
        AnyConverter converter = AnyConverter.create()
                .register(org.apache.camel.component.protojson.test.proto.SimpleUser.class);

        String json = """
                {
                    "@type": "type.googleapis.com/test.proto.SimpleUser",
                    "name": "Test",
                    "age": 42
                }
                """;

        EventMessage.Builder builder = EventMessage.newBuilder();

        // When
        try (JsonParser parser = jsonFactory.createParser(json.getBytes())) {
            parser.nextToken();
            converter.read(parser, builder, anyField);
        }

        // Then: Tests readJsonValue() VALUE_NUMBER_INT branch
        EventMessage event = builder.build();
        assertThat(event.hasPayload()).isTrue();
    }

    @Test
    void testAnyConverterReadWithBooleanValues() throws Exception {
        // Given: JSON with true/false values
        AnyConverter converter = AnyConverter.create()
                .register(org.apache.camel.component.protojson.test.proto.SimpleUser.class);

        String json = """
                {
                    "@type": "type.googleapis.com/test.proto.SimpleUser",
                    "name": "Test",
                    "active": true
                }
                """;

        EventMessage.Builder builder = EventMessage.newBuilder();

        // When
        try (JsonParser parser = jsonFactory.createParser(json.getBytes())) {
            parser.nextToken();
            converter.read(parser, builder, anyField);
        }

        // Then: Tests readJsonValue() VALUE_TRUE branch and convertSingleValue() BOOLEAN
        EventMessage event = builder.build();
        assertThat(event.hasPayload()).isTrue();
    }

    @Test
    void testAnyConverterReadWithNullValues() throws Exception {
        // Given: JSON with null field
        AnyConverter converter = AnyConverter.create()
                .register(org.apache.camel.component.protojson.test.proto.SimpleUser.class);

        String json = """
                {
                    "@type": "type.googleapis.com/test.proto.SimpleUser",
                    "name": "Test",
                    "email": null
                }
                """;

        EventMessage.Builder builder = EventMessage.newBuilder();

        // When
        try (JsonParser parser = jsonFactory.createParser(json.getBytes())) {
            parser.nextToken();
            converter.read(parser, builder, anyField);
        }

        // Then: Tests readJsonValue() VALUE_NULL branch
        EventMessage event = builder.build();
        assertThat(event.hasPayload()).isTrue();
    }

    @Test
    void testAnyConverterWriteEmpty() throws Exception {
        // Given: Empty Any
        AnyConverter converter = AnyConverter.create();
        Any any = Any.newBuilder().build();

        EventMessage message = EventMessage.newBuilder()
                .setPayload(any)
                .build();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // When
        try (JsonGenerator gen = jsonFactory.createGenerator(baos)) {
            converter.write(gen, message, anyField);
            gen.flush();
        }

        // Then: Should write empty object
        String json = baos.toString(StandardCharsets.UTF_8);
        assertThat(json).isEqualTo("{}");
    }

    @Test
    void testAnyConverterWriteWithRegisteredType() throws Exception {
        // Given: Any with registered type
        AnyConverter converter = AnyConverter.create()
                .register(org.apache.camel.component.protojson.test.proto.SimpleUser.class);

        org.apache.camel.component.protojson.test.proto.SimpleUser user =
                org.apache.camel.component.protojson.test.proto.SimpleUser.newBuilder()
                        .setName("John")
                        .setAge(30)
                        .build();

        Any any = Any.pack(user);

        EventMessage message = EventMessage.newBuilder()
                .setPayload(any)
                .build();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // When
        try (JsonGenerator gen = jsonFactory.createGenerator(baos)) {
            converter.write(gen, message, anyField);
            gen.flush();
        }

        // Then: Should write @type and fields
        String json = baos.toString(StandardCharsets.UTF_8);
        assertThat(json).contains("@type");
        assertThat(json).contains("SimpleUser");
    }

    @Test
    void testAnyConverterWriteUnregisteredType() throws Exception {
        // Given: Any with unregistered type (should encode as base64)
        AnyConverter converter = AnyConverter.create();

        org.apache.camel.component.protojson.test.proto.SimpleUser user =
                org.apache.camel.component.protojson.test.proto.SimpleUser.newBuilder()
                        .setName("John")
                        .setAge(30)
                        .build();

        Any any = Any.pack(user);

        EventMessage message = EventMessage.newBuilder()
                .setPayload(any)
                .build();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // When
        try (JsonGenerator gen = jsonFactory.createGenerator(baos)) {
            converter.write(gen, message, anyField);
            gen.flush();
        }

        // Then: Should write @type and base64 value
        String json = baos.toString(StandardCharsets.UTF_8);
        assertThat(json).contains("@type");
        assertThat(json).contains("value");
    }

    @Test
    void testDurationReadObject() throws Exception {
        // Given
        DurationConverter converter = new DurationConverter();
        String json = "{\"seconds\": 300, \"nanos\": 500000000}";

        EventMessage.Builder builder = EventMessage.newBuilder();

        // When
        try (JsonParser parser = jsonFactory.createParser(json.getBytes())) {
            parser.nextToken();
            converter.read(parser, builder, durationField);
        }

        // Then
        EventMessage event = builder.build();
        Duration duration = event.getDuration();
        assertThat(duration.getSeconds()).isEqualTo(300);
        assertThat(duration.getNanos()).isEqualTo(500000000);
    }

    @Test
    void testDurationReadNull() throws Exception {
        // Given
        DurationConverter converter = new DurationConverter();
        String json = "null";

        EventMessage.Builder builder = EventMessage.newBuilder();

        // When
        try (JsonParser parser = jsonFactory.createParser(json.getBytes())) {
            parser.nextToken();
            converter.read(parser, builder, durationField);
        }

        // Then
        EventMessage event = builder.build();
        assertThat(event.hasDuration()).isFalse();
    }

    @Test
    void testDurationReadEmptyString() throws Exception {
        // Given
        DurationConverter converter = new DurationConverter();
        String json = "\"\"";

        EventMessage.Builder builder = EventMessage.newBuilder();

        // When
        try (JsonParser parser = jsonFactory.createParser(json.getBytes())) {
            parser.nextToken();
            converter.read(parser, builder, durationField);
        }

        // Then
        EventMessage event = builder.build();
        assertThat(event.hasDuration()).isFalse();
    }

    @Test
    void testDurationReadOnlySeconds() throws Exception {
        // Given
        DurationConverter converter = new DurationConverter();
        String json = "\"300s\"";

        EventMessage.Builder builder = EventMessage.newBuilder();

        // When
        try (JsonParser parser = jsonFactory.createParser(json.getBytes())) {
            parser.nextToken();
            converter.read(parser, builder, durationField);
        }

        // Then
        EventMessage event = builder.build();
        Duration duration = event.getDuration();
        assertThat(duration.getSeconds()).isEqualTo(300);
        assertThat(duration.getNanos()).isZero();
    }

    @Test
    void testDurationWriteOnlyNanos() throws Exception {
        // Given
        DurationConverter converter = new DurationConverter();
        Duration duration = Duration.newBuilder()
                .setSeconds(0)
                .setNanos(500000000)
                .build();

        EventMessage message = EventMessage.newBuilder()
                .setDuration(duration)
                .build();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // When
        try (JsonGenerator gen = jsonFactory.createGenerator(baos)) {
            converter.write(gen, message, durationField);
            gen.flush();
        }

        // Then
        String json = baos.toString(StandardCharsets.UTF_8);
        assertThat(json).isEqualTo("\"0.5s\"");
    }

    @Test
    void testTimestampReadNull() throws Exception {
        // Given
        TimestampConverter converter = new TimestampConverter();
        String json = "null";

        EventMessage.Builder builder = EventMessage.newBuilder();

        // When
        try (JsonParser parser = jsonFactory.createParser(json.getBytes())) {
            parser.nextToken();
            converter.read(parser, builder, timestampField);
        }

        // Then
        EventMessage event = builder.build();
        assertThat(event.hasCreatedAt()).isFalse();
    }

    @Test
    void testTimestampReadEmptyString() throws Exception {
        // Given
        TimestampConverter converter = new TimestampConverter();
        String json = "\"\"";

        EventMessage.Builder builder = EventMessage.newBuilder();

        // When
        try (JsonParser parser = jsonFactory.createParser(json.getBytes())) {
            parser.nextToken();
            converter.read(parser, builder, timestampField);
        }

        // Then
        EventMessage event = builder.build();
        assertThat(event.hasCreatedAt()).isFalse();
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
    void testWrapperConvertersSupportsAllTypes() {
        // Given
        WrapperConverters converter = new WrapperConverters();

        // When/Then - Test all wrapper types are supported
        assertThat(converter.supports(stringValueField)).isTrue();
        assertThat(converter.supports(int32ValueField)).isTrue();
        assertThat(converter.supports(int64ValueField)).isTrue();
        assertThat(converter.supports(uint32ValueField)).isTrue();
        assertThat(converter.supports(uint64ValueField)).isTrue();
        assertThat(converter.supports(floatValueField)).isTrue();
        assertThat(converter.supports(doubleValueField)).isTrue();
        assertThat(converter.supports(boolValueField)).isTrue();
        assertThat(converter.supports(bytesValueField)).isTrue();

        // Non-wrapper types should not be supported
        assertThat(converter.supports(timestampField)).isFalse();
        assertThat(converter.supports(durationField)).isFalse();
    }

    @Test
    void testBoolValueReadFalse() throws Exception {
        // Given
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
    void testBoolValueReadTrue() throws Exception {
        // Given
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
        // Given
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
    void testBoolValueWrite() throws Exception {
        // Given
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

    @Test
    void testBytesValueRead() throws Exception {
        // Given
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
        // Given
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

    @Test
    void testInt32ValueReadNull() throws Exception {
        // Given
        WrapperConverters converter = new WrapperConverters();
        String json = "null";

        EventMessage.Builder builder = EventMessage.newBuilder();

        // When
        try (JsonParser parser = jsonFactory.createParser(json.getBytes())) {
            parser.nextToken();
            converter.read(parser, builder, int32ValueField);
        }

        // Then
        EventMessage event = builder.build();
        assertThat(event.hasOptionalCount()).isFalse();
    }

    @Test
    void testInt32ValueReadAsString() throws Exception {
        // Given: String token "42" not numeric token 42
        WrapperConverters converter = new WrapperConverters();
        String json = "\"42\"";

        EventMessage.Builder builder = EventMessage.newBuilder();

        // When
        try (JsonParser parser = jsonFactory.createParser(json.getBytes())) {
            parser.nextToken();
            converter.read(parser, builder, int32ValueField);
        }

        // Then: Should parse string to int (isScalarValue() branch)
        EventMessage event = builder.build();
        assertThat(event.hasOptionalCount()).isTrue();
        assertThat(event.getOptionalCount().getValue()).isEqualTo(42);
    }

    @Test
    void testInt64ValueReadAsString() throws Exception {
        // Given: String token
        WrapperConverters converter = new WrapperConverters();
        String json = "\"9223372036854775807\"";

        EventMessage.Builder builder = EventMessage.newBuilder();

        // When
        try (JsonParser parser = jsonFactory.createParser(json.getBytes())) {
            parser.nextToken();
            converter.read(parser, builder, int64ValueField);
        }

        // Then: Should parse string to long (isScalarValue() branch)
        EventMessage event = builder.build();
        assertThat(event.hasOptionalLong()).isTrue();
        assertThat(event.getOptionalLong().getValue()).isEqualTo(9223372036854775807L);
    }

    @Test
    void testUInt32ValueReadAsString() throws Exception {
        // Given: String token
        WrapperConverters converter = new WrapperConverters();
        String json = "\"100\"";

        EventMessage.Builder builder = EventMessage.newBuilder();

        // When
        try (JsonParser parser = jsonFactory.createParser(json.getBytes())) {
            parser.nextToken();
            converter.read(parser, builder, uint32ValueField);
        }

        // Then: Should parse string to unsigned int (isScalarValue() branch)
        EventMessage event = builder.build();
        assertThat(event.hasOptionalUint()).isTrue();
        assertThat(event.getOptionalUint().getValue()).isEqualTo(100);
    }

    @Test
    void testUInt64ValueReadAsString() throws Exception {
        // Given: String token
        WrapperConverters converter = new WrapperConverters();
        String json = "\"1000\"";

        EventMessage.Builder builder = EventMessage.newBuilder();

        // When
        try (JsonParser parser = jsonFactory.createParser(json.getBytes())) {
            parser.nextToken();
            converter.read(parser, builder, uint64ValueField);
        }

        // Then: Should parse string to unsigned long (isScalarValue() branch)
        EventMessage event = builder.build();
        assertThat(event.hasOptionalUlong()).isTrue();
        assertThat(event.getOptionalUlong().getValue()).isEqualTo(1000L);
    }

    @Test
    void testFloatValueReadAsString() throws Exception {
        // Given: String token
        WrapperConverters converter = new WrapperConverters();
        String json = "\"3.14159\"";

        EventMessage.Builder builder = EventMessage.newBuilder();

        // When
        try (JsonParser parser = jsonFactory.createParser(json.getBytes())) {
            parser.nextToken();
            converter.read(parser, builder, floatValueField);
        }

        // Then: Should parse string to float (isScalarValue() branch)
        EventMessage event = builder.build();
        assertThat(event.hasOptionalFloat()).isTrue();
        assertThat(event.getOptionalFloat().getValue()).isCloseTo(3.14159f, within(0.00001f));
    }

    @Test
    void testDoubleValueReadAsString() throws Exception {
        // Given: String token
        WrapperConverters converter = new WrapperConverters();
        String json = "\"2.718281828459045\"";

        EventMessage.Builder builder = EventMessage.newBuilder();

        // When
        try (JsonParser parser = jsonFactory.createParser(json.getBytes())) {
            parser.nextToken();
            converter.read(parser, builder, doubleValueField);
        }

        // Then: Should parse string to double (isScalarValue() branch)
        EventMessage event = builder.build();
        assertThat(event.hasOptionalDouble()).isTrue();
        assertThat(event.getOptionalDouble().getValue()).isCloseTo(2.718281828459045, within(0.000000000000001));
    }

    @Test
    void testBoolValueReadAsStringTrue() throws Exception {
        // Given: String token "true"
        WrapperConverters converter = new WrapperConverters();
        String json = "\"true\"";

        EventMessage.Builder builder = EventMessage.newBuilder();

        // When
        try (JsonParser parser = jsonFactory.createParser(json.getBytes())) {
            parser.nextToken();
            converter.read(parser, builder, boolValueField);
        }

        // Then: Should parse string to boolean (isScalarValue() branch)
        EventMessage event = builder.build();
        assertThat(event.hasOptionalBool()).isTrue();
        assertThat(event.getOptionalBool().getValue()).isTrue();
    }

    @Test
    void testBoolValueReadAsStringFalse() throws Exception {
        // Given: String token "false"
        WrapperConverters converter = new WrapperConverters();
        String json = "\"false\"";

        EventMessage.Builder builder = EventMessage.newBuilder();

        // When
        try (JsonParser parser = jsonFactory.createParser(json.getBytes())) {
            parser.nextToken();
            converter.read(parser, builder, boolValueField);
        }

        // Then: Should parse string to boolean (isScalarValue() branch)
        EventMessage event = builder.build();
        assertThat(event.hasOptionalBool()).isTrue();
        assertThat(event.getOptionalBool().getValue()).isFalse();
    }

    @Test
    void testStringValueReadAsScalar() throws Exception {
        // Given: String token (this tests isScalarValue() branch for StringValue)
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

    // ==================== WellKnownConverters Factory Tests ====================

    @Test
    void testWellKnownConvertersAllInConverters() {
        // When
        var converters = WellKnownConverters.allInConverters();

        // Then
        assertThat(converters).isNotEmpty();
        assertThat(converters).hasSize(4); // Timestamp, Duration, Struct, Wrappers
    }

    @Test
    void testWellKnownConvertersAllOutConverters() {
        // When
        var converters = WellKnownConverters.allOutConverters();

        // Then
        assertThat(converters).isNotEmpty();
        assertThat(converters).hasSize(4); // Timestamp, Duration, Struct, Wrappers
    }

    @Test
    void testWellKnownConvertersContainsTimestamp() {
        // When
        var converters = WellKnownConverters.allInConverters();

        // Then
        assertThat(converters).anyMatch(c -> c instanceof TimestampConverter);
    }

    @Test
    void testWellKnownConvertersContainsDuration() {
        // When
        var converters = WellKnownConverters.allInConverters();

        // Then
        assertThat(converters).anyMatch(c -> c instanceof DurationConverter);
    }

    @Test
    void testWellKnownConvertersContainsStruct() {
        // When
        var converters = WellKnownConverters.allInConverters();

        // Then
        assertThat(converters).anyMatch(c -> c instanceof StructConverter);
    }

    @Test
    void testWellKnownConvertersContainsWrappers() {
        // When
        var converters = WellKnownConverters.allInConverters();

        // Then - WrapperConverters is a single class handling all wrapper types
        assertThat(converters).anyMatch(c -> c instanceof WrapperConverters);
    }

    @Test
    void testWellKnownConvertersTimestampFactory() {
        // When
        TimestampConverter converter = WellKnownConverters.timestamp();

        // Then
        assertThat(converter).isNotNull();
        assertThat(converter).isInstanceOf(TimestampConverter.class);
    }

    @Test
    void testWellKnownConvertersDurationFactory() {
        // When
        DurationConverter converter = WellKnownConverters.duration();

        // Then
        assertThat(converter).isNotNull();
        assertThat(converter).isInstanceOf(DurationConverter.class);
    }

    @Test
    void testWellKnownConvertersStructFactory() {
        // When
        StructConverter converter = WellKnownConverters.struct();

        // Then
        assertThat(converter).isNotNull();
        assertThat(converter).isInstanceOf(StructConverter.class);
    }

    @Test
    void testWellKnownConvertersWrappersFactory() {
        // When
        WrapperConverters converter = WellKnownConverters.wrappers();

        // Then
        assertThat(converter).isNotNull();
        assertThat(converter).isInstanceOf(WrapperConverters.class);
    }

    // ==================== Edge Cases ====================

    @Test
    void testTimestampMaxValue() throws Exception {
        // Given
        TimestampConverter converter = new TimestampConverter();
        Timestamp timestamp = Timestamp.newBuilder()
                .setSeconds(253402300799L) // 9999-12-31T23:59:59Z
                .setNanos(999999999)
                .build();

        EventMessage message = EventMessage.newBuilder()
                .setCreatedAt(timestamp)
                .build();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // When
        try (JsonGenerator gen = jsonFactory.createGenerator(baos)) {
            converter.write(gen, message, timestampField);
            gen.flush();
        }

        // Then
        String json = baos.toString(StandardCharsets.UTF_8);
        assertThat(json).isNotEmpty();
    }

    @Test
    void testDurationMaxValue() throws Exception {
        // Given
        DurationConverter converter = new DurationConverter();
        Duration duration = Duration.newBuilder()
                .setSeconds(315576000000L)
                .setNanos(999999999)
                .build();

        EventMessage message = EventMessage.newBuilder()
                .setDuration(duration)
                .build();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // When
        try (JsonGenerator gen = jsonFactory.createGenerator(baos)) {
            converter.write(gen, message, durationField);
            gen.flush();
        }

        // Then
        String json = baos.toString(StandardCharsets.UTF_8);
        assertThat(json).contains("s");
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
