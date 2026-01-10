// src/test/java/org/apache/camel/component/protojson/WellKnownTypesTest.java

package org.apache.camel.component.protojson;

import com.google.protobuf.*;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.protojson.converter.wellknown.AnyConverter;
import org.apache.camel.component.protojson.test.proto.EventMessage;
import org.apache.camel.component.protojson.test.proto.SimpleUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for well-known type handling.
 */
@DisplayName("Well-Known Types Tests")
public class WellKnownTypesTest extends BaseProtoJsonTest {

    @Override
    protected CamelContext createCamelContext() throws Exception {

        CamelContext camelContext = super.createCamelContext();
        AnyConverter anyConverter = AnyConverter.create()
                .register(SimpleUser.class);

        camelContext.getRegistry().bind("anyConverter", anyConverter);

        return camelContext;

    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // Register AnyConverter with allowed types to Camel registry


                ProtoJsonDataFormat protoJson = new ProtoJsonDataFormat(EventMessage.class);

                from("direct:marshal")
                        .marshal(protoJson)
                        .convertBodyTo(String.class);

                from("direct:unmarshal")
                        .unmarshal(protoJson);
            }
        };
    }

    @Nested
    @DisplayName("Timestamp Tests")
    class TimestampTests {

        @Test
        @DisplayName("Should marshal Timestamp as ISO-8601")
        void shouldMarshalTimestampAsIso() throws Exception {
            Instant now = Instant.parse("2024-06-15T10:30:00.123456789Z");
            Timestamp ts = Timestamp.newBuilder()
                    .setSeconds(now.getEpochSecond())
                    .setNanos(now.getNano())
                    .build();

            EventMessage event = EventMessage.newBuilder()
                    .setEventId("evt-1")
                    .setCreatedAt(ts)
                    .build();

            String json = marshalToJson(event);

            assertThat(json).contains("\"createdAt\"");
            assertThat(json).contains("2024-06-15T10:30:00");
        }

        @Test
        @DisplayName("Should unmarshal ISO-8601 to Timestamp")
        void shouldUnmarshalIsoToTimestamp() {
            String json = """
                    {
                        "eventId": "evt-2",
                        "createdAt": "2024-12-25T08:00:00.000Z"
                    }
                    """;

            EventMessage event = unmarshalFromJson(json, EventMessage.class);

            assertThat(event.getCreatedAt().getSeconds())
                    .isEqualTo(Instant.parse("2024-12-25T08:00:00Z").getEpochSecond());
        }

        @Test
        @DisplayName("Should round-trip Timestamp")
        void shouldRoundTripTimestamp() {
            Instant now = Instant.now();
            Timestamp ts = Timestamp.newBuilder()
                    .setSeconds(now.getEpochSecond())
                    .setNanos(now.getNano())
                    .build();

            EventMessage original = EventMessage.newBuilder()
                    .setEventId("evt-rt")
                    .setCreatedAt(ts)
                    .build();

            EventMessage result = roundTrip(original);

            assertThat(result.getCreatedAt().getSeconds())
                    .isEqualTo(original.getCreatedAt().getSeconds());
        }
    }

    @Nested
    @DisplayName("Duration Tests")
    class DurationTests {

        @Test
        @DisplayName("Should marshal Duration with seconds")
        void shouldMarshalDurationWithSeconds() throws Exception {
            Duration duration = Duration.newBuilder()
                    .setSeconds(120)
                    .setNanos(0)
                    .build();

            EventMessage event = EventMessage.newBuilder()
                    .setEventId("evt-dur")
                    .setDuration(duration)
                    .build();

            String json = marshalToJson(event);

            assertThat(json).contains("\"duration\"");
            assertThat(json).contains("120s");
        }

        @Test
        @DisplayName("Should marshal Duration with nanos")
        void shouldMarshalDurationWithNanos() throws Exception {
            Duration duration = Duration.newBuilder()
                    .setSeconds(3)
                    .setNanos(500000000)
                    .build();

            EventMessage event = EventMessage.newBuilder()
                    .setEventId("evt-dur-nano")
                    .setDuration(duration)
                    .build();

            String json = marshalToJson(event);

            assertThat(json).contains("3.5s");
        }

        @Test
        @DisplayName("Should unmarshal Duration string")
        void shouldUnmarshalDurationString() {
            String json = """
                    {
                        "eventId": "evt-parse-dur",
                        "duration": "45.25s"
                    }
                    """;

            EventMessage event = unmarshalFromJson(json, EventMessage.class);

            assertThat(event.getDuration().getSeconds()).isEqualTo(45);
            assertThat(event.getDuration().getNanos()).isEqualTo(250000000);
        }

        @Test
        @DisplayName("Should round-trip Duration")
        void shouldRoundTripDuration() {
            Duration duration = Duration.newBuilder()
                    .setSeconds(90)
                    .setNanos(123456789)
                    .build();

            EventMessage original = EventMessage.newBuilder()
                    .setEventId("evt-rt-dur")
                    .setDuration(duration)
                    .build();

            EventMessage result = roundTrip(original);

            assertThat(result.getDuration().getSeconds())
                    .isEqualTo(original.getDuration().getSeconds());
        }
    }

    @Nested
    @DisplayName("Struct Tests")
    class StructTests {

        @Test
        @DisplayName("Should marshal Struct as JSON object")
        void shouldMarshalStructAsJsonObject() throws Exception {
            Struct metadata = Struct.newBuilder()
                    .putFields("key1", Value.newBuilder().setStringValue("value1").build())
                    .putFields("count", Value.newBuilder().setNumberValue(42).build())
                    .putFields("active", Value.newBuilder().setBoolValue(true).build())
                    .build();

            EventMessage event = EventMessage.newBuilder()
                    .setEventId("evt-struct")
                    .setMetadata(metadata)
                    .build();

            String json = marshalToJson(event);

            assertThat(json).contains("\"metadata\"");
            assertThat(json).contains("\"key1\":\"value1\"");
            assertThat(json).contains("\"count\":42");
            assertThat(json).contains("\"active\":true");
        }

        @Test
        @DisplayName("Should unmarshal JSON object to Struct")
        void shouldUnmarshalJsonObjectToStruct() {
            String json = """
                    {
                        "eventId": "evt-parse-struct",
                        "metadata": {
                            "env": "production",
                            "version": 2.5,
                            "enabled": false
                        }
                    }
                    """;

            EventMessage event = unmarshalFromJson(json, EventMessage.class);

            Struct metadata = event.getMetadata();
            assertThat(metadata.getFieldsMap()).containsKey("env");
            assertThat(metadata.getFieldsMap().get("env").getStringValue())
                    .isEqualTo("production");
            assertThat(metadata.getFieldsMap().get("version").getNumberValue())
                    .isEqualTo(2.5);
            assertThat(metadata.getFieldsMap().get("enabled").getBoolValue())
                    .isFalse();
        }

        @Test
        @DisplayName("Should handle nested Struct")
        void shouldHandleNestedStruct() {
            String json = """
                    {
                        "eventId": "evt-nested",
                        "metadata": {
                            "outer": {
                                "inner": "value"
                            },
                            "list": [1, 2, 3]
                        }
                    }
                    """;

            EventMessage event = unmarshalFromJson(json, EventMessage.class);

            Struct metadata = event.getMetadata();

            Value outerValue = metadata.getFieldsMap().get("outer");
            assertThat(outerValue.hasStructValue()).isTrue();
            assertThat(outerValue.getStructValue().getFieldsMap().get("inner").getStringValue())
                    .isEqualTo("value");

            Value listValue = metadata.getFieldsMap().get("list");
            assertThat(listValue.hasListValue()).isTrue();
            assertThat(listValue.getListValue().getValuesCount()).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("Wrapper Types Tests")
    class WrapperTypesTests {

        @Test
        @DisplayName("Should marshal StringValue")
        void shouldMarshalStringValue() throws Exception {
            EventMessage event = EventMessage.newBuilder()
                    .setEventId("evt-wrapper")
                    .setOptionalNote(StringValue.of("This is a note"))
                    .build();

            String json = marshalToJson(event);

            assertThat(json).contains("\"optionalNote\"");
            assertThat(json).contains("This is a note");
        }

        @Test
        @DisplayName("Should marshal Int32Value")
        void shouldMarshalInt32Value() throws Exception {
            EventMessage event = EventMessage.newBuilder()
                    .setEventId("evt-int-wrapper")
                    .setOptionalCount(Int32Value.of(999))
                    .build();

            String json = marshalToJson(event);

            assertThat(json).contains("\"optionalCount\"");
            assertThat(json).contains("999");
        }

        @Test
        @DisplayName("Should unmarshal wrapper types")
        void shouldUnmarshalWrapperTypes() {
            String json = """
                    {
                        "eventId": "evt-parse-wrapper",
                        "optionalNote": "Parsed note",
                        "optionalCount": 123
                    }
                    """;

            EventMessage event = unmarshalFromJson(json, EventMessage.class);

            assertThat(event.hasOptionalNote()).isTrue();
            assertThat(event.getOptionalNote().getValue()).isEqualTo("Parsed note");
            assertThat(event.hasOptionalCount()).isTrue();
            assertThat(event.getOptionalCount().getValue()).isEqualTo(123);
        }

        @Test
        @DisplayName("Should handle null wrapper values")
        void shouldHandleNullWrapperValues() {
            String json = """
                    {
                        "eventId": "evt-null-wrapper",
                        "optionalNote": null,
                        "optionalCount": null
                    }
                    """;

            EventMessage event = unmarshalFromJson(json, EventMessage.class);

            assertThat(event.hasOptionalNote()).isFalse();
            assertThat(event.hasOptionalCount()).isFalse();
        }
    }

    @Nested
    @DisplayName("Any Type Tests")
    class AnyTypeTests {

        @Test
        @DisplayName("Should marshal Any with known type")
        void shouldMarshalAnyWithKnownType() throws Exception {
            SimpleUser user = SimpleUser.newBuilder()
                    .setName("Packed User")
                    .setAge(30)
                    .build();

            EventMessage event = EventMessage.newBuilder()
                    .setEventId("evt-any")
                    .setPayload(Any.pack(user))
                    .build();

            String json = marshalToJson(event);

            assertThat(json).contains("\"payload\"");
            assertThat(json).contains("@type");
            assertThat(json).contains("SimpleUser");
            assertThat(json).contains("\"name\"");
            assertThat(json).contains("Packed User");
            assertThat(json).contains("\"age\"");
            assertThat(json).contains("30");
        }

        @Test
        @DisplayName("Should unmarshal Any with @type")
        void shouldUnmarshalAnyWithType() throws Exception {
            String json = """
                    {
                        "eventId": "evt-parse-any",
                        "payload": {
                            "@type": "type.googleapis.com/org.apache.camel.component.protojson.test.SimpleUser",
                                                          
                            "name": "Unpacked User",
                            "age": 25
                        }
                    }
                    """;

            EventMessage event = unmarshalFromJson(json, EventMessage.class);

            assertThat(event.hasPayload()).isTrue();

            Any payload = event.getPayload();
            assertThat(payload.getTypeUrl()).contains("SimpleUser");

            SimpleUser user = payload.unpack(SimpleUser.class);
            assertThat(user.getName()).isEqualTo("Unpacked User");
            assertThat(user.getAge()).isEqualTo(25);
        }

        @Test
        @DisplayName("Should round-trip Any")
        void shouldRoundTripAny() throws Exception {
            SimpleUser user = SimpleUser.newBuilder()
                    .setName("Round Trip User")
                    .setAge(42)
                    .build();

            EventMessage original = EventMessage.newBuilder()
                    .setEventId("evt-any-rt")
                    .setPayload(Any.pack(user))
                    .build();

            EventMessage result = roundTrip(original);

            assertThat(result.hasPayload()).isTrue();
            SimpleUser unpacked = result.getPayload().unpack(SimpleUser.class);
            assertThat(unpacked.getName()).isEqualTo("Round Trip User");
            assertThat(unpacked.getAge()).isEqualTo(42);
        }
    }
}