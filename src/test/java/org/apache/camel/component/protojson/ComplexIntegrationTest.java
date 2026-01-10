// src/test/java/org/apache/camel/component/protojson/ComplexIntegrationTest.java

package org.apache.camel.component.protojson;

import com.google.protobuf.Timestamp;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.protojson.test.proto.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Complex integration tests with multi-step routes.
 */
@DisplayName("Complex Integration Tests")
class ComplexIntegrationTest extends BaseProtoJsonTest {

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                ProtoJsonDataFormat complexFormat = new ProtoJsonDataFormat(ComplexMessage.class);

                from("direct:marshal")
                        .marshal(complexFormat)
                        .convertBodyTo(String.class);

                from("direct:unmarshal")
                        .unmarshal(complexFormat);

                // Multi-step route: unmarshal -> process -> marshal
                from("direct:process-pipeline")
                        .unmarshal(complexFormat)
                        .process(exchange -> {
                            ComplexMessage msg = exchange.getIn().getBody(ComplexMessage.class);
                            // Modify the message
                            ComplexMessage modified = msg.toBuilder()
                                    .setId(msg.getId() + "-processed")
                                    .build();
                            exchange.getIn().setBody(modified);
                        })
                        .marshal(complexFormat)
                        .convertBodyTo(String.class)
                        .to("mock:result");

                // Route with content-based routing
                ProtoJsonDataFormat simpleFormat = new ProtoJsonDataFormat(SimpleUser.class);

                from("direct:content-router")
                        .unmarshal(simpleFormat)
                        .choice()
                            .when(simple("${body.active} == true"))
                                .to("mock:active-users")
                            .otherwise()
                                .to("mock:inactive-users");
            }
        };
    }

    @Test
    @DisplayName("Should handle complex nested message")
    void shouldHandleComplexNestedMessage() {
        // Given
        Instant now = Instant.now();
        
        ComplexMessage complex = ComplexMessage.newBuilder()
                .setId("complex-1")
                .setUser(SimpleUser.newBuilder()
                        .setName("Complex User")
                        .setAge(35)
                        .setActive(true)
                        .build())
                .addEvents(EventMessage.newBuilder()
                        .setEventId("evt-1")
                        .setCreatedAt(Timestamp.newBuilder()
                                .setSeconds(now.getEpochSecond())
                                .build())
                        .build())
                .putUserMap("admin", UserWithAddress.newBuilder()
                        .setName("Admin User")
                        .setAddress(Address.newBuilder()
                                .setCity("Admin City")
                                .build())
                        .build())
                .setUpdatedAt(Timestamp.newBuilder()
                        .setSeconds(now.getEpochSecond())
                        .build())
                .build();

        // When
        ComplexMessage result = roundTrip(complex);

        // Then
        assertThat(result.getId()).isEqualTo("complex-1");
        assertThat(result.getUser().getName()).isEqualTo("Complex User");
        assertThat(result.getEventsCount()).isEqualTo(1);
        assertThat(result.getUserMapMap()).containsKey("admin");
    }

    @Test
    @DisplayName("Should process through pipeline")
    void shouldProcessThroughPipeline() throws Exception {
        // Given
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        String inputJson = """
                {
                    "id": "pipeline-test",
                    "user": {
                        "name": "Pipeline User",
                        "age": 25
                    }
                }
                """;

        // When
        producer.sendBody("direct:process-pipeline", inputJson);

        // Then
        mock.assertIsSatisfied();
        
        String resultJson = mock.getExchanges().get(0).getIn().getBody(String.class);
        assertThat(resultJson).contains("pipeline-test-processed");
    }

    @Test
    @DisplayName("Should route based on message content")
    void shouldRouteBasedOnMessageContent() throws Exception {
        // Given
        MockEndpoint activeMock = getMockEndpoint("mock:active-users");
        MockEndpoint inactiveMock = getMockEndpoint("mock:inactive-users");
        
        activeMock.expectedMessageCount(1);
        inactiveMock.expectedMessageCount(1);

        String activeUserJson = """
                {
                    "name": "Active User",
                    "active": true
                }
                """;

        String inactiveUserJson = """
                {
                    "name": "Inactive User",
                    "active": false
                }
                """;

        // When
        producer.sendBody("direct:content-router", activeUserJson);
        producer.sendBody("direct:content-router", inactiveUserJson);

        // Then
        activeMock.assertIsSatisfied();
        inactiveMock.assertIsSatisfied();
    }

    @Test
    @DisplayName("Should handle large message with many fields")
    void shouldHandleLargeMessageWithManyFields() {
        // Given
        ComplexMessage.Builder builder = ComplexMessage.newBuilder()
                .setId("large-message");

        // Add 100 events
        for (int i = 0; i < 100; i++) {
            builder.addEvents(EventMessage.newBuilder()
                    .setEventId("evt-" + i)
                    .build());
        }

        // Add 50 users to map
        for (int i = 0; i < 50; i++) {
            builder.putUserMap("user-" + i, UserWithAddress.newBuilder()
                    .setName("User " + i)
                    .setAddress(Address.newBuilder()
                            .setCity("City " + i)
                            .build())
                    .build());
        }

        ComplexMessage original = builder.build();

        // When
        ComplexMessage result = roundTrip(original);

        // Then
        assertThat(result.getEventsCount()).isEqualTo(100);
        assertThat(result.getUserMapMap()).hasSize(50);
    }
}