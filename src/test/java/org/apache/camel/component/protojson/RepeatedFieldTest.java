// src/test/java/org/apache/camel/component/protojson/RepeatedFieldTest.java

package org.apache.camel.component.protojson;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.protojson.test.proto.Address;
import org.apache.camel.component.protojson.test.proto.UserStatus;
import org.apache.camel.component.protojson.test.proto.UserWithTags;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for repeated field handling.
 */
@DisplayName("Repeated Field Tests")
class RepeatedFieldTest extends BaseProtoJsonTest {

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                ProtoJsonDataFormat protoJson = new ProtoJsonDataFormat(UserWithTags.class);

                from("direct:marshal")
                        .marshal(protoJson)
                        .convertBodyTo(String.class);

                from("direct:unmarshal")
                        .unmarshal(protoJson);
            }
        };
    }

    @Nested
    @DisplayName("Marshal Repeated Tests")
    class MarshalRepeatedTests {

        @Test
        @DisplayName("Should marshal repeated strings")
        void shouldMarshalRepeatedStrings() throws Exception {
            // Given
            UserWithTags user = UserWithTags.newBuilder()
                    .setName("Test")
                    .addTags("java")
                    .addTags("camel")
                    .addTags("protobuf")
                    .build();

            // When
            String json = marshalToJson(user);

            // Then
            assertThat(json).contains("\"tags\":[\"java\",\"camel\",\"protobuf\"]");
        }

        @Test
        @DisplayName("Should marshal repeated integers")
        void shouldMarshalRepeatedIntegers() throws Exception {
            // Given
            UserWithTags user = UserWithTags.newBuilder()
                    .setName("Test")
                    .addScores(100)
                    .addScores(95)
                    .addScores(88)
                    .build();

            // When
            String json = marshalToJson(user);

            // Then
            assertThat(json).contains("\"scores\":[100,95,88]");
        }

        @Test
        @DisplayName("Should marshal repeated messages")
        void shouldMarshalRepeatedMessages() throws Exception {
            // Given
            UserWithTags user = UserWithTags.newBuilder()
                    .setName("Test")
                    .addAddresses(Address.newBuilder()
                            .setCity("New York")
                            .build())
                    .addAddresses(Address.newBuilder()
                            .setCity("Los Angeles")
                            .build())
                    .build();

            // When
            String json = marshalToJson(user);

            // Then
            assertThat(json).contains("\"addresses\"");
            assertThat(json).contains("\"city\":\"New York\"");
            assertThat(json).contains("\"city\":\"Los Angeles\"");
        }

        @Test
        @DisplayName("Should marshal repeated enums")
        void shouldMarshalRepeatedEnums() throws Exception {
            // Given
            UserWithTags user = UserWithTags.newBuilder()
                    .setName("Test")
                    .addStatuses(UserStatus.ACTIVE)
                    .addStatuses(UserStatus.INACTIVE)
                    .build();

            // When
            String json = marshalToJson(user);

            // Then
            assertThat(json).contains("\"statuses\":[\"ACTIVE\",\"INACTIVE\"]");
        }

        @Test
        @DisplayName("Should not include empty repeated fields")
        void shouldNotIncludeEmptyRepeatedFields() throws Exception {
            // Given
            UserWithTags user = UserWithTags.newBuilder()
                    .setName("Test")
                    .build();

            // When
            String json = marshalToJson(user);

            // Then
            assertThat(json).doesNotContain("\"tags\"");
            assertThat(json).doesNotContain("\"scores\"");
        }
    }

    @Nested
    @DisplayName("Unmarshal Repeated Tests")
    class UnmarshalRepeatedTests {

        @Test
        @DisplayName("Should unmarshal repeated strings")
        void shouldUnmarshalRepeatedStrings() {
            // Given
            String json = """
                    {
                        "name": "Test",
                        "tags": ["spring", "boot", "camel"]
                    }
                    """;

            // When
            UserWithTags user = unmarshalFromJson(json, UserWithTags.class);

            // Then
            assertThat(user.getTagsList()).containsExactly("spring", "boot", "camel");
        }

        @Test
        @DisplayName("Should unmarshal repeated integers")
        void shouldUnmarshalRepeatedIntegers() {
            // Given
            String json = """
                    {
                        "name": "Test",
                        "scores": [10, 20, 30]
                    }
                    """;

            // When
            UserWithTags user = unmarshalFromJson(json, UserWithTags.class);

            // Then
            assertThat(user.getScoresList()).containsExactly(10, 20, 30);
        }

        @Test
        @DisplayName("Should unmarshal repeated messages")
        void shouldUnmarshalRepeatedMessages() {
            // Given
            String json = """
                    {
                        "name": "Test",
                        "addresses": [
                            {"city": "Boston"},
                            {"city": "Chicago"}
                        ]
                    }
                    """;

            // When
            UserWithTags user = unmarshalFromJson(json, UserWithTags.class);

            // Then
            assertThat(user.getAddressesList()).hasSize(2);
            assertThat(user.getAddresses(0).getCity()).isEqualTo("Boston");
            assertThat(user.getAddresses(1).getCity()).isEqualTo("Chicago");
        }

        @Test
        @DisplayName("Should unmarshal repeated enums from strings")
        void shouldUnmarshalRepeatedEnumsFromStrings() {
            // Given
            String json = """
                    {
                        "name": "Test",
                        "statuses": ["ACTIVE", "SUSPENDED"]
                    }
                    """;

            // When
            UserWithTags user = unmarshalFromJson(json, UserWithTags.class);

            // Then
            assertThat(user.getStatusesList())
                    .containsExactly(UserStatus.ACTIVE, UserStatus.SUSPENDED);
        }

        @Test
        @DisplayName("Should unmarshal repeated enums from integers")
        void shouldUnmarshalRepeatedEnumsFromIntegers() {
            // Given
            String json = """
                    {
                        "name": "Test",
                        "statuses": [1, 2, 3]
                    }
                    """;

            // When
            UserWithTags user = unmarshalFromJson(json, UserWithTags.class);

            // Then
            assertThat(user.getStatusesList())
                    .containsExactly(UserStatus.ACTIVE, UserStatus.INACTIVE, UserStatus.SUSPENDED);
        }

        @Test
        @DisplayName("Should handle single value as array")
        void shouldHandleSingleValueAsArray() {
            // Given
            String json = """
                    {
                        "name": "Test",
                        "tags": "single-tag"
                    }
                    """;

            // When
            UserWithTags user = unmarshalFromJson(json, UserWithTags.class);

            // Then
            assertThat(user.getTagsList()).containsExactly("single-tag");
        }

        @Test
        @DisplayName("Should handle empty array")
        void shouldHandleEmptyArray() {
            // Given
            String json = """
                    {
                        "name": "Test",
                        "tags": []
                    }
                    """;

            // When
            UserWithTags user = unmarshalFromJson(json, UserWithTags.class);

            // Then
            assertThat(user.getTagsList()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Round-Trip Repeated Tests")
    class RoundTripRepeatedTests {

        @Test
        @DisplayName("Should round-trip all repeated field types")
        void shouldRoundTripAllRepeatedFieldTypes() {
            // Given
            UserWithTags original = UserWithTags.newBuilder()
                    .setName("Complete")
                    .addAllTags(List.of("a", "b", "c"))
                    .addAllScores(List.of(1, 2, 3))
                    .addAddresses(Address.newBuilder().setCity("City1").build())
                    .addAddresses(Address.newBuilder().setCity("City2").build())
                    .addStatuses(UserStatus.ACTIVE)
                    .addStatuses(UserStatus.INACTIVE)
                    .build();

            // When
            UserWithTags result = roundTrip(original);

            // Then
            assertThat(result).isEqualTo(original);
        }
    }
}