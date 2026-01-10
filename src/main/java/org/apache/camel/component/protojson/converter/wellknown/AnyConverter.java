package org.apache.camel.component.protojson.converter.wellknown;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import org.apache.camel.component.protojson.converter.JsonInFieldConverter;
import org.apache.camel.component.protojson.converter.JsonOutFieldConverter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Converter for google.protobuf.Any.
 *
 * JSON format:
 * {
 *   "@type": "type.googleapis.com/full.type.name",
 *   "field1": value1,
 *   "field2": value2,
 *   ...
 * }
 *
 * Usage:
 * <pre>
 * {@code
 * @Bean
 * public AnyConverter anyConverter() {
 *     return AnyConverter.create()
 *         .register(SimpleUser.class)
 *         .register(OrderMessage.class);
 * }
 * }
 * </pre>
 */
public class AnyConverter implements JsonInFieldConverter, JsonOutFieldConverter {

    private static final String ANY_TYPE = "google.protobuf.Any";
    private static final String TYPE_URL_PREFIX = "type.googleapis.com/";
    private static final String TYPE_FIELD = "@type";

    // Static cache for descriptor resolution - avoids repeated reflection
    private static final ConcurrentHashMap<Class<?>, Descriptors.Descriptor> DESCRIPTOR_CACHE =
            new ConcurrentHashMap<>();

    // Type registry for resolving type URLs to descriptors
    private static final Map<String, Descriptors.Descriptor> WELL_KNOWN_TYPES;

    static {
        Map<String, Descriptors.Descriptor> map = new HashMap<>();
        map.put(com.google.protobuf.Timestamp.getDescriptor().getFullName(),
                com.google.protobuf.Timestamp.getDescriptor());
        map.put(com.google.protobuf.Duration.getDescriptor().getFullName(),
                com.google.protobuf.Duration.getDescriptor());
        map.put(com.google.protobuf.Struct.getDescriptor().getFullName(),
                com.google.protobuf.Struct.getDescriptor());
        map.put(com.google.protobuf.Value.getDescriptor().getFullName(),
                com.google.protobuf.Value.getDescriptor());
        map.put(com.google.protobuf.ListValue.getDescriptor().getFullName(),
                com.google.protobuf.ListValue.getDescriptor());
        map.put(com.google.protobuf.BoolValue.getDescriptor().getFullName(),
                com.google.protobuf.BoolValue.getDescriptor());
        map.put(com.google.protobuf.Int32Value.getDescriptor().getFullName(),
                com.google.protobuf.Int32Value.getDescriptor());
        map.put(com.google.protobuf.Int64Value.getDescriptor().getFullName(),
                com.google.protobuf.Int64Value.getDescriptor());
        map.put(com.google.protobuf.UInt32Value.getDescriptor().getFullName(),
                com.google.protobuf.UInt32Value.getDescriptor());
        map.put(com.google.protobuf.UInt64Value.getDescriptor().getFullName(),
                com.google.protobuf.UInt64Value.getDescriptor());
        map.put(com.google.protobuf.FloatValue.getDescriptor().getFullName(),
                com.google.protobuf.FloatValue.getDescriptor());
        map.put(com.google.protobuf.DoubleValue.getDescriptor().getFullName(),
                com.google.protobuf.DoubleValue.getDescriptor());
        map.put(com.google.protobuf.StringValue.getDescriptor().getFullName(),
                com.google.protobuf.StringValue.getDescriptor());
        map.put(com.google.protobuf.BytesValue.getDescriptor().getFullName(),
                com.google.protobuf.BytesValue.getDescriptor());

        WELL_KNOWN_TYPES = Map.copyOf(map);
    }

    private final ConcurrentHashMap<String, Descriptors.Descriptor> typeRegistry;

    /**
     * Create a new AnyConverter instance.
     */
    public static AnyConverter create() {
        return new AnyConverter();
    }

    private AnyConverter() {
        this.typeRegistry = new ConcurrentHashMap<>(WELL_KNOWN_TYPES);

    }

    /**
     * Register a Message class. Returns this for fluent chaining.
     */
    public AnyConverter register(Class<? extends Message> messageClass) {
        Descriptors.Descriptor descriptor = DESCRIPTOR_CACHE.computeIfAbsent(
                messageClass,
                clazz -> {
                    try {
                        java.lang.reflect.Method method = clazz.getMethod("getDescriptor");
                        return (Descriptors.Descriptor) method.invoke(null);
                    } catch (Exception e) {
                        throw new IllegalArgumentException("Cannot get descriptor from " + clazz.getName(), e);
                    }
                }
        );
        typeRegistry.put(descriptor.getFullName(), descriptor);
        return this;
    }

