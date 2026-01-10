package org.apache.camel.component.protojson.converter.wellknown;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.google.protobuf.Descriptors;
import com.google.protobuf.Duration;
import com.google.protobuf.Message;
import org.apache.camel.component.protojson.converter.JsonInFieldConverter;
import org.apache.camel.component.protojson.converter.JsonOutFieldConverter;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converter for google.protobuf.Duration.
 * JSON format: string with 's' suffix (e.g., "3.5s", "120s", "-1.5s")
 */
public class DurationConverter implements JsonInFieldConverter, JsonOutFieldConverter {

    private static final String DURATION_TYPE = "google.protobuf.Duration";
    private static final Pattern DURATION_PATTERN = Pattern.compile("^(-?\\d+)(\\.\\d{1,9})?s$");

    @Override
    public boolean supports(Descriptors.FieldDescriptor field) {
        return field.getJavaType() == Descriptors.FieldDescriptor.JavaType.MESSAGE
                && field.getMessageType().getFullName().equals(DURATION_TYPE);
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
        
        // Handle string format "3.5s"
        if (token == JsonToken.VALUE_STRING) {
            String durationStr = parser.getText();
            if (durationStr == null || durationStr.isEmpty()) {
                return;
            }

            Matcher matcher = DURATION_PATTERN.matcher(durationStr);
            if (!matcher.matches()) {
                throw new IOException("Invalid duration format: " + durationStr + 
                        ". Expected format: '3.5s' or '120s'");
            }

            long seconds = Long.parseLong(matcher.group(1));
            int nanos = 0;
            
            String fractionPart = matcher.group(2);
            if (fractionPart != null) {
                // ".5" -> "500000000", ".123" -> "123000000"
                String nanoStr = fractionPart.substring(1); // remove "."
                nanoStr = (nanoStr + "000000000").substring(0, 9);
                nanos = Integer.parseInt(nanoStr);
                if (seconds < 0) {
                    nanos = -nanos;
                }
            }

            Duration duration = Duration.newBuilder()
                    .setSeconds(seconds)
                    .setNanos(nanos)
                    .build();

            builder.setField(field, duration);
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
            
            Duration duration = Duration.newBuilder()
                    .setSeconds(seconds)
                    .setNanos(nanos)
                    .build();
            
            builder.setField(field, duration);
            return;
        }
        
        throw new IOException("Unexpected token for Duration: " + token);
    }

    // ==================== Proto -> JSON ====================

    @Override
    public void write(JsonGenerator gen, Message message,
                      Descriptors.FieldDescriptor field) throws IOException {
        Message durMsg = (Message) message.getField(field);
        
        Descriptors.Descriptor desc = durMsg.getDescriptorForType();
        long seconds = (Long) durMsg.getField(desc.findFieldByName("seconds"));
        int nanos = (Integer) durMsg.getField(desc.findFieldByName("nanos"));

        StringBuilder sb = new StringBuilder();
        sb.append(seconds);
        
        if (nanos != 0) {
            sb.append(".");
            String nanoStr = String.format("%09d", Math.abs(nanos));
            // Trim trailing zeros
            int end = 9;
            while (end > 1 && nanoStr.charAt(end - 1) == '0') {
                end--;
            }
            sb.append(nanoStr, 0, end);
        }
        sb.append("s");
        
        gen.writeString(sb.toString());
    }
}
