package org.apache.camel.component.protojson.converter.wellknown;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.google.protobuf.Descriptors;
import com.google.protobuf.Message;
import com.google.protobuf.Timestamp;
import org.apache.camel.component.protojson.converter.JsonInFieldConverter;
import org.apache.camel.component.protojson.converter.JsonOutFieldConverter;

import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Converter for google.protobuf.Timestamp.
 * JSON format: RFC 3339 string (e.g., "2024-01-15T10:30:00.123Z")
 */
public class TimestampConverter implements JsonInFieldConverter, JsonOutFieldConverter {

    private static final String TIMESTAMP_TYPE = "google.protobuf.Timestamp";

    @Override
    public boolean supports(Descriptors.FieldDescriptor field) {
        return field.getJavaType() == Descriptors.FieldDescriptor.JavaType.MESSAGE
                && field.getMessageType().getFullName().equals(TIMESTAMP_TYPE);
    }

    // ==================== JSON -> Proto ====================

    @Override
    public void read(JsonParser parser, Message.Builder builder,
                     Descriptors.FieldDescriptor field) throws IOException {
        
        JsonToken token = parser.currentToken();
        
        // Handle null
        if (token == JsonToken.VALUE_NULL) {
            return;
        }
        
        // Handle string (ISO-8601 format)
        if (token == JsonToken.VALUE_STRING) {
            String isoDate = parser.getText();
            if (isoDate == null || isoDate.isEmpty()) {
                return;
            }

            try {
                Instant instant = Instant.parse(isoDate);
                
                Timestamp ts = Timestamp.newBuilder()
                        .setSeconds(instant.getEpochSecond())
                        .setNanos(instant.getNano())
                        .build();

                builder.setField(field, ts);
            } catch (DateTimeParseException e) {
                throw new IOException("Invalid Timestamp format: " + isoDate, e);
            }
            return;
        }
        
        // Handle object format {"seconds": ..., "nanos": ...}
        if (token == JsonToken.START_OBJECT) {
            long seconds = 0;
            int nanos = 0;
            
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                String fieldName = parser.getCurrentName();
                parser.nextToken();
                
                if ("seconds".equals(fieldName)) {
                    seconds = parser.getLongValue();
                } else if ("nanos".equals(fieldName)) {
                    nanos = parser.getIntValue();
                }
            }
            
            Timestamp ts = Timestamp.newBuilder()
                    .setSeconds(seconds)
                    .setNanos(nanos)
                    .build();
            
            builder.setField(field, ts);
            return;
        }
        
        throw new IOException("Unexpected token for Timestamp: " + token);
    }

    // ==================== Proto -> JSON ====================

    @Override
    public void write(JsonGenerator gen, Message message,
                      Descriptors.FieldDescriptor field) throws IOException {
        Message tsMsg = (Message) message.getField(field);
        
        Descriptors.Descriptor desc = tsMsg.getDescriptorForType();
        long seconds = (Long) tsMsg.getField(desc.findFieldByName("seconds"));
        int nanos = (Integer) tsMsg.getField(desc.findFieldByName("nanos"));

        Instant instant = Instant.ofEpochSecond(seconds, nanos);
        String isoDate = DateTimeFormatter.ISO_INSTANT.format(instant);
        
        gen.writeString(isoDate);
    }
}
