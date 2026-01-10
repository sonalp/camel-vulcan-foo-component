// src/test/java/org/apache/camel/component/protojson/MapFieldTest.java

package org.apache.camel.component.protojson;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.protojson.test.proto.Address;
import org.apache.camel.component.protojson.test.proto.UserWithMetadata;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for map field handling.
 */
@DisplayName("Map Field Tests")
class MapFieldTest extends BaseProtoJsonTest {

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                ProtoJsonDataFormat protoJson = new ProtoJsonDataFormat(UserWithMetadata.class);

                from("direct:marshal")
                        .marshal(protoJson)
                        .convertBodyTo(String.class);

                from("direct:unmarshal")
                        .unmarshal(protoJson);
            }
        };
    }

    @Nested
    @DisplayName("Marshal Map Tests")
    class MarshalMapTests {

        @Test
        @DisplayName("Should marshal string-string map")
        void shouldMarshalStringStringMap() throws Exception {
            // Given
            UserWithMetadata user = UserWithMetadata.newBuilder()
                    .setName("Test")
                    .putStringMeta("key1", "value1")
                    .putStringMeta("key2", "value2")
                    .build();

            // When
            String json = marshalToJson(user);

            // Then
            assertThat(json).contains("\"stringMeta\"");
            assertThat(json).contains("\"key1\":\"value1\"");
            assertThat(json).contains("\"key2\":\"value2\"");
        }

        @Test
        @DisplayName("Should marshal string-int map")
        void shouldMarshalStringIntMap() throws Exception {
            // Given
            UserWithMetadata user = UserWithMetadata.newBuilder()
                    .setName("Test")
                    .putIntMeta("count", 42)
                    .putIntMeta("score", 100)
                    .build();

            // When
            String json = marshalToJson(user);

            // Then
            assertThat(json).contains("\"intMeta\"");
            assertThat(json).contains("\"count\":42");
            assertThat(json).contains("\"score\":100");
        }

        @Test
        @DisplayName("Should marshal int-key map")
        void shouldMarshalIntKeyMap() throws Exception {
            // Given
            UserWithMetadata user = UserWithMetadata.newBuilder()
                    .setName("Test")
                    .putIntKeyMeta(1, "first")
                    .putIntKeyMeta(2, "second")
                    .build();

            // When
            String json = marshalToJson(user);

            // Then
            assertThat(json).contains("\"intKeyMeta\"");
            // JSON keys are always strings
            assertThat(json).contains("\"1\":\"first\"");
            assertThat(json).contains("\"2\":\"second\"");
        }

        @Test
        @DisplayName("Should marshal map with message values")
        void shouldMarshalMapWithMessageValues() throws Exception {
            // Given
            UserWithMetadata user = UserWithMetadata.newBuilder()
                    .setName("Test")
                    .putAddressMap("home", Address.newBuilder()
                            .setCity("New York")
                            .build())
                    .putAddressMap("work", Address.newBuilder()
                            .setCity("Boston")
                            .build())
                    .build();

            // When
            String json = marshalToJson(user);

            // Then
            assertThat(json).contains("\"addressMap\"");
            assertThat(json).contains("\"home\"");
            assertThat(json).contains("\"work\"");
            assertThat(json).contains("\"city\":\"New York\"");
            assertThat(json).contains("\"city\":\"Boston\"");
        }
    }

    @Nested
    @DisplayName("Unmarshal Map Tests")
    class UnmarshalMapTests {

        @Test
        @DisplayName("Should unmarshal string-string map")
        void shouldUnmarshalStringStringMap() {
            // Given
            String json = """
                    {
                        "name": "Test",
                        "stringMeta": {
                            "env": "production",
                            "version": "1.0.0"
                        }
                    }
                    """;

            // When
            UserWithMetadata user = unmarshalFromJson(json, UserWithMetadata.class);

            // Then
            assertThat(user.getStringMetaMap())
                    .containsEntry("env", "production")
                    .containsEntry("version", "1.0.0");
        }

        @Test
        @DisplayName("Should unmarshal string-int map")
        void shouldUnmarshalStringIntMap() {
            // Given
            String json = """
                    {
                        "name": "Test",
                        "intMeta": {
                            "retries": 3,
                            "timeout": 5000
                        }
                    }
                    """;

            // When
            UserWithMetadata user = unmarshalFromJson(json, UserWithMetadata.class);

            // Then
            assertThat(user.getIntMetaMap())
                    .containsEntry("retries", 3)
                    .containsEntry("timeout", 5000);
        }

        @Test
        @DisplayName("Should unmarshal int-key map")
        void shouldUnmarshalIntKeyMap() {
            // Given
            String json = """
                    {
                        "name": "Test",
                        "intKeyMeta": {
                            "100": "hundred",
                            "200": "two hundred"
                        }
                    }
                    """;

            // When
            UserWithMetadata user = unmarshalFromJson(json, UserWithMetadata.class);

            // Then
            assertThat(user.getIntKeyMetaMap())
                    .containsEntry(100, "hundred")
                    .containsEntry(200, "two hundred");
        }

        @Test
        @DisplayName("Should unmarshal map with message values")
        void shouldUnmarshalMapWithMessageValues() {
            // Given
            String json = """
                    {
                        "name": "Test",
                        "addressMap": {
                            "office": {
                                "street": "123 Business Rd",
                                "city": "San Francisco"
                            }
                        }
                    }
                    """;

            // When
            UserWithMetadata user = unmarshalFromJson(json, UserWithMetadata.class);

            // Then
            assertThat(user.getAddressMapMap()).containsKey("office");
            Address office = user.getAddressMapMap().get("office");
            assertThat(office.getStreet()).isEqualTo("123 Business Rd");
            assertThat(office.getCity()).isEqualTo("San Francisco");
        }

        @Test
        @DisplayName("Should handle empty map")
        void shouldHandleEmptyMap() {
            // Given
            String json = """
                    {
                        "name": "Test",
                        "stringMeta": {}
                    }
                    """;

            // When
            UserWithMetadata user = unmarshalFromJson(json, UserWithMetadata.class);

            // Then
            assertThat(user.getStringMetaMap()).isEmpty();
        }

        @Test
        @DisplayName("Should handle null map values")
        void shouldHandleNullMapValues() {
            // Given
            String json = """
                    {
                        "name": "Test",
                        "stringMeta": {
                            "valid": "value",
                            "nullValue": null
                        }
                    }
                    """;

            // When
            UserWithMetadata user = unmarshalFromJson(json, UserWithMetadata.class);

            // Then
            assertThat(user.getStringMetaMap())
                    .containsEntry("valid", "value")
                    .doesNotContainKey("nullValue"); // null entries skipped
        }
    }

    @Nested
    @DisplayName("Round-Trip Map Tests")
    class RoundTripMapTests {

        @Test
        @DisplayName("Should round-trip all map types")
        void shouldRoundTripAllMapTypes() {
            // Given
            UserWithMetadata original = UserWithMetadata.newBuilder()
                    .setName("Complete")
                    .putStringMeta("k1", "v1")
                    .putIntMeta("count", 99)
                    .putIntKeyMeta(42, "answer")
                    .putAddressMap("main", Address.newBuilder()
                            .setCity("Denver")
                            .setZipCode(80202)
                            .build())
                    .build();

            // When
            UserWithMetadata result = roundTrip(original);

            // Then
            assertThat(result).isEqualTo(original);
        }
    }
}