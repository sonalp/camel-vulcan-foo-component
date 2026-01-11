package org.apache.camel.component.protojson.internal.parser;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.google.protobuf.Descriptors;
import com.google.protobuf.Message;

import org.apache.camel.component.protojson.config.ParserConfig;
import org.apache.camel.component.protojson.converter.JsonInFieldConverter;
import org.apache.camel.component.protojson.converter.JsonInMapConverter;
import org.apache.camel.component.protojson.converter.MessageTypeConverter;
import org.apache.camel.component.protojson.engine.ProtoJsonException;
import org.apache.camel.component.protojson.internal.registry.MetaRegistry.MessageMeta;
import org.apache.camel.component.protojson.internal.registry.MetaRegistry.FieldMeta;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Core streaming JSON parser for converting JSON to Protobuf messages.
 *
 * <p>Zero-DynamicMessage implementation - uses only generated class builders.
 *
 * <p><strong>INTERNAL USE ONLY</strong> - This class is not part of the public API
 * and may change without notice.
 */
public final class ProtoJsonStreamer {

    /**
     * Cache for common integer map keys (0-9999).
     */
    private static final Map<String, Integer> INT_KEY_CACHE;
    private static final Map<String, Long> LONG_KEY_CACHE;

    static {
        INT_KEY_CACHE = new HashMap<>(10000);
        LONG_KEY_CACHE = new HashMap<>(1000);

        for (int i = 0; i < 10000; i++) {
            INT_KEY_CACHE.put(String.valueOf(i), i);
        }
        for (long i = 0; i < 1000; i++) {
            LONG_KEY_CACHE.put(String.valueOf(i), i);
        }
    }

    private ProtoJsonStreamer() {}

    public static void merge(JsonParser p,
            Descriptors.Descriptor desc,
            Message.Builder builder,
            JsonToProtoContext ctx) throws IOException, ProtoJsonException {

        JsonToken t = p.currentToken();
        if (t == null) t = p.nextToken();

        if (t != JsonToken.START_OBJECT) {
            throw new ProtoJsonException(
                    ProtoJsonException.ErrorCode.INVALID_JSON,
                    "Expected START_OBJECT for " + desc.getFullName(),
                    desc.getFullName()
            );
        }

        MessageMeta meta = ctx.getMetaRegistry().metaFor(desc);
        ParserConfig cfg = ctx.getParserConfig();

        while ((t = p.nextToken()) != JsonToken.END_OBJECT) {
            if (t != JsonToken.FIELD_NAME) {
                p.skipChildren();
                continue;
            }

            String fieldName = p.getCurrentName();
            JsonToken valueToken = p.nextToken();

            FieldMeta fm = meta.find(fieldName);
            if (fm == null) {
                if (cfg.isIgnoringUnknownFields()) {
                    p.skipChildren();
                    continue;
                }
                throw new ProtoJsonException(
                        ProtoJsonException.ErrorCode.UNKNOWN_FIELD,
                        "Unknown field: " + fieldName + " in " + desc.getFullName(),
                        desc.getFullName() + "." + fieldName
                );
            }

            Descriptors.FieldDescriptor fd = fm.fd;

            if (valueToken == JsonToken.VALUE_NULL && cfg.isAllowNullForScalars()) {
                continue;
            }

            if (fd.isMapField()) {
                parseMapField(p, valueToken, fm, builder, ctx);
            } else if (fd.isRepeated()) {
                parseRepeatedField(p, valueToken, fm, builder, ctx);
            } else {
                parseSingleField(p, valueToken, fm, builder, ctx);
            }
        }
    }

    // ========== SINGLE FIELD ==========

    private static void parseSingleField(JsonParser p,
            JsonToken t,
            FieldMeta fm,
            Message.Builder builder,
            JsonToProtoContext ctx) throws IOException, ProtoJsonException {

        Descriptors.FieldDescriptor fd = fm.fd;

        // 1. Custom converter
        JsonInFieldConverter converter = ctx.getParserConfig().getInConverterRegistry().findConverter(fd);
        if (converter != null) {
            try {
                converter.read(p, builder, fd);
                return;
            } catch (Exception e) {
                throw new ProtoJsonException(
                        ProtoJsonException.ErrorCode.CUSTOM_CONVERTER_ERROR,
                        "Custom converter failed: " + fd.getFullName(),
                        fd.getFullName(), e
                );
            }
        }

        // 2. Parse and set value
        Object value = parseValue(p, t, fd, ctx);
        builder.setField(fd, value);
    }

    // ========== REPEATED FIELD ==========

    private static void parseRepeatedField(JsonParser p,
            JsonToken t,
            FieldMeta fm,
            Message.Builder builder,
            JsonToProtoContext ctx) throws IOException, ProtoJsonException {

        if (t == JsonToken.START_ARRAY) {
            while ((t = p.nextToken()) != JsonToken.END_ARRAY) {
                addRepeatedElement(p, t, fm, builder, ctx);
            }
        } else {
            addRepeatedElement(p, t, fm, builder, ctx);
        }
    }