    @Override
    public boolean supports(Descriptors.FieldDescriptor field) {
        return field.getJavaType() == Descriptors.FieldDescriptor.JavaType.MESSAGE
                && field.getMessageType().getFullName().equals(ANY_TYPE);
    }

    // ==================== JSON -> Proto ====================

    @Override
    public void read(JsonParser parser, Message.Builder builder,
            Descriptors.FieldDescriptor field) throws IOException {

        JsonToken token = parser.currentToken();

        if (token == JsonToken.VALUE_NULL) {
            return;
        }

        if (token != JsonToken.START_OBJECT) {
            throw new IOException("Any must be a JSON object");
        }

        String typeUrl = null;
        Map<String, Object> fields = new HashMap<>();

        while (parser.nextToken() != JsonToken.END_OBJECT) {
            String fieldName = parser.getCurrentName();
            parser.nextToken();

            if (TYPE_FIELD.equals(fieldName)) {
                typeUrl = parser.getText();
            } else {
                fields.put(fieldName, readJsonValue(parser));
            }
        }

        if (typeUrl == null || typeUrl.isEmpty()) {
            throw new IOException("Any message must have @type field");
        }

        String typeName = typeUrl.startsWith(TYPE_URL_PREFIX)
                ? typeUrl.substring(TYPE_URL_PREFIX.length())
                : typeUrl;

        Descriptors.Descriptor targetDesc = typeRegistry.get(typeName);
        if (targetDesc == null) {
            throw new IOException("Cannot resolve type: " + typeName +
                    ". Register it with AnyConverter.register()");
        }

        Message innerMessage = buildInnerMessage(targetDesc, fields);

        Any any = Any.newBuilder()
                .setTypeUrl(typeUrl)
                .setValue(innerMessage.toByteString())
                .build();

        builder.setField(field, any);
    }

    private Message buildInnerMessage(Descriptors.Descriptor desc,
            Map<String, Object> fields) throws IOException {
        DynamicMessage.Builder msgBuilder = DynamicMessage.newBuilder(desc);

        for (Map.Entry<String, Object> entry : fields.entrySet()) {
            Descriptors.FieldDescriptor fd = desc.findFieldByName(entry.getKey());
            if (fd == null) {
                fd = findFieldByJsonName(desc, entry.getKey());
            }
            if (fd != null) {
                setFieldValue(msgBuilder, fd, entry.getValue());
            }
        }

        return msgBuilder.build();
    }

    private Descriptors.FieldDescriptor findFieldByJsonName(Descriptors.Descriptor desc,
            String jsonName) {
        for (Descriptors.FieldDescriptor fd : desc.getFields()) {
            if (fd.getJsonName().equals(jsonName)) {
                return fd;
            }
        }
        return null;
    }

    private void setFieldValue(DynamicMessage.Builder builder,
            Descriptors.FieldDescriptor fd,
            Object value) throws IOException {
        if (value == null) {
            return;
        }

        if (fd.isRepeated() && !fd.isMapField()) {
            if (value instanceof List<?> list) {
                for (Object item : list) {
                    Object converted = convertSingleValue(fd, item);
                    if (converted != null) {
                        builder.addRepeatedField(fd, converted);
                    }
                }
            }
            return;
        }

        Object converted = convertSingleValue(fd, value);
        if (converted != null) {
            builder.setField(fd, converted);
        }
    }

    private Object convertSingleValue(Descriptors.FieldDescriptor fd, Object value) throws IOException {
        if (value == null) {
            return null;
        }

        return switch (fd.getJavaType()) {
            case STRING -> value.toString();
            case INT -> ((Number) value).intValue();
            case LONG -> ((Number) value).longValue();
            case FLOAT -> ((Number) value).floatValue();
            case DOUBLE -> ((Number) value).doubleValue();
            case BOOLEAN -> (Boolean) value;
            case ENUM -> {
                Descriptors.EnumValueDescriptor ev;
                if (value instanceof Number) {
                    ev = fd.getEnumType().findValueByNumber(((Number) value).intValue());
                } else {
                    ev = fd.getEnumType().findValueByName(value.toString());
                }
                yield ev;
            }
            case BYTE_STRING -> {
                if (value instanceof String) {
                    yield ByteString.copyFrom(Base64.getDecoder().decode((String) value));
                }
                yield null;
            }
            case MESSAGE -> {
                if (value instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> mapValue = (Map<String, Object>) value;
                    yield buildInnerMessage(fd.getMessageType(), mapValue);
                }
                yield null;
            }
        };
    }

