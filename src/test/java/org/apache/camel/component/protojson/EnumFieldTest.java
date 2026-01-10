// src/test/java/org/apache/camel/component/protojson/EnumFieldTest.java

package org.apache.camel.component.protojson;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.protojson.test.proto.UserStatus;
import org.apache.camel.component.protojson.test.proto.UserWithStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for enum field handling.
 */
@DisplayName("Enum Field Tests")
class EnumFieldTest extends BaseProtoJsonTest {

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                ProtoJsonDataFormat protoJson = new ProtoJsonDataFormat(UserWithStatus.class);

                from("direct:marshal")
                        .marshal(protoJson)
                        .convertBodyTo(String.class);

                from("direct:unmarshal")
                        .unmarshal(protoJson);

                // Route with numeric enum output
                ProtoJsonDataFormat numericEnumFormat = new ProtoJsonDataFormat(UserWithStatus.class);
                numericEnumFormat.setPrintingEnumsAsInts(true);

                from("direct:marshal-numeric")
                        .marshal(numericEnumFormat)
                        .convertBodyTo(String.class);
            }
        };
    }

    @Nested
    @DisplayName("Marshal Enum Tests")
    class MarshalEnumTests {

        @Test
        @DisplayName("Should marshal enum as string name")
        void shouldMarshalEnumAsString() throws Exception {
            // Given
            UserWithStatus user = UserWithStatus.newBuilder()
                    .setName("Test")
                    .setStatus(UserStatus.ACTIVE)
                    .build();

            // When
            String json = marshalToJson(user);

            // Then
            assertJsonContains(json, "status", "ACTIVE");
        }

        @Test
        @DisplayName("Should marshal enum as integer when configured")
        void shouldMarshalEnumAsInteger() throws Exception {
            // Given
            UserWithStatus user = UserWithStatus.newBuilder()
                    .setName("Test")
                    .setStatus(UserStatus.SUSPENDED)
                    .build();

            // When
            String json = producer.requestBody("direct:marshal-numeric", user, String.class);

            // Then
            assertJsonContains(json, "status", 3); // SUSPENDED = 3
        }

        @ParameterizedTest
        @EnumSource(UserStatus.class)
        @DisplayName("Should marshal all enum values")
        void shouldMarshalAllEnumValues(UserStatus status) throws Exception {
            // Given
            UserWithStatus user = UserWithStatus.newBuilder()
                    .setName("Test")
                    .setStatus(status)
                    .build();

            // When
            String json = marshalToJson(user);

            // Then
            assertJsonContains(json, "status", status.name());
        }
    }

    @Nested
    @DisplayName("Unmarshal Enum Tests")
    class UnmarshalEnumTests {

        @Test
        @DisplayName("Should unmarshal enum from string name")
        void shouldUnmarshalEnumFromString() {
            // Given
            String json = """
                    {
                        "name": "Test",
                        "status": "ACTIVE"
                    }
                    """;

            // When
            UserWithStatus user = unmarshalFromJson(json, UserWithStatus.class);

            // Then
            assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        }

        @Test
        @DisplayName("Should unmarshal enum from integer")
        void shouldUnmarshalEnumFromInteger() {
            // Given
            String json = """
                    {
                        "name": "Test",
                        "status": 2
                    }
                    """;

            // When
            UserWithStatus user = unmarshalFromJson(json, UserWithStatus.class);

            // Then
            assertThat(user.getStatus()).isEqualTo(UserStatus.INACTIVE);
        }

        @ParameterizedTest
        @EnumSource(UserStatus.class)
        @DisplayName("Should round-trip all enum values")
        void shouldRoundTripAllEnumValues(UserStatus status) {
            // Given
            UserWithStatus original = UserWithStatus.newBuilder()
                    .setName("Test")
                    .setStatus(status)
                    .build();

            // When
            UserWithStatus result = roundTrip(original);

            // Then
            assertThat(result.getStatus()).isEqualTo(status);
        }
    }
}