    private static void addRepeatedElement(JsonParser p,
            JsonToken t,
            FieldMeta fm,
            Message.Builder builder,
            JsonToProtoContext ctx) throws IOException, ProtoJsonException {

        Descriptors.FieldDescriptor fd = fm.fd;

        // 1. Custom converter
        JsonInFieldConverter converter = ctx.getParserConfig().getInConverterRegistry().findConverter(fd);
        if (converter != null) {
            try {
                converter.read(p, builder, fd);
                return;
            } catch (Exception e) {
                throw new ProtoJsonException(
                        ProtoJsonException.ErrorCode.CUSTOM_CONVERTER_ERROR,
                        "Custom converter failed: " + fd.getFullName(),
                        fd.getFullName(), e
                );
            }
        }

        // 2. Parse and add value
        Object value = parseValue(p, t, fd, ctx);
        builder.addRepeatedField(fd, value);
    }

    // ========== MAP FIELD ==========

    private static void parseMapField(JsonParser p,
            JsonToken t,
            FieldMeta fm,
            Message.Builder builder,
            JsonToProtoContext ctx) throws IOException, ProtoJsonException {

        Descriptors.FieldDescriptor fd = fm.fd;

        if (t != JsonToken.START_OBJECT) {
            throw new ProtoJsonException(
                    ProtoJsonException.ErrorCode.TYPE_MISMATCH,
                    "Map field must be JSON object: " + fd.getFullName(),
                    fd.getFullName()
            );
        }

        Descriptors.Descriptor entryDesc = fd.getMessageType();
        Descriptors.FieldDescriptor keyFd = entryDesc.findFieldByName("key");
        Descriptors.FieldDescriptor valFd = entryDesc.findFieldByName("value");
        ParserConfig cfg = ctx.getParserConfig();

        // Get entry builder from registry (uses generated class if registered)
        MessageMeta entryMeta = ctx.getMetaRegistry().metaFor(entryDesc);

        // Custom converter check
        JsonInMapConverter mapConverter = cfg.getMapConverterRegistry().findConverter(fd);

        if (mapConverter == null) {
            // Standard path: Create map entries using builders from registry
            while ((t = p.nextToken()) != JsonToken.END_OBJECT) {
                if (t != JsonToken.FIELD_NAME) {
                    p.skipChildren();
                    continue;
                }

                String jsonKey = p.getCurrentName();
                JsonToken valToken = p.nextToken();

                if (valToken == JsonToken.VALUE_NULL && cfg.isAllowNullForScalars()) {
                    continue;
                }

                Object key = convertMapKey(jsonKey, keyFd);
                Object value = parseValue(p, valToken, valFd, ctx);

                // Create entry using builder from MetaRegistry
                Message.Builder entryBuilder = entryMeta.newBuilder();
                entryBuilder.setField(keyFd, key);
                entryBuilder.setField(valFd, value);
                builder.addRepeatedField(fd, entryBuilder.build());
            }
        } else {
            // Custom converter path
            Message.Builder entryBuilder = entryMeta.newBuilder();

            while ((t = p.nextToken()) != JsonToken.END_OBJECT) {
                if (t != JsonToken.FIELD_NAME) {
                    p.skipChildren();
                    continue;
                }

                String jsonKey = p.getCurrentName();
                JsonToken valToken = p.nextToken();

                Object keyValue = convertMapKey(jsonKey, keyFd);
                entryBuilder.clear();
                entryBuilder.setField(keyFd, keyValue);

                if (valToken == JsonToken.VALUE_NULL && cfg.isAllowNullForScalars()) {
                    continue;
                }

                try {
                    Object convertedValue = mapConverter.readValue(p, fd, valFd, keyValue);
                    entryBuilder.setField(valFd, convertedValue);
                    builder.addRepeatedField(fd, entryBuilder.build());
                } catch (Exception e) {
                    throw new ProtoJsonException(
                            ProtoJsonException.ErrorCode.CUSTOM_CONVERTER_ERROR,
                            "Custom map converter failed: " + fd.getFullName(),
                            fd.getFullName(), e
                    );
                }
            }
        }
    }

    // ========== VALUE PARSING ==========

