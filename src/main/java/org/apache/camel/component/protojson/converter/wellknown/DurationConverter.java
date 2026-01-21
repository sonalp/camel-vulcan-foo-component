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

    /**
     * Pre-computed powers of 10 for fast nano digit extraction.
     * Index i gives 10^(8-i), used to extract digits from left to right.
     */
    private static final int[] POWERS_OF_10 = {
            100_000_000, 10_000_000, 1_000_000, 100_000, 10_000, 1_000, 100, 10, 1
    };

    @Override
    public void write(JsonGenerator gen, Message message,
                      Descriptors.FieldDescriptor field) throws IOException {
        Message durMsg = (Message) message.getField(field);

        Descriptors.Descriptor desc = durMsg.getDescriptorForType();
        long seconds = (Long) durMsg.getField(desc.findFieldByName("seconds"));
        int nanos = (Integer) durMsg.getField(desc.findFieldByName("nanos"));

        // Performance: Direct char buffer approach - avoids String.format() allocation
        // Maximum length: "-9223372036854775808.123456789s" = 32 chars
        char[] buffer = new char[32];
        int pos = 0;

        // Write seconds part
        String secsStr = Long.toString(seconds);
        int secsLen = secsStr.length();
        secsStr.getChars(0, secsLen, buffer, 0);
        pos = secsLen;

        // Write nanos part if non-zero
        if (nanos != 0) {
            buffer[pos++] = '.';

            int absNanos = Math.abs(nanos);

            // Extract 9 digits using division (avoids String.format allocation)
            int nanoStart = pos;
            for (int power : POWERS_OF_10) {
                buffer[pos++] = (char) ('0' + (absNanos / power));
                absNanos %= power;
            }

            // Trim trailing zeros (but keep at least one digit after decimal)
            int nanoEnd = pos;
            while (nanoEnd > nanoStart + 1 && buffer[nanoEnd - 1] == '0') {
                nanoEnd--;
            }
            pos = nanoEnd;
        }

        buffer[pos++] = 's';

        gen.writeString(new String(buffer, 0, pos));
    }
}
