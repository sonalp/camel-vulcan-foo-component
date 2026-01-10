package org.apache.camel.component.protojson.internal.parser;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.Message;

import org.apache.camel.component.protojson.config.ParserConfig;
import org.apache.camel.component.protojson.converter.JsonInFieldConverter;
import org.apache.camel.component.protojson.converter.JsonInMapConverter;
import org.apache.camel.component.protojson.converter.MessageTypeConverter;
import org.apache.camel.component.protojson.engine.ProtoJsonException;
import org.apache.camel.component.protojson.internal.registry.FieldConverterRegistry;
import org.apache.camel.component.protojson.internal.registry.MetaRegistry.MessageMeta;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Core streaming JSON parser for converting JSON to Protobuf messages.
 *
 * <p><strong>INTERNAL USE ONLY</strong> - This class is not part of the public API
 * and may change without notice.
 */
public final class ProtoJsonStreamer {

    /**
     * Cache for common integer map keys (0-9999).
     * Avoids Integer.parseInt() allocation overhead for frequently used keys.
     */
    private static final Map<String, Integer> INT_KEY_CACHE;
    private static final Map<String, Long> LONG_KEY_CACHE;

    static {
        // Pre-populate cache with common integer keys
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

            MessageMeta.FieldMeta fm = meta.find(fieldName);
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
                // skip, field not set
                continue;
            }

