package org.apache.camel.component.protojson;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.protojson.engine.ProtoJsonException;
import org.apache.camel.component.protojson.test.proto.SimpleUser;
import org.apache.camel.component.protojson.test.proto.UserWithStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for error handling scenarios.
 */
@DisplayName("Error Handling Tests")
class ErrorHandlingTest extends BaseProtoJsonTest {

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                ProtoJsonDataFormat strictFormat = new ProtoJsonDataFormat(SimpleUser.class);
                strictFormat.setIgnoringUnknownFields(false);

                from("direct:marshal")
                        .marshal(strictFormat)
                        .convertBodyTo(String.class);

                from("direct:unmarshal")
                        .unmarshal(strictFormat);

                ProtoJsonDataFormat enumFormat = new ProtoJsonDataFormat(UserWithStatus.class);

                from("direct:unmarshal-enum")
                        .unmarshal(enumFormat);
            }
        };
    }

    @Nested
    @DisplayName("Invalid JSON Tests")
    class InvalidJsonTests {

        @Test
        @DisplayName("Should throw on malformed JSON")
        void shouldThrowOnMalformedJson() {
            String malformedJson = "{ invalid json }";

            assertThatThrownBy(() ->
                    producer.requestBody("direct:unmarshal", malformedJson, SimpleUser.class))
                    .isInstanceOf(CamelExecutionException.class);
        }

        @Test
        @DisplayName("Should throw on JSON array instead of object")
        void shouldThrowOnJsonArray() {
            String jsonArray = "[1, 2, 3]";

            assertThatThrownBy(() ->
                    producer.requestBody("direct:unmarshal", jsonArray, SimpleUser.class))
                    .isInstanceOf(CamelExecutionException.class)
                    .cause()
                    .isInstanceOf(ProtoJsonException.class)
                    .hasMessageContaining("START_OBJECT");
        }

        @Test
        @DisplayName("Should throw on scalar JSON")
        void shouldThrowOnScalarJson() {
            String scalarJson = "\"just a string\"";

            assertThatThrownBy(() ->
                    producer.requestBody("direct:unmarshal", scalarJson, SimpleUser.class))
                    .isInstanceOf(CamelExecutionException.class);
        }
    }

    @Nested
    @DisplayName("Type Mismatch Tests")
    class TypeMismatchTests {

        @Test
        @DisplayName("Should throw on wrong type for int field")
        void shouldThrowOnWrongTypeForIntField() {
            String json = """
                    {
                        "name": "Test",
                        "age": "not a number"
                    }
                    """;

            assertThatThrownBy(() ->
                    producer.requestBody("direct:unmarshal", json, SimpleUser.class))
                    .isInstanceOf(CamelExecutionException.class);
        }

        @Test
        @DisplayName("Should throw on invalid enum value")
        void shouldThrowOnInvalidEnumValue() {
            String json = """
                    {
                        "name": "Test",
                        "status": "INVALID_STATUS"
                    }
                    """;

            assertThatThrownBy(() ->
                    producer.requestBody("direct:unmarshal-enum", json, UserWithStatus.class))
                    .isInstanceOf(CamelExecutionException.class)
                    .cause()
                    .isInstanceOf(ProtoJsonException.class)
                    .hasMessageContaining("enum");
        }

        @Test
        @DisplayName("Should throw on invalid numeric enum value")
        void shouldThrowOnInvalidNumericEnumValue() {
            String json = """
                    {
                        "name": "Test",
                        "status": 999
                    }
                    """;

            assertThatThrownBy(() ->
                    producer.requestBody("direct:unmarshal-enum", json, UserWithStatus.class))
                    .isInstanceOf(CamelExecutionException.class)
                    .cause()
                    .isInstanceOf(ProtoJsonException.class)
                    .hasMessageContaining("enum");
        }
    }

    @Nested
    @DisplayName("Unknown Fields Tests")
    class UnknownFieldsTests {

        @Test
        @DisplayName("Should throw on unknown field in strict mode")
        void shouldThrowOnUnknownField() {
            String json = """
                    {
                        "name": "Test",
                        "unknownField": "value"
                    }
                    """;

            assertThatThrownBy(() ->
                    producer.requestBody("direct:unmarshal", json, SimpleUser.class))
                    .isInstanceOf(CamelExecutionException.class)
                    .cause()
                    .isInstanceOf(ProtoJsonException.class)
                    .hasMessageContaining("Unknown field");
        }
    }

    @Nested
    @DisplayName("Marshal Error Tests")
    class MarshalErrorTests {

        @Test
        @DisplayName("Should throw when marshalling non-Message object")
        void shouldThrowWhenMarshallingNonMessage() {
            String notAMessage = "I am not a protobuf message";

            assertThatThrownBy(() ->
                    producer.requestBody("direct:marshal", notAMessage, String.class))
                    .isInstanceOf(CamelExecutionException.class)
                    .hasMessageContaining("Protobuf Message");
        }
    }
}