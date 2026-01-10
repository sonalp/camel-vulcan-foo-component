// src/test/java/org/apache/camel/component/protojson/SimpleMessageTest.java

package org.apache.camel.component.protojson;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.protojson.test.proto.SimpleUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for simple message marshal/unmarshal.
 */
@DisplayName("Simple Message Tests")
class SimpleMessageTest extends BaseProtoJsonTest {

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                ProtoJsonDataFormat protoJson = new ProtoJsonDataFormat(SimpleUser.class);

                from("direct:marshal")
                        .marshal(protoJson)
                        .convertBodyTo(String.class);

                from("direct:unmarshal")
                        .unmarshal(protoJson);
            }
        };
    }

    @Nested
    @DisplayName("Marshal Tests")
    class MarshalTests {

        @Test
        @DisplayName("Should marshal simple user to JSON")
        void shouldMarshalSimpleUser() throws Exception {
            // Given
            SimpleUser user = SimpleUser.newBuilder()
                    .setName("John Doe")
                    .setAge(30)
                    .setEmail("john@example.com")
                    .setActive(true)
                    .build();

            // When
            String json = marshalToJson(user);

            // Then
            assertJsonContains(json, "name", "John Doe");
            assertJsonContains(json, "age", 30);
            assertJsonContains(json, "email", "john@example.com");
            assertJsonContains(json, "active", true);
        }

        @Test
        @DisplayName("Should use JSON field names by default")
        void shouldUseJsonFieldNames() throws Exception {
            // Given
            SimpleUser user = SimpleUser.newBuilder()
                    .setName("Test")
                    .build();

            // When
            String json = marshalToJson(user);

            // Then - should use camelCase (JSON names)
            assertThat(json).contains("\"name\"");
        }

        @Test
        @DisplayName("Should not include default values by default")
        void shouldNotIncludeDefaultValues() throws Exception {
            // Given
            SimpleUser user = SimpleUser.newBuilder()
                    .setName("Test")
                    .build();

            // When
            String json = marshalToJson(user);

            // Then - age=0, active=false should not be in JSON
            assertThat(json).doesNotContain("\"age\"");
            assertThat(json).doesNotContain("\"active\"");
        }
    }

    @Nested
    @DisplayName("Unmarshal Tests")
    class UnmarshalTests {

        @Test
        @DisplayName("Should unmarshal JSON to simple user")
        void shouldUnmarshalSimpleUser() {
            // Given
            String json = """
                    {
                        "name": "Jane Doe",
                        "age": 25,
                        "email": "jane@example.com",
                        "active": true
                    }
                    """;

            // When
            SimpleUser user = unmarshalFromJson(json, SimpleUser.class);

            // Then
            assertThat(user.getName()).isEqualTo("Jane Doe");
            assertThat(user.getAge()).isEqualTo(25);
            assertThat(user.getEmail()).isEqualTo("jane@example.com");
            assertThat(user.getActive()).isTrue();
        }

        @Test
        @DisplayName("Should handle missing fields with defaults")
        void shouldHandleMissingFields() {
            // Given
            String json = """
                    {
                        "name": "Minimal"
                    }
                    """;

            // When
            SimpleUser user = unmarshalFromJson(json, SimpleUser.class);

            // Then
            assertThat(user.getName()).isEqualTo("Minimal");
            assertThat(user.getAge()).isEqualTo(0); // default
            assertThat(user.getEmail()).isEmpty(); // default
            assertThat(user.getActive()).isFalse(); // default
        }

        @Test
        @DisplayName("Should handle null values for scalars")
        void shouldHandleNullValues() {
            // Given
            String json = """
                    {
                        "name": "Test",
                        "age": null,
                        "email": null
                    }
                    """;

            // When
            SimpleUser user = unmarshalFromJson(json, SimpleUser.class);

            // Then
            assertThat(user.getName()).isEqualTo("Test");
            assertThat(user.getAge()).isEqualTo(0);
            assertThat(user.getEmail()).isEmpty();
        }

        @Test
        @DisplayName("Should parse string numbers")
        void shouldParseStringNumbers() {
            // Given
            String json = """
                    {
                        "name": "Test",
                        "age": "42"
                    }
                    """;

            // When
            SimpleUser user = unmarshalFromJson(json, SimpleUser.class);

            // Then
            assertThat(user.getAge()).isEqualTo(42);
        }
    }

    @Nested
    @DisplayName("Round-Trip Tests")
    class RoundTripTests {

        @Test
        @DisplayName("Should round-trip simple user")
        void shouldRoundTripSimpleUser() {
            // Given
            SimpleUser original = SimpleUser.newBuilder()
                    .setName("Round Trip")
                    .setAge(99)
                    .setEmail("rt@test.com")
                    .setActive(true)
                    .build();

            // When
            SimpleUser result = roundTrip(original);

            // Then
            assertThat(result).isEqualTo(original);
        }
    }
}