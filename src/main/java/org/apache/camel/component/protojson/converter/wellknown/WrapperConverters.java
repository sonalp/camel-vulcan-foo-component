package org.apache.camel.component.protojson.converter.wellknown;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.google.protobuf.BoolValue;
import com.google.protobuf.BytesValue;
import com.google.protobuf.Descriptors;
import com.google.protobuf.DoubleValue;
import com.google.protobuf.FloatValue;
import com.google.protobuf.Int32Value;
import com.google.protobuf.Int64Value;
import com.google.protobuf.Message;
import com.google.protobuf.StringValue;
import com.google.protobuf.UInt32Value;
import com.google.protobuf.UInt64Value;
import com.google.protobuf.ByteString;
import org.apache.camel.component.protojson.converter.JsonInFieldConverter;
import org.apache.camel.component.protojson.converter.JsonOutFieldConverter;
import org.apache.camel.component.protojson.internal.parser.WrapperParsers;

import java.io.IOException;
import java.util.Base64;
import java.util.Set;

/**
 * Converter for google.protobuf wrapper types:
 * - StringValue, BytesValue
 * - BoolValue
 * - Int32Value, Int64Value, UInt32Value, UInt64Value
 * - FloatValue, DoubleValue
 * 
 * These are serialized as their primitive JSON equivalents.
 */
public class WrapperConverters implements JsonInFieldConverter, JsonOutFieldConverter {

    public static final Set<String> WRAPPER_TYPES = Set.of(
            "google.protobuf.StringValue",
            "google.protobuf.BytesValue",
            "google.protobuf.BoolValue",
            "google.protobuf.Int32Value",
            "google.protobuf.Int64Value",
            "google.protobuf.UInt32Value",
            "google.protobuf.UInt64Value",
            "google.protobuf.FloatValue",
            "google.protobuf.DoubleValue"
    );

    @Override
    public boolean supports(Descriptors.FieldDescriptor field) {
        if (field.getJavaType() != Descriptors.FieldDescriptor.JavaType.MESSAGE) {
            return false;
        }
        return WRAPPER_TYPES.contains(field.getMessageType().getFullName());
    }

    // ==================== JSON -> Proto ====================

    @Override
    public void read(JsonParser parser, Message.Builder builder,
                     Descriptors.FieldDescriptor field) throws IOException {
        
        JsonToken token = parser.currentToken();
        
        // Handle null - wrapper not set
        if (token == JsonToken.VALUE_NULL) {
            return;
        }
        
        String typeName = field.getMessageType().getFullName();
        Message wrapper = parseWrapper(parser, token, typeName);
        
        if (wrapper != null) {
            builder.setField(field, wrapper);
        }
    }

    private Message parseWrapper(JsonParser parser, JsonToken token, String typeName) throws IOException {
        return WrapperParsers.parseWrapper(parser, token, typeName);
    }

    // ==================== Proto -> JSON ====================

    @Override
    public void write(JsonGenerator gen, Message message,
                      Descriptors.FieldDescriptor field) throws IOException {
        Message wrapper = (Message) message.getField(field);
        String typeName = field.getMessageType().getFullName();
        
        Descriptors.FieldDescriptor valueField = wrapper.getDescriptorForType()
                .findFieldByName("value");
        Object value = wrapper.getField(valueField);
        
        switch (typeName) {
            case "google.protobuf.StringValue" -> gen.writeString((String) value);
            case "google.protobuf.BytesValue" -> {
                ByteString bs = (ByteString) value;
                gen.writeString(Base64.getEncoder().encodeToString(bs.toByteArray()));
            }
            case "google.protobuf.BoolValue" -> gen.writeBoolean((Boolean) value);
            case "google.protobuf.Int32Value" -> gen.writeNumber((Integer) value);
            case "google.protobuf.Int64Value" -> gen.writeNumber((Long) value);
            case "google.protobuf.UInt32Value" -> gen.writeNumber((Integer) value);
            case "google.protobuf.UInt64Value" -> gen.writeNumber((Long) value);
            case "google.protobuf.FloatValue" -> gen.writeNumber((Float) value);
            case "google.protobuf.DoubleValue" -> gen.writeNumber((Double) value);
            default -> throw new IOException("Unknown wrapper type: " + typeName);
        }
    }
}
