// src/test/java/org/apache/camel/component/protojson/EnumFieldTest.java

package org.apache.camel.component.protojson;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.protojson.test.proto.UserStatus;
import org.apache.camel.component.protojson.test.proto.UserWithStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for enum field handling in ProtoJson data format.
 * Tests marshalling and unmarshalling of enum fields with various configurations.
 */
@DisplayName("Enum Field Tests")
class EnumFieldTest extends BaseProtoJsonTest {

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // Standard route with default settings
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

                // Route with numeric enum input enabled
                ProtoJsonDataFormat numericEnumInputFormat = new ProtoJsonDataFormat(UserWithStatus.class);
                numericEnumInputFormat.setAcceptNumericEnums(true);

                from("direct:unmarshal-numeric")
                        .unmarshal(numericEnumInputFormat);
            }
        };
    }

    @Nested
    @DisplayName("Marshal Enum Tests")
    class MarshalEnumTests {

        @Test
        @DisplayName("Should marshal enum as string name (ACTIVE)")
        void shouldMarshalActiveEnumAsString() throws Exception {
            // Given
            UserWithStatus user = UserWithStatus.newBuilder()
                    .setName("Test User")
                    .setStatus(UserStatus.ACTIVE)
                    .build();

            // When
            String json = marshalToJson(user);

            // Then
            assertJsonContains(json, "status", "ACTIVE");
            assertJsonContains(json, "name", "Test User");
        }

        @Test
        @DisplayName("Should marshal enum as string name (INACTIVE)")
        void shouldMarshalInactiveEnumAsString() throws Exception {
            // Given
            UserWithStatus user = UserWithStatus.newBuilder()
                    .setName("Test User")
                    .setStatus(UserStatus.INACTIVE)
                    .build();

            // When
            String json = marshalToJson(user);

            // Then
            assertJsonContains(json, "status", "INACTIVE");
        }

        @Test
        @DisplayName("Should marshal enum as string name (SUSPENDED)")
        void shouldMarshalSuspendedEnumAsString() throws Exception {
            // Given
            UserWithStatus user = UserWithStatus.newBuilder()
                    .setName("Test User")
                    .setStatus(UserStatus.SUSPENDED)
                    .build();

            // When
            String json = marshalToJson(user);

            // Then
            assertJsonContains(json, "status", "SUSPENDED");
        }

        @Test
        @DisplayName("Should marshal enum as string name (UNKNOWN)")
        void shouldMarshalUnknownEnumAsString() throws Exception {
            // Given
            UserWithStatus user = UserWithStatus.newBuilder()
                    .setName("Test User")
                    .setStatus(UserStatus.UNKNOWN)
                    .build();

            // When
            String json = marshalToJson(user);

            // Then
            assertJsonContains(json, "status", "UNKNOWN");
        }

        @Test
        @DisplayName("Should marshal enum as integer when configured (ACTIVE=1)")
        void shouldMarshalActiveEnumAsInteger() throws Exception {
            // Given
            UserWithStatus user = UserWithStatus.newBuilder()
                    .setName("Test User")
                    .setStatus(UserStatus.ACTIVE)
                    .build();

            // When
            String json = producer.requestBody("direct:marshal-numeric", user, String.class);

            // Then
            assertJsonContains(json, "status", 1); // ACTIVE = 1
        }

        @Test
        @DisplayName("Should marshal enum as integer when configured (INACTIVE=2)")
        void shouldMarshalInactiveEnumAsInteger() throws Exception {
            // Given
            UserWithStatus user = UserWithStatus.newBuilder()
                    .setName("Test User")
                    .setStatus(UserStatus.INACTIVE)
                    .build();

            // When
            String json = producer.requestBody("direct:marshal-numeric", user, String.class);

            // Then
            assertJsonContains(json, "status", 2); // INACTIVE = 2
        }

        @Test
        @DisplayName("Should marshal enum as integer when configured (SUSPENDED=3)")
        void shouldMarshalSuspendedEnumAsInteger() throws Exception {
            // Given
            UserWithStatus user = UserWithStatus.newBuilder()
                    .setName("Test User")
                    .setStatus(UserStatus.SUSPENDED)
                    .build();

            // When
            String json = producer.requestBody("direct:marshal-numeric", user, String.class);

            // Then
            assertJsonContains(json, "status", 3); // SUSPENDED = 3
        }

        @Test
        @DisplayName("Should marshal enum as integer when configured (UNKNOWN=0)")
        void shouldMarshalUnknownEnumAsInteger() throws Exception {
            // Given
            UserWithStatus user = UserWithStatus.newBuilder()
                    .setName("Test User")
                    .setStatus(UserStatus.UNKNOWN)
                    .build();

            // When
            String json = producer.requestBody("direct:marshal-numeric", user, String.class);

            // Then
            assertJsonContains(json, "status", 0); // UNKNOWN = 0
        }
    }

    @Nested
    @DisplayName("Unmarshal Enum Tests")
    class UnmarshalEnumTests {

        @Test
        @DisplayName("Should unmarshal enum from string name (ACTIVE)")
        void shouldUnmarshalActiveEnumFromString() {
            // Given
            String json = """
                    {
                        "name": "Test User",
                        "status": "ACTIVE"
                    }
                    """;

            // When
            UserWithStatus user = unmarshalFromJson(json, UserWithStatus.class);

            // Then
            assertThat(user.getName()).isEqualTo("Test User");
            assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        }

        @Test
        @DisplayName("Should unmarshal enum from string name (INACTIVE)")
        void shouldUnmarshalInactiveEnumFromString() {
            // Given
            String json = """
                    {
                        "name": "Test User",
                        "status": "INACTIVE"
                    }
                    """;

            // When
            UserWithStatus user = unmarshalFromJson(json, UserWithStatus.class);

            // Then
            assertThat(user.getStatus()).isEqualTo(UserStatus.INACTIVE);
        }

        @Test
        @DisplayName("Should unmarshal enum from string name (SUSPENDED)")
        void shouldUnmarshalSuspendedEnumFromString() {
            // Given
            String json = """
                    {
                        "name": "Test User",
                        "status": "SUSPENDED"
                    }
                    """;

            // When
            UserWithStatus user = unmarshalFromJson(json, UserWithStatus.class);

            // Then
            assertThat(user.getStatus()).isEqualTo(UserStatus.SUSPENDED);
        }

        @Test
        @DisplayName("Should unmarshal enum from string name (UNKNOWN)")
        void shouldUnmarshalUnknownEnumFromString() {
            // Given
            String json = """
                    {
                        "name": "Test User",
                        "status": "UNKNOWN"
                    }
                    """;

            // When
            UserWithStatus user = unmarshalFromJson(json, UserWithStatus.class);

            // Then
            assertThat(user.getStatus()).isEqualTo(UserStatus.UNKNOWN);
        }

        @Test
        @DisplayName("Should unmarshal enum from integer (ACTIVE=1)")
        void shouldUnmarshalActiveEnumFromInteger() {
            // Given
            String json = """
                    {
                        "name": "Test User",
                        "status": 1
                    }
                    """;

            // When
            UserWithStatus user = producer.requestBodyAndHeader(
                    "direct:unmarshal-numeric",
                    json,
                    "CamelProtoJsonClass",
                    UserWithStatus.class.getName(),
                    UserWithStatus.class
            );

            // Then
            assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        }

        @Test
        @DisplayName("Should unmarshal enum from integer (INACTIVE=2)")
        void shouldUnmarshalInactiveEnumFromInteger() {
            // Given
            String json = """
                    {
                        "name": "Test User",
                        "status": 2
                    }
                    """;

            // When
            UserWithStatus user = producer.requestBodyAndHeader(
                    "direct:unmarshal-numeric",
                    json,
                    "CamelProtoJsonClass",
                    UserWithStatus.class.getName(),
                    UserWithStatus.class
            );

            // Then
            assertThat(user.getStatus()).isEqualTo(UserStatus.INACTIVE);
        }

        @Test
        @DisplayName("Should unmarshal enum from integer (SUSPENDED=3)")
        void shouldUnmarshalSuspendedEnumFromInteger() {
            // Given
            String json = """
                    {
                        "name": "Test User",
                        "status": 3
                    }
                    """;

            // When
            UserWithStatus user = producer.requestBodyAndHeader(
                    "direct:unmarshal-numeric",
                    json,
                    "CamelProtoJsonClass",
                    UserWithStatus.class.getName(),
                    UserWithStatus.class
            );

            // Then
            assertThat(user.getStatus()).isEqualTo(UserStatus.SUSPENDED);
        }

        @Test
        @DisplayName("Should unmarshal enum from integer (UNKNOWN=0)")
        void shouldUnmarshalUnknownEnumFromInteger() {
            // Given
            String json = """
                    {
                        "name": "Test User",
                        "status": 0
                    }
                    """;

            // When
            UserWithStatus user = producer.requestBodyAndHeader(
                    "direct:unmarshal-numeric",
                    json,
                    "CamelProtoJsonClass",
                    UserWithStatus.class.getName(),
                    UserWithStatus.class
            );

            // Then
            assertThat(user.getStatus()).isEqualTo(UserStatus.UNKNOWN);
        }

        @Test
        @DisplayName("Should default to UNKNOWN when status is missing")
        void shouldDefaultToUnknownWhenMissing() {
            // Given
            String json = """
                    {
                        "name": "Test User"
                    }
                    """;

            // When
            UserWithStatus user = unmarshalFromJson(json, UserWithStatus.class);

            // Then
            assertThat(user.getStatus()).isEqualTo(UserStatus.UNKNOWN);
        }
    }

    @Nested
    @DisplayName("Round-trip Tests")
    class RoundTripTests {

        @Test
        @DisplayName("Should round-trip ACTIVE enum")
        void shouldRoundTripActiveEnum() {
            // Given
            UserWithStatus original = UserWithStatus.newBuilder()
                    .setName("Test User")
                    .setStatus(UserStatus.ACTIVE)
                    .build();

            // When
            UserWithStatus result = roundTrip(original);

            // Then
            assertThat(result.getName()).isEqualTo(original.getName());
            assertThat(result.getStatus()).isEqualTo(UserStatus.ACTIVE);
        }

        @Test
        @DisplayName("Should round-trip INACTIVE enum")
        void shouldRoundTripInactiveEnum() {
            // Given
            UserWithStatus original = UserWithStatus.newBuilder()
                    .setName("Test User")
                    .setStatus(UserStatus.INACTIVE)
                    .build();

            // When
            UserWithStatus result = roundTrip(original);

            // Then
            assertThat(result.getStatus()).isEqualTo(UserStatus.INACTIVE);
        }

        @Test
        @DisplayName("Should round-trip SUSPENDED enum")
        void shouldRoundTripSuspendedEnum() {
            // Given
            UserWithStatus original = UserWithStatus.newBuilder()
                    .setName("Test User")
                    .setStatus(UserStatus.SUSPENDED)
                    .build();

            // When
            UserWithStatus result = roundTrip(original);

            // Then
            assertThat(result.getStatus()).isEqualTo(UserStatus.SUSPENDED);
        }

        @Test
        @DisplayName("Should round-trip UNKNOWN enum")
        void shouldRoundTripUnknownEnum() {
            // Given
            UserWithStatus original = UserWithStatus.newBuilder()
                    .setName("Test User")
                    .setStatus(UserStatus.UNKNOWN)
                    .build();

            // When
            UserWithStatus result = roundTrip(original);

            // Then
            assertThat(result.getStatus()).isEqualTo(UserStatus.UNKNOWN);
        }
    }

    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Should throw exception for invalid enum name")
        void shouldThrowExceptionForInvalidEnumName() {
            // Given
            String json = """
                    {
                        "name": "Test User",
                        "status": "INVALID_STATUS"
                    }
                    """;

            // When/Then
            assertThatThrownBy(() -> unmarshalFromJson(json, UserWithStatus.class))
                    .isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("Should throw exception for invalid enum number")
        void shouldThrowExceptionForInvalidEnumNumber() {
            // Given
            String json = """
                    {
                        "name": "Test User",
                        "status": 999
                    }
                    """;

            // When/Then
            assertThatThrownBy(() -> producer.requestBodyAndHeader(
                    "direct:unmarshal-numeric",
                    json,
                    "CamelProtoJsonClass",
                    UserWithStatus.class.getName(),
                    UserWithStatus.class
            )).isInstanceOf(Exception.class);
        }
    }
}
