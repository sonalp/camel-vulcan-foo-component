package org.apache.camel.component.protojson.converter.wellknown;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.google.protobuf.Descriptors;
import com.google.protobuf.Duration;
import org.apache.camel.component.protojson.test.proto.EventMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for DurationConverter.
 */
class DurationConverterTest {

    private JsonFactory jsonFactory;
    private Descriptors.FieldDescriptor durationField;
    private Descriptors.FieldDescriptor timestampField;

    @BeforeEach
    void setUp() {
        jsonFactory = new JsonFactory();
        Descriptors.Descriptor eventDesc = EventMessage.getDescriptor();
        durationField = eventDesc.findFieldByName("duration");
        timestampField = eventDesc.findFieldByName("created_at");
    }

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
}
