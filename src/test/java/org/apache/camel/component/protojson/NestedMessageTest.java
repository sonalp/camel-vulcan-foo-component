// src/test/java/org/apache/camel/component/protojson/NestedMessageTest.java

package org.apache.camel.component.protojson;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.protojson.test.proto.Address;
import org.apache.camel.component.protojson.test.proto.UserWithAddress;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for nested message handling.
 */
@DisplayName("Nested Message Tests")
class NestedMessageTest extends BaseProtoJsonTest {

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                ProtoJsonDataFormat protoJson = new ProtoJsonDataFormat(UserWithAddress.class);

                from("direct:marshal")
                        .marshal(protoJson)
                        .convertBodyTo(String.class);

                from("direct:unmarshal")
                        .unmarshal(protoJson);
            }
        };
    }

    @Nested
    @DisplayName("Marshal Nested Tests")
    class MarshalNestedTests {

        @Test
        @DisplayName("Should marshal nested message")
        void shouldMarshalNestedMessage() throws Exception {
            // Given
            Address address = Address.newBuilder()
                    .setStreet("123 Main St")
                    .setCity("Springfield")
                    .setCountry("USA")
                    .setZipCode(12345)
                    .build();

            UserWithAddress user = UserWithAddress.newBuilder()
                    .setName("John")
                    .setAddress(address)
                    .build();

            // When
            String json = marshalToJson(user);

            // Then
            assertThat(json).contains("\"address\"");
            assertThat(json).contains("\"street\":\"123 Main St\"");
            assertThat(json).contains("\"city\":\"Springfield\"");
            assertThat(json).contains("\"zipCode\":12345");
        }

        @Test
        @DisplayName("Should handle null nested message")
        void shouldHandleNullNestedMessage() throws Exception {
            // Given
            UserWithAddress user = UserWithAddress.newBuilder()
                    .setName("John")
                    // no address set
                    .build();

            // When
            String json = marshalToJson(user);

            // Then
            assertThat(json).doesNotContain("\"address\"");
        }
    }

    @Nested
    @DisplayName("Unmarshal Nested Tests")
    class UnmarshalNestedTests {

        @Test
        @DisplayName("Should unmarshal nested message")
        void shouldUnmarshalNestedMessage() {
            // Given
            String json = """
                    {
                        "name": "Jane",
                        "address": {
                            "street": "456 Oak Ave",
                            "city": "Portland",
                            "country": "USA",
                            "zipCode": 97201
                        }
                    }
                    """;

            // When
            UserWithAddress user = unmarshalFromJson(json, UserWithAddress.class);

            // Then
            assertThat(user.getName()).isEqualTo("Jane");
            assertThat(user.hasAddress()).isTrue();
            assertThat(user.getAddress().getStreet()).isEqualTo("456 Oak Ave");
            assertThat(user.getAddress().getCity()).isEqualTo("Portland");
            assertThat(user.getAddress().getZipCode()).isEqualTo(97201);
        }

        @Test
        @DisplayName("Should handle missing nested message")
        void shouldHandleMissingNestedMessage() {
            // Given
            String json = """
                    {
                        "name": "Jane"
                    }
                    """;

            // When
            UserWithAddress user = unmarshalFromJson(json, UserWithAddress.class);

            // Then
            assertThat(user.getName()).isEqualTo("Jane");
            assertThat(user.hasAddress()).isFalse();
        }

        @Test
        @DisplayName("Should handle null nested message")
        void shouldHandleNullNestedMessage() {
            // Given
            String json = """
                    {
                        "name": "Jane",
                        "address": null
                    }
                    """;

            // When
            UserWithAddress user = unmarshalFromJson(json, UserWithAddress.class);

            // Then
            assertThat(user.getName()).isEqualTo("Jane");
            assertThat(user.hasAddress()).isFalse();
        }
    }

    @Nested
    @DisplayName("Round-Trip Nested Tests")
    class RoundTripNestedTests {

        @Test
        @DisplayName("Should round-trip nested message")
        void shouldRoundTripNestedMessage() {
            // Given
            Address address = Address.newBuilder()
                    .setStreet("789 Pine St")
                    .setCity("Seattle")
                    .setCountry("USA")
                    .setZipCode(98101)
                    .build();

            UserWithAddress original = UserWithAddress.newBuilder()
                    .setName("Bob")
                    .setAddress(address)
                    .build();

            // When
            UserWithAddress result = roundTrip(original);

            // Then
            assertThat(result).isEqualTo(original);
        }
    }
}