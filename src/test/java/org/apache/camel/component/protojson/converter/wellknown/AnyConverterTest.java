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

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for AnyConverter covering all branches.
 */
class AnyConverterTest {

    private JsonFactory jsonFactory;
    private Descriptors.FieldDescriptor anyField;

    @BeforeEach
    void setUp() {
        jsonFactory = new JsonFactory();
        Descriptors.Descriptor eventDesc = EventMessage.getDescriptor();
        anyField = eventDesc.findFieldByName("payload");
    }

    @Test
    void testAnyConverterSupports() {
        // Given
        AnyConverter converter = AnyConverter.create();
        Descriptors.FieldDescriptor timestampField = EventMessage.getDescriptor().findFieldByName("created_at");

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

    // ==================== Advanced Coverage Tests ====================

    @Test
    void testAnyConverterWithLongField() throws Exception {
        // Given: Message with int64/long field to cover LONG branch in convertSingleValue
        AnyConverter converter = AnyConverter.create()
                .register(org.apache.camel.component.protojson.test.proto.AnyTestMessage.class);

        String json = """
                {
                    "@type": "type.googleapis.com/test.proto.AnyTestMessage",
                    "longField": 9223372036854775807
                }
                """;

        EventMessage.Builder builder = EventMessage.newBuilder();

        // When
        try (JsonParser parser = jsonFactory.createParser(json.getBytes())) {
            parser.nextToken();
            converter.read(parser, builder, anyField);
        }

        // Then: Tests convertSingleValue() LONG branch
        EventMessage event = builder.build();
        assertThat(event.hasPayload()).isTrue();
    }

    @Test
    void testAnyConverterWithFloatField() throws Exception {
        // Given: Message with float field to cover FLOAT branch in convertSingleValue
        AnyConverter converter = AnyConverter.create()
                .register(org.apache.camel.component.protojson.test.proto.AnyTestMessage.class);

        String json = """
                {
                    "@type": "type.googleapis.com/test.proto.AnyTestMessage",
                    "floatField": 3.14159
                }
                """;

        EventMessage.Builder builder = EventMessage.newBuilder();

        // When
        try (JsonParser parser = jsonFactory.createParser(json.getBytes())) {
            parser.nextToken();
            converter.read(parser, builder, anyField);
        }

        // Then: Tests convertSingleValue() FLOAT branch and readJsonValue() VALUE_NUMBER_FLOAT
        EventMessage event = builder.build();
        assertThat(event.hasPayload()).isTrue();
    }

    @Test
    void testAnyConverterWithDoubleField() throws Exception {
        // Given: Message with double field to cover DOUBLE branch in convertSingleValue
        AnyConverter converter = AnyConverter.create()
                .register(org.apache.camel.component.protojson.test.proto.AnyTestMessage.class);

        String json = """
                {
                    "@type": "type.googleapis.com/test.proto.AnyTestMessage",
                    "doubleField": 2.718281828459045
                }
                """;

        EventMessage.Builder builder = EventMessage.newBuilder();

        // When
        try (JsonParser parser = jsonFactory.createParser(json.getBytes())) {
            parser.nextToken();
            converter.read(parser, builder, anyField);
        }

        // Then: Tests convertSingleValue() DOUBLE branch
        EventMessage event = builder.build();
        assertThat(event.hasPayload()).isTrue();
    }

    @Test
    void testAnyConverterWithEnumFieldAsNumber() throws Exception {
        // Given: Enum field as number to cover ENUM number branch in convertSingleValue
        AnyConverter converter = AnyConverter.create()
                .register(org.apache.camel.component.protojson.test.proto.AnyTestMessage.class);

        String json = """
                {
                    "@type": "type.googleapis.com/test.proto.AnyTestMessage",
                    "enumField": 1
                }
                """;

        EventMessage.Builder builder = EventMessage.newBuilder();

        // When
        try (JsonParser parser = jsonFactory.createParser(json.getBytes())) {
            parser.nextToken();
            converter.read(parser, builder, anyField);
        }

        // Then: Tests convertSingleValue() ENUM branch with number input
        EventMessage event = builder.build();
        assertThat(event.hasPayload()).isTrue();
    }

    @Test
    void testAnyConverterWithEnumFieldAsString() throws Exception {
        // Given: Enum field as string to cover ENUM string branch in convertSingleValue
        AnyConverter converter = AnyConverter.create()
                .register(org.apache.camel.component.protojson.test.proto.AnyTestMessage.class);

        String json = """
                {
                    "@type": "type.googleapis.com/test.proto.AnyTestMessage",
                    "enumField": "ACTIVE"
                }
                """;

        EventMessage.Builder builder = EventMessage.newBuilder();

        // When
        try (JsonParser parser = jsonFactory.createParser(json.getBytes())) {
            parser.nextToken();
            converter.read(parser, builder, anyField);
        }

        // Then: Tests convertSingleValue() ENUM branch with string input
        EventMessage event = builder.build();
        assertThat(event.hasPayload()).isTrue();
    }

    @Test
    void testAnyConverterWithBytesField() throws Exception {
        // Given: Bytes field to cover BYTE_STRING branch in convertSingleValue
        AnyConverter converter = AnyConverter.create()
                .register(org.apache.camel.component.protojson.test.proto.AnyTestMessage.class);

        String base64 = java.util.Base64.getEncoder().encodeToString("test data".getBytes());
        String json = String.format("""
                {
                    "@type": "type.googleapis.com/test.proto.AnyTestMessage",
                    "bytesField": "%s"
                }
                """, base64);

        EventMessage.Builder builder = EventMessage.newBuilder();

        // When
        try (JsonParser parser = jsonFactory.createParser(json.getBytes())) {
            parser.nextToken();
            converter.read(parser, builder, anyField);
        }

        // Then: Tests convertSingleValue() BYTE_STRING branch
        EventMessage event = builder.build();
        assertThat(event.hasPayload()).isTrue();
    }

    @Test
    void testAnyConverterWithFalseBoolean() throws Exception {
        // Given: Boolean false to cover VALUE_FALSE token in readJsonValue
        AnyConverter converter = AnyConverter.create()
                .register(org.apache.camel.component.protojson.test.proto.AnyTestMessage.class);

        String json = """
                {
                    "@type": "type.googleapis.com/test.proto.AnyTestMessage",
                    "boolField": false
                }
                """;

        EventMessage.Builder builder = EventMessage.newBuilder();

        // When
        try (JsonParser parser = jsonFactory.createParser(json.getBytes())) {
            parser.nextToken();
            converter.read(parser, builder, anyField);
        }

        // Then: Tests readJsonValue() VALUE_FALSE branch
        EventMessage event = builder.build();
        assertThat(event.hasPayload()).isTrue();
    }

    @Test
    void testAnyConverterWriteWithRepeatedFields() throws Exception {
        // Given: Message with repeated fields to cover array output in writeMessageFields
        AnyConverter converter = AnyConverter.create()
                .register(org.apache.camel.component.protojson.test.proto.AnyTestMessage.class);

        org.apache.camel.component.protojson.test.proto.AnyTestMessage testMsg =
                org.apache.camel.component.protojson.test.proto.AnyTestMessage.newBuilder()
                        .addRepeatedField("tag1")
                        .addRepeatedField("tag2")
                        .addRepeatedField("tag3")
                        .build();

        Any any = Any.pack(testMsg);
        EventMessage message = EventMessage.newBuilder()
                .setPayload(any)
                .build();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // When
        try (JsonGenerator gen = jsonFactory.createGenerator(baos)) {
            converter.write(gen, message, anyField);
            gen.flush();
        }

        // Then: Tests writeMessageFields() repeated field branch (array output)
        String json = baos.toString(StandardCharsets.UTF_8);
        assertThat(json).contains("[");
        assertThat(json).contains("]");
    }

    @Test
    void testAnyConverterWriteWithLongField() throws Exception {
        // Given: Message with long field to cover LONG branch in writeJsonValue
        AnyConverter converter = AnyConverter.create()
                .register(org.apache.camel.component.protojson.test.proto.AnyTestMessage.class);

        org.apache.camel.component.protojson.test.proto.AnyTestMessage testMsg =
                org.apache.camel.component.protojson.test.proto.AnyTestMessage.newBuilder()
                        .setLongField(9223372036854775807L)
                        .build();

        Any any = Any.pack(testMsg);
        EventMessage message = EventMessage.newBuilder()
                .setPayload(any)
                .build();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // When
        try (JsonGenerator gen = jsonFactory.createGenerator(baos)) {
            converter.write(gen, message, anyField);
            gen.flush();
        }

        // Then: Tests writeJsonValue() LONG branch
        String json = baos.toString(StandardCharsets.UTF_8);
        assertThat(json).contains("9223372036854775807");
    }

    @Test
    void testAnyConverterWriteWithFloatField() throws Exception {
        // Given: Message with float field to cover FLOAT branch in writeJsonValue
        AnyConverter converter = AnyConverter.create()
                .register(org.apache.camel.component.protojson.test.proto.AnyTestMessage.class);

        org.apache.camel.component.protojson.test.proto.AnyTestMessage testMsg =
                org.apache.camel.component.protojson.test.proto.AnyTestMessage.newBuilder()
                        .setFloatField(3.14159f)
                        .build();

        Any any = Any.pack(testMsg);
        EventMessage message = EventMessage.newBuilder()
                .setPayload(any)
                .build();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // When
        try (JsonGenerator gen = jsonFactory.createGenerator(baos)) {
            converter.write(gen, message, anyField);
            gen.flush();
        }

        // Then: Tests writeJsonValue() FLOAT branch
        String json = baos.toString(StandardCharsets.UTF_8);
        assertThat(json).contains("3.14159");
    }

    @Test
    void testAnyConverterWriteWithDoubleField() throws Exception {
        // Given: Message with double field to cover DOUBLE branch in writeJsonValue
        AnyConverter converter = AnyConverter.create()
                .register(org.apache.camel.component.protojson.test.proto.AnyTestMessage.class);

        org.apache.camel.component.protojson.test.proto.AnyTestMessage testMsg =
                org.apache.camel.component.protojson.test.proto.AnyTestMessage.newBuilder()
                        .setDoubleField(2.718281828459045)
                        .build();

        Any any = Any.pack(testMsg);
        EventMessage message = EventMessage.newBuilder()
                .setPayload(any)
                .build();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // When
        try (JsonGenerator gen = jsonFactory.createGenerator(baos)) {
            converter.write(gen, message, anyField);
            gen.flush();
        }

        // Then: Tests writeJsonValue() DOUBLE branch
        String json = baos.toString(StandardCharsets.UTF_8);
        assertThat(json).contains("2.718281828459045");
    }

    @Test
    void testAnyConverterWriteWithEnumField() throws Exception {
        // Given: Message with enum field to cover ENUM branch in writeJsonValue
        AnyConverter converter = AnyConverter.create()
                .register(org.apache.camel.component.protojson.test.proto.AnyTestMessage.class);

        org.apache.camel.component.protojson.test.proto.AnyTestMessage testMsg =
                org.apache.camel.component.protojson.test.proto.AnyTestMessage.newBuilder()
                        .setEnumField(org.apache.camel.component.protojson.test.proto.UserStatus.ACTIVE)
                        .build();

        Any any = Any.pack(testMsg);
        EventMessage message = EventMessage.newBuilder()
                .setPayload(any)
                .build();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // When
        try (JsonGenerator gen = jsonFactory.createGenerator(baos)) {
            converter.write(gen, message, anyField);
            gen.flush();
        }

        // Then: Tests writeJsonValue() ENUM branch
        String json = baos.toString(StandardCharsets.UTF_8);
        assertThat(json).contains("ACTIVE");
    }

    @Test
    void testAnyConverterWriteWithBytesField() throws Exception {
        // Given: Message with bytes field to cover BYTE_STRING branch in writeJsonValue
        AnyConverter converter = AnyConverter.create()
                .register(org.apache.camel.component.protojson.test.proto.AnyTestMessage.class);

        org.apache.camel.component.protojson.test.proto.AnyTestMessage testMsg =
                org.apache.camel.component.protojson.test.proto.AnyTestMessage.newBuilder()
                        .setBytesField(ByteString.copyFromUtf8("test data"))
                        .build();

        Any any = Any.pack(testMsg);
        EventMessage message = EventMessage.newBuilder()
                .setPayload(any)
                .build();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // When
        try (JsonGenerator gen = jsonFactory.createGenerator(baos)) {
            converter.write(gen, message, anyField);
            gen.flush();
        }

        // Then: Tests writeJsonValue() BYTE_STRING branch
        String json = baos.toString(StandardCharsets.UTF_8);
        String expectedBase64 = java.util.Base64.getEncoder().encodeToString("test data".getBytes());
        assertThat(json).contains(expectedBase64);
    }

    @Test
    void testAnyConverterWriteWithBooleanField() throws Exception {
        // Given: Message with boolean field to cover BOOLEAN branch in writeJsonValue
        AnyConverter converter = AnyConverter.create()
                .register(org.apache.camel.component.protojson.test.proto.AnyTestMessage.class);

        org.apache.camel.component.protojson.test.proto.AnyTestMessage testMsg =
                org.apache.camel.component.protojson.test.proto.AnyTestMessage.newBuilder()
                        .setBoolField(true)
                        .build();

        Any any = Any.pack(testMsg);
        EventMessage message = EventMessage.newBuilder()
                .setPayload(any)
                .build();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // When
        try (JsonGenerator gen = jsonFactory.createGenerator(baos)) {
            converter.write(gen, message, anyField);
            gen.flush();
        }

        // Then: Tests writeJsonValue() BOOLEAN branch
        String json = baos.toString(StandardCharsets.UTF_8);
        assertThat(json).contains("true");
    }

    @Test
    void testAnyConverterReadWithJsonName() throws Exception {
        // Given: JSON with camelCase field name to cover findFieldByJsonName() method
        AnyConverter converter = AnyConverter.create()
                .register(org.apache.camel.component.protojson.test.proto.AnyTestMessage.class);

        // Use JSON name (camelCase) instead of field name (snake_case)
        String json = """
                {
                    "@type": "type.googleapis.com/test.proto.AnyTestMessage",
                    "stringField": "test",
                    "longField": 123
                }
                """;

        EventMessage.Builder builder = EventMessage.newBuilder();

        // When
        try (JsonParser parser = jsonFactory.createParser(json.getBytes())) {
            parser.nextToken();
            converter.read(parser, builder, anyField);
        }

        // Then: Tests findFieldByJsonName() method
        EventMessage event = builder.build();
        assertThat(event.hasPayload()).isTrue();
    }

    @Test
    void testAnyConverterReadTypeUrlWithoutPrefix() throws Exception {
        // Given: Type URL without "type.googleapis.com/" prefix
        AnyConverter converter = AnyConverter.create()
                .register(org.apache.camel.component.protojson.test.proto.SimpleUser.class);

        String json = """
                {
                    "@type": "test.proto.SimpleUser",
                    "name": "John"
                }
                """;

        EventMessage.Builder builder = EventMessage.newBuilder();

        // When
        try (JsonParser parser = jsonFactory.createParser(json.getBytes())) {
            parser.nextToken();
            converter.read(parser, builder, anyField);
        }

        // Then: Tests type URL handling without prefix
        EventMessage event = builder.build();
        assertThat(event.hasPayload()).isTrue();
    }

    @Test
    void testAnyConverterWriteInvalidProtocolBuffer() throws Exception {
        // Given: Any with corrupted bytes to trigger InvalidProtocolBufferException
        AnyConverter converter = AnyConverter.create()
                .register(org.apache.camel.component.protojson.test.proto.SimpleUser.class);

        // Create Any with invalid/corrupted value bytes
        Any any = Any.newBuilder()
                .setTypeUrl("type.googleapis.com/test.proto.SimpleUser")
                .setValue(ByteString.copyFromUtf8("corrupted data that is not valid protobuf"))
                .build();

        EventMessage message = EventMessage.newBuilder()
                .setPayload(any)
                .build();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // When
        try (JsonGenerator gen = jsonFactory.createGenerator(baos)) {
            converter.write(gen, message, anyField);
            gen.flush();
        }

        // Then: Should fall back to base64 encoding due to InvalidProtocolBufferException
        String json = baos.toString(StandardCharsets.UTF_8);
        assertThat(json).contains("value");
        assertThat(json).contains("@type");
    }

    @Test
    void testAnyConverterRegisterInvalidClass() {
        // Given: Try to register a class without getDescriptor() method
        AnyConverter converter = AnyConverter.create();

        // When/Then: Should throw IllegalArgumentException
        assertThatThrownBy(() -> converter.register(String.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot get descriptor");
    }
}
