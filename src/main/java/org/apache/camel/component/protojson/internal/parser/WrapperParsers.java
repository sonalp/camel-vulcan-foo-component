package org.apache.camel.component.protojson.internal.parser;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.google.protobuf.*;
import java.io.IOException;
import java.util.Base64;
import java.util.Map;

public final class WrapperParsers {

    @FunctionalInterface
    private interface WrapperParser {
        Message parse(JsonParser parser, JsonToken token) throws IOException;
    }

    private static final Map<String, WrapperParser> WRAPPER_PARSERS = Map.ofEntries(
        Map.entry("google.protobuf.StringValue", (parser, token) -> {
            if (token.isScalarValue()) {
                return StringValue.of(parser.getValueAsString());
            }
            throw new IOException("Expected string for StringValue");
        }),

        Map.entry("google.protobuf.BytesValue", (parser, token) -> {
            if (token == JsonToken.VALUE_STRING) {
                // Jackson zaten Base64 decode biliyor, ekstra string -> decoder yerine bunu kullanabiliriz.
                byte[] bytes = parser.getBinaryValue();
                return BytesValue.of(ByteString.copyFrom(bytes));
            }
            throw new IOException("Expected base64 string for BytesValue");
        }),

        Map.entry("google.protobuf.BoolValue", (parser, token) -> {
            if (token == JsonToken.VALUE_TRUE) {
                return BoolValue.of(true);
            }
            if (token == JsonToken.VALUE_FALSE) {
                return BoolValue.of(false);
            }
            if (token.isScalarValue()) {
                return BoolValue.of(Boolean.parseBoolean(parser.getValueAsString()));
            }
            throw new IOException("Expected boolean for BoolValue");
        }),

        Map.entry("google.protobuf.Int32Value", (parser, token) -> {
            if (token.isNumeric()) {
                return Int32Value.of(parser.getIntValue());
            }
            if (token.isScalarValue()) {
                return Int32Value.of(Integer.parseInt(parser.getValueAsString()));
            }
            throw new IOException("Expected integer for Int32Value");
        }),

        Map.entry("google.protobuf.Int64Value", (parser, token) -> {
            if (token.isNumeric()) {
                return Int64Value.of(parser.getLongValue());
            }
            if (token.isScalarValue()) {
                return Int64Value.of(Long.parseLong(parser.getValueAsString()));
            }
            throw new IOException("Expected long for Int64Value");
        }),

        Map.entry("google.protobuf.UInt32Value", (parser, token) -> {
            if (token.isNumeric()) {
                return UInt32Value.of(parser.getIntValue());
            }
            if (token.isScalarValue()) {
                return UInt32Value.of(Integer.parseUnsignedInt(parser.getValueAsString()));
            }
            throw new IOException("Expected unsigned integer for UInt32Value");
        }),

        Map.entry("google.protobuf.UInt64Value", (parser, token) -> {
            if (token.isNumeric()) {
                return UInt64Value.of(parser.getLongValue());
            }
            if (token.isScalarValue()) {
                return UInt64Value.of(Long.parseUnsignedLong(parser.getValueAsString()));
            }
            throw new IOException("Expected unsigned long for UInt64Value");
        }),

        Map.entry("google.protobuf.FloatValue", (parser, token) -> {
            if (token.isNumeric()) {
                return FloatValue.of((float) parser.getDoubleValue());
            }
            if (token.isScalarValue()) {
                return FloatValue.of(Float.parseFloat(parser.getValueAsString()));
            }
            throw new IOException("Expected float for FloatValue");
        }),

        Map.entry("google.protobuf.DoubleValue", (parser, token) -> {
            if (token.isNumeric()) {
                return DoubleValue.of(parser.getDoubleValue());
            }
            if (token.isScalarValue()) {
                return DoubleValue.of(Double.parseDouble(parser.getValueAsString()));
            }
            throw new IOException("Expected double for DoubleValue");
        })
    );

    // Esas çağıracağın API
    public static Message parseWrapper(JsonParser parser,
                                       JsonToken token,
                                       String typeName) throws IOException {

        WrapperParser wrapperParser = WRAPPER_PARSERS.get(typeName);
        if (wrapperParser == null) {
            throw new IOException("Unknown wrapper type: " + typeName);
        }
        return wrapperParser.parse(parser, token);
    }
}
