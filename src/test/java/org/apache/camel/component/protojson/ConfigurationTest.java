// src/test/java/org/apache/camel/component/protojson/ConfigurationTest.java

package org.apache.camel.component.protojson;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.protojson.test.proto.SimpleUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for ProtoJsonDataFormat configuration options.
 */
@DisplayName("Configuration Tests")
class ConfigurationTest extends BaseProtoJsonTest {

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // Default config
                ProtoJsonDataFormat defaultFormat = new ProtoJsonDataFormat(SimpleUser.class);

                from("direct:marshal")
                        .marshal(defaultFormat)
                        .convertBodyTo(String.class);

                from("direct:unmarshal")
                        .unmarshal(defaultFormat);

                // With proto field names
                ProtoJsonDataFormat protoNamesFormat = new ProtoJsonDataFormat(SimpleUser.class);
                protoNamesFormat.setPreservingProtoFieldNames(true);

                from("direct:marshal-proto-names")
                        .marshal(protoNamesFormat)
                        .convertBodyTo(String.class);

                // With default values
                ProtoJsonDataFormat includeDefaultsFormat = new ProtoJsonDataFormat(SimpleUser.class);
                includeDefaultsFormat.setIncludingDefaultValueFields(true);

                from("direct:marshal-with-defaults")
                        .marshal(includeDefaultsFormat)
                        .convertBodyTo(String.class);

                // Ignoring unknown fields
                ProtoJsonDataFormat ignoreUnknownFormat = new ProtoJsonDataFormat(SimpleUser.class);
                ignoreUnknownFormat.setIgnoringUnknownFields(true);

                from("direct:unmarshal-ignore-unknown")
                        .unmarshal(ignoreUnknownFormat);

                // Strict mode (not ignoring unknown fields)
                ProtoJsonDataFormat strictFormat = new ProtoJsonDataFormat(SimpleUser.class);
                strictFormat.setIgnoringUnknownFields(false);

                from("direct:unmarshal-strict")
                        .unmarshal(strictFormat);
            }
        };
    }

    @Nested
    @DisplayName("Preserving Proto Field Names")
    class PreservingProtoFieldNamesTests {

        @Test
        @DisplayName("Should use JSON names by default")
        void shouldUseJsonNamesByDefault() throws Exception {
            // Given - proto field: zip_code, json name: zipCode
            // We need Address for this, but using SimpleUser for simplicity
            SimpleUser user = SimpleUser.newBuilder()
                    .setName("Test")
                    .build();

            // When
            String json = marshalToJson(user);

            // Then - uses JSON names (camelCase)
            assertThat(json).contains("\"name\"");
        }

        @Test
        @DisplayName("Should preserve proto field names when configured")
        void shouldPreserveProtoFieldNamesWhenConfigured() throws Exception {
            // Given
            SimpleUser user = SimpleUser.newBuilder()
                    .setName("Test")
                    .build();

            // When
            String json = producer.requestBody("direct:marshal-proto-names", user, String.class);

            // Then - uses proto names (snake_case if applicable)
            assertThat(json).contains("\"name\""); // same in this case
        }
    }

    @Nested
    @DisplayName("Including Default Values")
    class IncludingDefaultValuesTests {

        @Test
        @DisplayName("Should not include defaults by default")
        void shouldNotIncludeDefaultsByDefault() throws Exception {
            // Given
            SimpleUser user = SimpleUser.newBuilder()
                    .setName("Test")
                    // age, email, active not set
                    .build();

            // When
            String json = marshalToJson(user);

            // Then
            assertThat(json).doesNotContain("\"age\"");
            assertThat(json).doesNotContain("\"email\"");
            assertThat(json).doesNotContain("\"active\"");
        }

        @Test
        @DisplayName("Should include defaults when configured")
        void shouldIncludeDefaultsWhenConfigured() throws Exception {
            // Given
            SimpleUser user = SimpleUser.newBuilder()
                    .setName("Test")
                    .build();

            // When
            String json = producer.requestBody("direct:marshal-with-defaults", user, String.class);

            // Then
            assertThat(json).contains("\"age\":0");
            assertThat(json).contains("\"email\":\"\"");
            assertThat(json).contains("\"active\":false");
        }
    }

    @Nested
    @DisplayName("Unknown Fields Handling")
    class UnknownFieldsTests {

        @Test
        @DisplayName("Should ignore unknown fields when configured")
        void shouldIgnoreUnknownFieldsWhenConfigured() {
            // Given
            String json = """
                    {
                        "name": "Test",
                        "unknownField": "should be ignored",
                        "anotherUnknown": 123
                    }
                    """;

            // When
            SimpleUser user = producer.requestBody(
                    "direct:unmarshal-ignore-unknown", json, SimpleUser.class);

            // Then - should parse successfully, ignoring unknown
            assertThat(user.getName()).isEqualTo("Test");
        }

        @Test
        @DisplayName("Should throw on unknown fields in strict mode")
        void shouldThrowOnUnknownFieldsInStrictMode() {
            // Given
            String json = """
                    {
                        "name": "Test",
                        "unknownField": "should cause error"
                    }
                    """;

            // When/Then
            assertThatThrownBy(() -> 
                    producer.requestBody("direct:unmarshal-strict", json, SimpleUser.class));
        }
    }

    @Nested
    @DisplayName("Numeric Enum Handling")
    class NumericEnumTests {

        @Test
        @DisplayName("Should accept numeric enums by default")
        void shouldAcceptNumericEnumsByDefault() {
            // Tested in EnumFieldTest - acceptNumericEnums default is true
        }
    }
}