            if (fd.isMapField()) {
                parseMapField(p, valueToken, fd, builder, ctx);
            } else if (fd.isRepeated()) {
                parseRepeatedField(p, valueToken, fd, builder, ctx);
            } else {
                parseSingleField(p, valueToken, fd, builder, ctx);
            }
        }
    }

    // ---------- single / repeated / map ----------

    private static void parseSingleField(JsonParser p,
            JsonToken t,
            Descriptors.FieldDescriptor fd,
            Message.Builder builder,
            JsonToProtoContext ctx) throws IOException, ProtoJsonException {

        // Try custom converter first - O(1) lookup with caching
        ParserConfig cfg = ctx.getParserConfig();
        FieldConverterRegistry<JsonInFieldConverter> registry = cfg.getInConverterRegistry();
        JsonInFieldConverter converter = registry.findConverter(fd);

        if (converter != null) {
            try {
                converter.read(p, builder, fd);
                return;
            } catch (Exception e) {
                throw new ProtoJsonException(
                        ProtoJsonException.ErrorCode.CUSTOM_CONVERTER_ERROR,
                        "Custom converter failed for field: " + fd.getFullName(),
                        fd.getFullName(),
                        e
                );
            }
        }

        // Default handling
        switch (fd.getJavaType()) {
            case STRING -> {
                if (t.isScalarValue()) {
                    builder.setField(fd, p.getValueAsString());
                }
            }
            case INT -> {
                int v;
                if (t.isNumeric()) {
                    v = p.getIntValue();
                } else if (t.isScalarValue()) {
                    v = Integer.parseInt(p.getValueAsString());
                } else {
                    throw typeMismatch(fd, "int32");
                }
                builder.setField(fd, v);
            }
            case LONG -> {
                long v;
                if (t.isNumeric()) {
                    v = p.getLongValue();
                } else if (t.isScalarValue()) {
                    v = Long.parseLong(p.getValueAsString());
                } else {
                    throw typeMismatch(fd, "int64");
                }
                builder.setField(fd, v);
            }
            case DOUBLE -> {
                double v;
                if (t.isNumeric()) {
                    v = p.getDoubleValue();
                } else if (t.isScalarValue()) {
                    v = Double.parseDouble(p.getValueAsString());
                } else {
                    throw typeMismatch(fd, "double");
                }
                builder.setField(fd, v);
            }
            case FLOAT -> {
                float v;
                if (t.isNumeric()) {
                    v = (float) p.getDoubleValue();
                } else if (t.isScalarValue()) {
                    v = Float.parseFloat(p.getValueAsString());
                } else {
                    throw typeMismatch(fd, "float");
                }
                builder.setField(fd, v);
            }
            case BOOLEAN -> {
                boolean v;
                if (t == JsonToken.VALUE_TRUE || t == JsonToken.VALUE_FALSE) {
                    v = p.getBooleanValue();
                } else if (t.isScalarValue()) {
                    v = Boolean.parseBoolean(p.getValueAsString());
                } else {
                    throw typeMismatch(fd, "bool");
                }
                builder.setField(fd, v);
            }
            case BYTE_STRING -> {
                if (t.isScalarValue()) {
                    String base64 = p.getValueAsString();
                    try {
                        byte[] decoded = java.util.Base64.getDecoder().decode(base64);
                        builder.setField(fd, com.google.protobuf.ByteString.copyFrom(decoded));
                    } catch (IllegalArgumentException e) {
                        throw new ProtoJsonException(
                                ProtoJsonException.ErrorCode.TYPE_MISMATCH,
                                "Invalid Base64 for bytes field: " + fd.getFullName(),
                                fd.getFullName(),
                                e
                        );
                    }
                } else {
                    throw typeMismatch(fd, "bytes (base64 string)");
                }
            }
            case ENUM -> parseEnumField(p, t, fd, builder, ctx.getParserConfig());
            case MESSAGE -> {
                Message.Builder nestedBuilder = builder.newBuilderForField(fd);
                Descriptors.Descriptor nestedDesc = fd.getMessageType();
                MessageTypeConverter conv = ctx.getRegistry().get(nestedDesc);
                conv.mergeInto(p, nestedDesc, nestedBuilder, ctx);
                builder.setField(fd, nestedBuilder.build());
            }
        }
    }

    private static void parseEnumField(JsonParser p,
            JsonToken t,
            Descriptors.FieldDescriptor fd,
            Message.Builder builder,
            ParserConfig cfg) throws IOException, ProtoJsonException {

        Descriptors.EnumValueDescriptor ev = parseEnumValue(p, t, fd, cfg);
        builder.setField(fd, ev);
    }

    /**
     * Parse enum value and return the descriptor directly.
     * Used for repeated enum fields to avoid unnecessary DynamicMessage creation.
     */
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

    private static void parseRepeatedField(JsonParser p,
            JsonToken t,
            Descriptors.FieldDescriptor fd,
            Message.Builder builder,
            JsonToProtoContext ctx) throws IOException, ProtoJsonException {

        if (t == JsonToken.START_ARRAY) {
            while ((t = p.nextToken()) != JsonToken.END_ARRAY) {
                addRepeatedElement(p, t, fd, builder, ctx);
            }
        } else {
            // single value -> treat as single-element array
            addRepeatedElement(p, t, fd, builder, ctx);
        }
    }

    private static void addRepeatedElement(JsonParser p,
            JsonToken t,
            Descriptors.FieldDescriptor fd,
            Message.Builder builder,
            JsonToProtoContext ctx) throws IOException, ProtoJsonException {

        // Try custom converter first - O(1) lookup with caching
        ParserConfig cfg = ctx.getParserConfig();
        FieldConverterRegistry<JsonInFieldConverter> registry = cfg.getInConverterRegistry();
        JsonInFieldConverter converter = registry.findConverter(fd);

        if (converter != null) {
            try {
                // For repeated fields, custom converter should add to repeated field
                converter.read(p, builder, fd);
                return;
            } catch (Exception e) {
                throw new ProtoJsonException(
                        ProtoJsonException.ErrorCode.CUSTOM_CONVERTER_ERROR,
                        "Custom converter failed for repeated field: " + fd.getFullName(),
                        fd.getFullName(),
                        e
                );
            }
        }

        // Default handling
        switch (fd.getJavaType()) {
            case STRING  -> builder.addRepeatedField(fd, p.getValueAsString());
            case INT     -> builder.addRepeatedField(fd,
                    t.isNumeric() ? p.getIntValue() : Integer.parseInt(p.getValueAsString()));
            case LONG    -> builder.addRepeatedField(fd,
                    t.isNumeric() ? p.getLongValue() : Long.parseLong(p.getValueAsString()));
            case DOUBLE  -> builder.addRepeatedField(fd,
                    t.isNumeric() ? p.getDoubleValue() : Double.parseDouble(p.getValueAsString()));
            case FLOAT   -> builder.addRepeatedField(fd,
                    t.isNumeric() ? (float) p.getDoubleValue() : Float.parseFloat(p.getValueAsString()));
            case BOOLEAN -> builder.addRepeatedField(fd,
                    t == JsonToken.VALUE_TRUE
                            || (t.isScalarValue() && Boolean.parseBoolean(p.getValueAsString())));
            case BYTE_STRING -> {
                String base64 = p.getValueAsString();
                try {
                    byte[] decoded = java.util.Base64.getDecoder().decode(base64);
                    builder.addRepeatedField(fd, com.google.protobuf.ByteString.copyFrom(decoded));
                } catch (IllegalArgumentException e) {
                    throw new ProtoJsonException(
                            ProtoJsonException.ErrorCode.TYPE_MISMATCH,
                            "Invalid Base64 for repeated bytes field: " + fd.getFullName(),
                            fd.getFullName(),
                            e
                    );
                }
            }
            case ENUM -> {
                Descriptors.EnumValueDescriptor ev = parseEnumValue(p, t, fd, ctx.getParserConfig());
                builder.addRepeatedField(fd, ev);
            }
            case MESSAGE -> {
                Message.Builder nestedBuilder = builder.newBuilderForField(fd);
                Descriptors.Descriptor nestedDesc = fd.getMessageType();
                MessageTypeConverter conv = ctx.getRegistry().get(nestedDesc);
                conv.mergeInto(p, nestedDesc, nestedBuilder, ctx);
                builder.addRepeatedField(fd, nestedBuilder.build());
            }
        }
    }

    private static void parseMapField(JsonParser p,
            JsonToken t,
            Descriptors.FieldDescriptor fd,
            Message.Builder builder,
            JsonToProtoContext ctx) throws IOException, ProtoJsonException {

        Descriptors.Descriptor entryDesc = fd.getMessageType();
        Descriptors.FieldDescriptor keyFd = entryDesc.findFieldByName("key");
        Descriptors.FieldDescriptor valFd = entryDesc.findFieldByName("value");

        if (t != JsonToken.START_OBJECT) {
            throw new ProtoJsonException(
                    ProtoJsonException.ErrorCode.TYPE_MISMATCH,
                    "Map field " + fd.getFullName() + " must be JSON object",
                    fd.getFullName()
            );
        }

        // Check for custom map converter
        ParserConfig cfg = ctx.getParserConfig();
        FieldConverterRegistry<JsonInMapConverter> mapRegistry = cfg.getMapConverterRegistry();
        JsonInMapConverter mapConverter = mapRegistry.findConverter(fd);

        // OPTIMIZATION: Use generated class's native putXxx() method when available
        // This is 5-10x faster than DynamicMessage entry creation
        if (GeneratedMessageHelper.isGeneratedBuilder(builder) && mapConverter == null) {
            parseMapFieldFast(p, t, fd, keyFd, valFd, builder, ctx);
            return;
        }

        // Fallback: Use DynamicMessage entry builder (for dynamic messages or custom converters)
        parseMapFieldDynamic(p, t, fd, keyFd, valFd, entryDesc, builder, ctx, mapConverter);
    }

    /**
     * Fast path for generated message classes: uses native putXxx(K, V) methods.
     * Avoids DynamicMessage entry allocation entirely.
     */
    private static void parseMapFieldFast(JsonParser p,
            JsonToken t,
            Descriptors.FieldDescriptor fd,
            Descriptors.FieldDescriptor keyFd,
            Descriptors.FieldDescriptor valFd,
            Message.Builder builder,
            JsonToProtoContext ctx) throws IOException, ProtoJsonException {

        ParserConfig cfg = ctx.getParserConfig();

        // Get MethodHandle for putXxx(K, V) method
        Class<?> keyClass = GeneratedMessageHelper.getJavaClass(keyFd);
        Class<?> valClass = GeneratedMessageHelper.getJavaClass(valFd);
        java.lang.invoke.MethodHandle putMethod = GeneratedMessageHelper.getMapPutMethod(
                builder, fd, keyClass, valClass);

        // If putMethod not found, fall back to dynamic path
        if (putMethod == null) {
            Descriptors.Descriptor entryDesc = fd.getMessageType();
            parseMapFieldDynamic(p, t, fd, keyFd, valFd, entryDesc, builder, ctx, null);
            return;
        }

        // Parse map entries using native putXxx() method
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

            Object keyValue = convertMapKey(jsonKey, keyFd);
            Object value = parseMapValue(p, valToken, valFd, ctx);

            // Direct method call: builder.putXxx(keyValue, value)
            try {
                putMethod.invoke(builder, keyValue, value);
            } catch (Throwable e) {
                throw new ProtoJsonException(
                        ProtoJsonException.ErrorCode.TYPE_MISMATCH,
                        "Failed to set map field: " + fd.getFullName(),
                        fd.getFullName(),
                        e
                );
            }
        }
    }

    /**
     * Parses a map value (non-key part of map entry).
     * Handles all protobuf types including nested messages.
     */
    private static Object parseMapValue(JsonParser p,
            JsonToken t,
            Descriptors.FieldDescriptor valFd,
            JsonToProtoContext ctx) throws IOException, ProtoJsonException {

        return switch (valFd.getJavaType()) {
            case STRING -> p.getValueAsString();
            case INT -> t.isNumeric() ? p.getIntValue() : Integer.parseInt(p.getValueAsString());
            case LONG -> t.isNumeric() ? p.getLongValue() : Long.parseLong(p.getValueAsString());
            case DOUBLE -> t.isNumeric() ? p.getDoubleValue() : Double.parseDouble(p.getValueAsString());
            case FLOAT -> t.isNumeric() ? (float) p.getDoubleValue() : Float.parseFloat(p.getValueAsString());
            case BOOLEAN -> t == JsonToken.VALUE_TRUE
                    || (t.isScalarValue() && Boolean.parseBoolean(p.getValueAsString()));
            case BYTE_STRING -> {
                String base64 = p.getValueAsString();
                try {
                    byte[] decoded = java.util.Base64.getDecoder().decode(base64);
                    yield com.google.protobuf.ByteString.copyFrom(decoded);
                } catch (IllegalArgumentException e) {
                    throw new ProtoJsonException(
                            ProtoJsonException.ErrorCode.TYPE_MISMATCH,
                            "Invalid Base64 for bytes map value: " + valFd.getFullName(),
                            valFd.getFullName(),
                            e
                    );
                }
            }
            case ENUM -> parseEnumValue(p, t, valFd, ctx.getParserConfig());
            case MESSAGE -> {
                // For nested messages in map values, we need to parse them
                Message.Builder nestedBuilder = createBuilderForField(valFd, ctx);
                Descriptors.Descriptor nestedDesc = valFd.getMessageType();
                MessageTypeConverter conv = ctx.getRegistry().get(nestedDesc);
                conv.mergeInto(p, nestedDesc, nestedBuilder, ctx);
                yield nestedBuilder.build();
            }
        };
    }

    /**
     * Creates a builder for a field. Uses generated class when available.
     */
    private static Message.Builder createBuilderForField(
            Descriptors.FieldDescriptor fd,
            JsonToProtoContext ctx) {
        // TODO: Could optimize this to use generated class builders too
        return DynamicMessage.newBuilder(fd.getMessageType());
    }

    /**
     * Fallback path: uses DynamicMessage entry builder.
     * Used for DynamicMessage or when custom converter is present.
     */
    private static void parseMapFieldDynamic(JsonParser p,
            JsonToken t,
            Descriptors.FieldDescriptor fd,
            Descriptors.FieldDescriptor keyFd,
            Descriptors.FieldDescriptor valFd,
            Descriptors.Descriptor entryDesc,
            Message.Builder builder,
            JsonToProtoContext ctx,
            JsonInMapConverter mapConverter) throws IOException, ProtoJsonException {

        ParserConfig cfg = ctx.getParserConfig();

        // OPTIMIZATION: Reuse entry builder to avoid allocation per entry
        // This reduces heap pressure significantly for large maps (1000+ entries)
        Message.Builder entryBuilder = DynamicMessage.newBuilder(entryDesc);

        while ((t = p.nextToken()) != JsonToken.END_OBJECT) {
            if (t != JsonToken.FIELD_NAME) {
                p.skipChildren();
                continue;
            }

            String jsonKey = p.getCurrentName();
            JsonToken valToken = p.nextToken();

            Object keyValue = convertMapKey(jsonKey, keyFd);

            // OPTIMIZATION: Clear builder instead of creating new one
            entryBuilder.clear();
            entryBuilder.setField(keyFd, keyValue);

            if (valToken == JsonToken.VALUE_NULL && cfg.isAllowNullForScalars()) {
                continue;
            }

            // Custom converter varsa kullan
            if (mapConverter != null) {
                try {
                    Object convertedValue = mapConverter.readValue(p, fd, valFd, keyValue);
                    entryBuilder.setField(valFd, convertedValue);
                    builder.addRepeatedField(fd, entryBuilder.build());
                    continue;
                } catch (Exception e) {
                    throw new ProtoJsonException(
                            ProtoJsonException.ErrorCode.CUSTOM_CONVERTER_ERROR,
                            "Custom map converter failed for field: " + fd.getFullName(),
                            fd.getFullName(),
                            e
                    );
                }
            }

            // Default handling
            if (valFd.getJavaType() == Descriptors.FieldDescriptor.JavaType.MESSAGE) {
                Message.Builder nestedBuilder = entryBuilder.newBuilderForField(valFd);
                Descriptors.Descriptor nestedDesc = valFd.getMessageType();
                MessageTypeConverter conv = ctx.getRegistry().get(nestedDesc);
                conv.mergeInto(p, nestedDesc, nestedBuilder, ctx);
                entryBuilder.setField(valFd, nestedBuilder.build());
            } else {
                parseSingleField(p, valToken, valFd, entryBuilder, ctx);
            }

            builder.addRepeatedField(fd, entryBuilder.build());
        }
    }

    private static Object convertMapKey(String jsonKey,
            Descriptors.FieldDescriptor keyFd) throws ProtoJsonException {
        return switch (keyFd.getJavaType()) {
            case STRING -> jsonKey;
            case INT -> {
                // OPTIMIZATION: Use cache for common integer keys (0-9999)
                // Avoids Integer.parseInt() and boxing allocation
                Integer cached = INT_KEY_CACHE.get(jsonKey);
                if (cached != null) {
                    yield cached;
                }
                // Fallback for uncommon keys
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
                // OPTIMIZATION: Use cache for common long keys (0-999)
                Long cached = LONG_KEY_CACHE.get(jsonKey);
                if (cached != null) {
                    yield cached;
                }
                // Fallback for uncommon keys
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