    private Object readJsonValue(JsonParser p) throws IOException {
        JsonToken token = p.currentToken();

        return switch (token) {
            case VALUE_NULL -> null;
            case VALUE_TRUE -> true;
            case VALUE_FALSE -> false;
            case VALUE_NUMBER_INT -> p.getLongValue();
            case VALUE_NUMBER_FLOAT -> p.getDoubleValue();
            case VALUE_STRING -> p.getText();
            case START_ARRAY -> {
                List<Object> list = new ArrayList<>();
                while (p.nextToken() != JsonToken.END_ARRAY) {
                    list.add(readJsonValue(p));
                }
                yield list;
            }
            case START_OBJECT -> {
                Map<String, Object> map = new HashMap<>();
                while (p.nextToken() != JsonToken.END_OBJECT) {
                    String name = p.getCurrentName();
                    p.nextToken();
                    map.put(name, readJsonValue(p));
                }
                yield map;
            }
            default -> throw new IOException("Unexpected token: " + token);
        };
    }

    // ==================== Proto -> JSON ====================

    @Override
    public void write(JsonGenerator gen, Message message,
            Descriptors.FieldDescriptor field) throws IOException {
        Message anyMsg = (Message) message.getField(field);

        Descriptors.Descriptor anyDesc = anyMsg.getDescriptorForType();
        String typeUrl = (String) anyMsg.getField(anyDesc.findFieldByName("type_url"));
        ByteString valueBytes = (ByteString) anyMsg.getField(anyDesc.findFieldByName("value"));

        if (typeUrl == null || typeUrl.isEmpty()) {
            gen.writeStartObject();
            gen.writeEndObject();
            return;
        }

        String typeName = typeUrl.startsWith(TYPE_URL_PREFIX)
                ? typeUrl.substring(TYPE_URL_PREFIX.length())
                : typeUrl;

        Descriptors.Descriptor targetDesc = typeRegistry.get(typeName);

        gen.writeStartObject();
        gen.writeStringField(TYPE_FIELD, typeUrl);

        if (targetDesc != null && valueBytes != null && !valueBytes.isEmpty()) {
            try {
                DynamicMessage innerMsg = DynamicMessage.parseFrom(targetDesc, valueBytes);
                writeMessageFields(gen, innerMsg);
            } catch (InvalidProtocolBufferException e) {
                gen.writeStringField("value", Base64.getEncoder().encodeToString(valueBytes.toByteArray()));
            }
        } else if (valueBytes != null && !valueBytes.isEmpty()) {
            gen.writeStringField("value", Base64.getEncoder().encodeToString(valueBytes.toByteArray()));
        }

        gen.writeEndObject();
    }

    private void writeMessageFields(JsonGenerator gen, DynamicMessage msg) throws IOException {
        Descriptors.Descriptor desc = msg.getDescriptorForType();

        for (Descriptors.FieldDescriptor fd : desc.getFields()) {
            if (!fd.isRepeated() && !msg.hasField(fd)) {
                continue;
            }
            if (fd.isRepeated() && msg.getRepeatedFieldCount(fd) == 0) {
                continue;
            }

            gen.writeFieldName(fd.getJsonName());

            if (fd.isRepeated() && !fd.isMapField()) {
                gen.writeStartArray();
                int count = msg.getRepeatedFieldCount(fd);
                for (int i = 0; i < count; i++) {
                    writeJsonValue(gen, msg.getRepeatedField(fd, i), fd);
                }
                gen.writeEndArray();
            } else {
                writeJsonValue(gen, msg.getField(fd), fd);
            }
        }
    }

    private void writeJsonValue(JsonGenerator gen, Object value,
            Descriptors.FieldDescriptor fd) throws IOException {
        if (value == null) {
            gen.writeNull();
            return;
        }

        switch (fd.getJavaType()) {
            case BOOLEAN -> gen.writeBoolean((Boolean) value);
            case INT -> gen.writeNumber((Integer) value);
            case LONG -> gen.writeNumber((Long) value);
            case FLOAT -> gen.writeNumber((Float) value);
            case DOUBLE -> gen.writeNumber((Double) value);
            case STRING -> gen.writeString((String) value);
            case ENUM -> {
                Descriptors.EnumValueDescriptor ev = (Descriptors.EnumValueDescriptor) value;
                gen.writeString(ev.getName());
            }
            case BYTE_STRING -> {
                ByteString bs = (ByteString) value;
                gen.writeString(Base64.getEncoder().encodeToString(bs.toByteArray()));
            }
            case MESSAGE -> {
                if (value instanceof DynamicMessage dynMsg) {
                    gen.writeStartObject();
                    writeMessageFields(gen, dynMsg);
                    gen.writeEndObject();
                } else if (value instanceof Message msg) {
                    gen.writeStartObject();
                    writeMessageFields(gen, DynamicMessage.newBuilder(msg).build());
                    gen.writeEndObject();
                }
            }
        }
    }
}