    private static Object parseValue(JsonParser p,
            JsonToken t,
            Descriptors.FieldDescriptor fd,
            JsonToProtoContext ctx) throws IOException, ProtoJsonException {

        return switch (fd.getJavaType()) {
            case STRING -> {
                if (!t.isScalarValue()) throw typeMismatch(fd, "string");
                yield p.getValueAsString();
            }
            case INT -> {
                if (t.isNumeric()) yield p.getIntValue();
                if (t.isScalarValue()) yield Integer.parseInt(p.getValueAsString());
                throw typeMismatch(fd, "int32");
            }
            case LONG -> {
                if (t.isNumeric()) yield p.getLongValue();
                if (t.isScalarValue()) yield Long.parseLong(p.getValueAsString());
                throw typeMismatch(fd, "int64");
            }
            case DOUBLE -> {
                if (t.isNumeric()) yield p.getDoubleValue();
                if (t.isScalarValue()) yield Double.parseDouble(p.getValueAsString());
                throw typeMismatch(fd, "double");
            }
            case FLOAT -> {
                if (t.isNumeric()) yield (float) p.getDoubleValue();
                if (t.isScalarValue()) yield Float.parseFloat(p.getValueAsString());
                throw typeMismatch(fd, "float");
            }
            case BOOLEAN -> {
                if (t == JsonToken.VALUE_TRUE || t == JsonToken.VALUE_FALSE) yield p.getBooleanValue();
                if (t.isScalarValue()) yield Boolean.parseBoolean(p.getValueAsString());
                throw typeMismatch(fd, "bool");
            }
            case BYTE_STRING -> {
                if (!t.isScalarValue()) throw typeMismatch(fd, "bytes (base64)");
                String base64 = p.getValueAsString();
                try {
                    byte[] decoded = java.util.Base64.getDecoder().decode(base64);
                    yield com.google.protobuf.ByteString.copyFrom(decoded);
                } catch (IllegalArgumentException e) {
                    throw new ProtoJsonException(
                            ProtoJsonException.ErrorCode.TYPE_MISMATCH,
                            "Invalid Base64 for bytes: " + fd.getFullName(),
                            fd.getFullName(), e
                    );
                }
            }
            case ENUM -> parseEnumValue(p, t, fd, ctx.getParserConfig());
            case MESSAGE -> {
                // Use generated builder from registry
                MessageMeta nestedMeta = ctx.getMetaRegistry().metaFor(fd.getMessageType());
                Message.Builder nestedBuilder = nestedMeta.newBuilder();
                Descriptors.Descriptor nestedDesc = fd.getMessageType();
                MessageTypeConverter conv = ctx.getRegistry().get(nestedDesc);
                conv.mergeInto(p, nestedDesc, nestedBuilder, ctx);
                yield nestedBuilder.build();
            }
        };
    }

    private static Descriptors.EnumValueDescriptor parseEnumValue(
            JsonParser p,
            JsonToken t,
            Descriptors.FieldDescriptor fd,
            ParserConfig cfg) throws IOException, ProtoJsonException {

        Descriptors.EnumDescriptor enumDesc = fd.getEnumType();
        Descriptors.EnumValueDescriptor ev = null;

        if (t.isNumeric() && cfg.isAcceptNumericEnums()) {
            int number = p.getIntValue();
            ev = enumDesc.findValueByNumber(number);
        } else if (t.isScalarValue()) {
            String name = p.getValueAsString();
            ev = enumDesc.findValueByName(name);
        }

        if (ev == null) {
            throw new ProtoJsonException(
                    ProtoJsonException.ErrorCode.INVALID_ENUM_VALUE,
                    "Invalid enum value for " + fd.getFullName(),
                    fd.getFullName()
            );
        }

        return ev;
    }

    // ========== HELPERS ==========

    private static Object convertMapKey(String jsonKey,
            Descriptors.FieldDescriptor keyFd) throws ProtoJsonException {
        return switch (keyFd.getJavaType()) {
            case STRING -> jsonKey;
            case INT -> {
                Integer cached = INT_KEY_CACHE.get(jsonKey);
                if (cached != null) yield cached;
                try {
                    yield Integer.parseInt(jsonKey);
                } catch (NumberFormatException e) {
                    throw new ProtoJsonException(
                            ProtoJsonException.ErrorCode.TYPE_MISMATCH,
                            "Invalid int32 map key: " + jsonKey,
                            keyFd.getFullName(), e
                    );
                }
            }
            case LONG -> {
                Long cached = LONG_KEY_CACHE.get(jsonKey);
                if (cached != null) yield cached;
                try {
                    yield Long.parseLong(jsonKey);
                } catch (NumberFormatException e) {
                    throw new ProtoJsonException(
                            ProtoJsonException.ErrorCode.TYPE_MISMATCH,
                            "Invalid int64 map key: " + jsonKey,
                            keyFd.getFullName(), e
                    );
                }
            }
            case BOOLEAN -> {
                if ("true".equalsIgnoreCase(jsonKey) || "false".equalsIgnoreCase(jsonKey)) {
                    yield Boolean.parseBoolean(jsonKey);
                }
                throw new ProtoJsonException(
                        ProtoJsonException.ErrorCode.TYPE_MISMATCH,
                        "Invalid bool map key: " + jsonKey,
                        keyFd.getFullName()
                );
            }
            default -> throw new ProtoJsonException(
                    ProtoJsonException.ErrorCode.TYPE_MISMATCH,
                    "Unsupported map key type: " + keyFd.getJavaType(),
                    keyFd.getFullName()
            );
        };
    }

    private static ProtoJsonException typeMismatch(Descriptors.FieldDescriptor fd, String expected) {
        return new ProtoJsonException(
                ProtoJsonException.ErrorCode.TYPE_MISMATCH,
                "Expected " + expected + " for " + fd.getFullName(),
                fd.getFullName()
        );
    }
}
