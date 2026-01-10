package org.apache.camel.component.protojson;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.google.protobuf.Descriptors;
import com.google.protobuf.Message;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.protojson.converter.JsonInFieldConverter;
import org.apache.camel.component.protojson.converter.JsonOutFieldConverter;
import org.apache.camel.component.protojson.test.proto.OrderMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for custom BigDecimal message converter.
 * 
 * Proto:
 * <pre>
 * message BigDecimal {
 *   int64 unscaled_value = 1;  // e.g., 12345
 *   int32 scale = 2;           // e.g., 2 → 123.45
 * }
 * 
 * message OrderMessage {
 *   string order_id = 1;
 *   BigDecimal amount = 2;
 * }
 * </pre>
 * 
 * JSON representation: "123.45" or 123.45
 */
@DisplayName("Custom BigDecimal Message Converter Tests")
public class BigDecimalMessageConverterTest extends BaseProtoJsonTest {

    private static final String BIG_DECIMAL_TYPE = "org.apache.camel.component.protojson.test.BigDecimal";

    @Override
    protected CamelContext createCamelContext() throws Exception {

        CamelContext camelContext = super.createCamelContext();

        BigDecimalMessageConverter converter = new BigDecimalMessageConverter();
        camelContext.getRegistry().bind("bigDecimalConverter", converter);

        return camelContext;

    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {


                ProtoJsonDataFormat protoJson = new ProtoJsonDataFormat(OrderMessage.class);

                from("direct:marshal")
                        .marshal(protoJson)
                        .convertBodyTo(String.class);

                from("direct:unmarshal")
                        .unmarshal(protoJson);
            }
        };
    }

    @Test
    @DisplayName("Should unmarshal JSON string to BigDecimal message")
    void shouldUnmarshalJsonStringToBigDecimalMessage() {
        String json = """
                {
                    "orderId": "order-1",
                    "amount": "123.45"
                }
                """;

        OrderMessage order = unmarshalFromJson(json, OrderMessage.class);

        assertThat(order.getOrderId()).isEqualTo("order-1");
        assertThat(order.getAmount().getUnscaledValue()).isEqualTo(12345L);
        assertThat(order.getAmount().getScale()).isEqualTo(2);
    }

    @Test
    @DisplayName("Should unmarshal JSON number to BigDecimal message")
    void shouldUnmarshalJsonNumberToBigDecimalMessage() {
        String json = """
                {
                    "orderId": "order-2",
                    "amount": 999.99
                }
                """;

        OrderMessage order = unmarshalFromJson(json, OrderMessage.class);

        assertThat(order.getAmount().getUnscaledValue()).isEqualTo(99999L);
        assertThat(order.getAmount().getScale()).isEqualTo(2);
    }

    @Test
    @DisplayName("Should marshal BigDecimal message to JSON string")
    void shouldMarshalBigDecimalMessageToJsonString() {
        OrderMessage order = OrderMessage.newBuilder()
                .setOrderId("order-3")
                .setAmount(org.apache.camel.component.protojson.test.proto.BigDecimal.newBuilder()
                        .setUnscaledValue(150000L)
                        .setScale(2)
                        .build())
                .build();

        String json = marshalToJson(order);

        assertThat(json).contains("\"amount\":\"1500.00\"");
    }

    @Test
    @DisplayName("Should handle integer values")
    void shouldHandleIntegerValues() {
        String json = """
                {
                    "orderId": "order-int",
                    "amount": "100"
                }
                """;

        OrderMessage order = unmarshalFromJson(json, OrderMessage.class);

        assertThat(order.getAmount().getUnscaledValue()).isEqualTo(100L);
        assertThat(order.getAmount().getScale()).isEqualTo(0);
    }

    @Test
    @DisplayName("Should handle large precision values")
    void shouldHandleLargePrecisionValues() {
        String json = """
                {
                    "orderId": "order-large",
                    "amount": "123456789.123456789"
                }
                """;

        OrderMessage order = unmarshalFromJson(json, OrderMessage.class);

        // Verify round-trip
        BigDecimal original = new BigDecimal("123456789.123456789");
        BigDecimal reconstructed = BigDecimal.valueOf(
                order.getAmount().getUnscaledValue(),
                order.getAmount().getScale()
        );
        assertThat(reconstructed).isEqualByComparingTo(original);
    }

    @Test
    @DisplayName("Should round-trip BigDecimal")
    void shouldRoundTripBigDecimal() {
        OrderMessage original = OrderMessage.newBuilder()
                .setOrderId("order-rt")
                .setAmount(org.apache.camel.component.protojson.test.proto.BigDecimal.newBuilder()
                        .setUnscaledValue(999999L)
                        .setScale(4)
                        .build())
                .build();

        OrderMessage result = roundTrip(original);

        assertThat(result.getAmount().getUnscaledValue())
                .isEqualTo(original.getAmount().getUnscaledValue());
        assertThat(result.getAmount().getScale())
                .isEqualTo(original.getAmount().getScale());
    }

    // ==================== Custom Converter ====================

    /**
     * Converter for BigDecimal proto message.
     * 
     * JSON: "123.45" or 123.45
     * Proto: BigDecimal { unscaled_value: 12345, scale: 2 }
     */
    public static class BigDecimalMessageConverter implements JsonInFieldConverter, JsonOutFieldConverter {

        @Override
        public boolean supports(Descriptors.FieldDescriptor field) {
            return field.getJavaType() == Descriptors.FieldDescriptor.JavaType.MESSAGE
                    && field.getMessageType().getFullName()
                    .equals(org.apache.camel.component.protojson.test.proto.BigDecimal.getDescriptor().getFullName());
        }

        @Override
        public void read(JsonParser parser, Message.Builder builder,
                Descriptors.FieldDescriptor field) throws IOException {
            JsonToken token = parser.currentToken();

            if (token == JsonToken.VALUE_NULL) {
                return;
            }

            BigDecimal value;

            if (token == JsonToken.VALUE_NUMBER_FLOAT || token == JsonToken.VALUE_NUMBER_INT) {
                value = parser.getDecimalValue();
            } else if (token == JsonToken.VALUE_STRING) {
                String text = parser.getText();
                if (text == null || text.isEmpty()) {
                    return;
                }
                value = new BigDecimal(text);
            } else {
                throw new IOException("Expected number or string for BigDecimal, got: " + token);
            }

            org.apache.camel.component.protojson.test.proto.BigDecimal bigDecimalMsg = org.apache.camel.component.protojson.test.proto.BigDecimal.newBuilder()
                    .setUnscaledValue(value.unscaledValue().longValueExact())
                    .setScale(value.scale())
                    .build();

            builder.setField(field, bigDecimalMsg);
        }

        @Override
        public void write(JsonGenerator gen, Message message,
                Descriptors.FieldDescriptor field) throws IOException {
            org.apache.camel.component.protojson.test.proto.BigDecimal bigDecimalMsg = (org.apache.camel.component.protojson.test.proto.BigDecimal) message.getField(field);

            BigDecimal value = BigDecimal.valueOf(
                    bigDecimalMsg.getUnscaledValue(),
                    bigDecimalMsg.getScale()
            );

            gen.writeString(value.toPlainString());
        }
    }
}