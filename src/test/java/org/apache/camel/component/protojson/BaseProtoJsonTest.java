// src/test/java/org/apache/camel/component/protojson/BaseProtoJsonTest.java

package org.apache.camel.component.protojson;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.Message;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Base class for ProtoJson integration tests.
 */
public abstract class BaseProtoJsonTest extends CamelTestSupport {

    protected static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    
    protected ProducerTemplate producer;

    @BeforeEach
    void setupProducer() {
        producer = context.createProducerTemplate();
    }

    // ==================== Helper Methods ====================

    /**
     * Marshal Proto message to JSON and return as String.
     */
    protected String marshalToJson(Message message) {
        return producer.requestBody("direct:marshal", message, String.class);
    }

    /**
     * Marshal Proto message to JSON bytes.
     */
    protected byte[] marshalToBytes(Message message) {
        return producer.requestBody("direct:marshal", message, byte[].class);
    }

    /**
     * Unmarshal JSON to Proto message.
     */
    @SuppressWarnings("unchecked")
    protected <T extends Message> T unmarshalFromJson(String json, Class<T> type) {
        return producer.requestBodyAndHeader(
                "direct:unmarshal", 
                json, 
                "CamelProtoJsonClass", 
                type.getName(), 
                type
        );
    }

    /**
     * Unmarshal JSON bytes to Proto message.
     */
    protected <T extends Message> T unmarshalFromBytes(byte[] json, Class<T> type) {
        return producer.requestBodyAndHeader(
                "direct:unmarshal", 
                json, 
                "CamelProtoJsonClass", 
                type.getName(), 
                type
        );
    }

    /**
     * Round-trip test: Proto -> JSON -> Proto
     */
    protected <T extends Message> T roundTrip(T message) {
        String json = marshalToJson(message);
        @SuppressWarnings("unchecked")
        Class<T> type = (Class<T>) message.getClass();
        return unmarshalFromJson(json, type);
    }

    /**
     * Assert JSON equals (ignoring field order).
     */
    protected void assertJsonEquals(String expected, String actual) throws Exception {
        JSONAssert.assertEquals(expected, actual, JSONCompareMode.NON_EXTENSIBLE);
    }

    /**
     * Assert JSON contains field with value.
     */
    protected void assertJsonContains(String json, String field, Object expectedValue) throws Exception {
        JsonNode node = OBJECT_MAPPER.readTree(json);
        assertThat(node.has(field)).isTrue();
        
        JsonNode fieldNode = node.get(field);
        if (expectedValue instanceof String) {
            assertThat(fieldNode.asText()).isEqualTo(expectedValue);
        } else if (expectedValue instanceof Integer) {
            assertThat(fieldNode.asInt()).isEqualTo(expectedValue);
        } else if (expectedValue instanceof Boolean) {
            assertThat(fieldNode.asBoolean()).isEqualTo(expectedValue);
        }
    }

    /**
     * Get JSON field value.
     */
    protected JsonNode getJsonField(String json, String field) throws Exception {
        return OBJECT_MAPPER.readTree(json).get(field);
    }

    /**
     * Create ProtoJsonDataFormat with custom settings.
     */
    protected ProtoJsonDataFormat createDataFormat(Class<? extends Message> type) {
        ProtoJsonDataFormat df = new ProtoJsonDataFormat(type);
        return df;
    }

    /**
     * Create ProtoJsonDataFormat with configuration.
     */
    protected ProtoJsonDataFormat createDataFormat(
            Class<? extends Message> type,
            boolean ignoringUnknownFields,
            boolean preservingProtoFieldNames) {
        
        ProtoJsonDataFormat df = new ProtoJsonDataFormat(type);
        df.setIgnoringUnknownFields(ignoringUnknownFields);
        df.setPreservingProtoFieldNames(preservingProtoFieldNames);
        return df;
    }
}