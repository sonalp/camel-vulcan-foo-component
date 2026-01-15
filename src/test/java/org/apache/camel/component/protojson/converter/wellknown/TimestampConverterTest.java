package org.apache.camel.component.protojson.converter.wellknown;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.google.protobuf.Descriptors;
import com.google.protobuf.Timestamp;
import org.apache.camel.component.protojson.test.proto.EventMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for TimestampConverter.
 */
class TimestampConverterTest {

    private JsonFactory jsonFactory;
    private Descriptors.FieldDescriptor timestampField;
    private Descriptors.FieldDescriptor durationField;

    @BeforeEach
    void setUp() {
        jsonFactory = new JsonFactory();
        Descriptors.Descriptor eventDesc = EventMessage.getDescriptor();
        timestampField = eventDesc.findFieldByName("created_at");
        durationField = eventDesc.findFieldByName("duration");
    }

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
    void testTimestampReadObject() throws Exception {
        // Given: Object format {"seconds": ..., "nanos": ...}
        TimestampConverter converter = new TimestampConverter();
        String json = "{\"seconds\": 1705315800, \"nanos\": 123456000}";

        EventMessage.Builder builder = EventMessage.newBuilder();

        // When
        try (JsonParser parser = jsonFactory.createParser(json.getBytes())) {
            parser.nextToken();
            converter.read(parser, builder, timestampField);
        }

        // Then: Tests START_OBJECT branch
        EventMessage event = builder.build();
        assertThat(event.hasCreatedAt()).isTrue();
        Timestamp timestamp = event.getCreatedAt();
        assertThat(timestamp.getSeconds()).isEqualTo(1705315800);
        assertThat(timestamp.getNanos()).isEqualTo(123456000);
    }

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
}
