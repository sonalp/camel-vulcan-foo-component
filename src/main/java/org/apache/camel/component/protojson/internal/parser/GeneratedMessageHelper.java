package org.apache.camel.component.protojson.internal.parser;

import com.google.protobuf.Descriptors;
import com.google.protobuf.Message;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MethodHandle-based optimizer for protoc-generated message classes.
 *
 * <p>Uses cached MethodHandles to invoke native builder methods (setXxx, addXxx, putXxx)
 * providing near-native performance instead of generic reflection-based operations.
 *
 * <p><strong>INTERNAL USE ONLY</strong>
 */
final class GeneratedMessageHelper {

    private static final ConcurrentHashMap<String, MethodHandle> METHOD_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Class<?>, Boolean> GENERATED_CLASS_CACHE = new ConcurrentHashMap<>();

    private GeneratedMessageHelper() {}

    /**
     * Sets a single field value using native setXxx() method.
     * @return true if successful, false to use fallback
     */
    static boolean setField(Message.Builder builder, Descriptors.FieldDescriptor fd, Object value) {
        if (!isGenerated(builder)) return false;

        MethodHandle mh = getOrCacheSetter(builder, fd);
        if (mh == null) return false;

        try {
            mh.invoke(builder, value);
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    /**
     * Adds a repeated field element using native addXxx() method.
     * @return true if successful, false to use fallback
     */
    static boolean addRepeated(Message.Builder builder, Descriptors.FieldDescriptor fd, Object value) {
        if (!isGenerated(builder)) return false;

        MethodHandle mh = getOrCacheAdder(builder, fd);
        if (mh == null) return false;

        try {
            mh.invoke(builder, value);
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    /**
     * Puts a map entry using native putXxx(K, V) method.
     * @return true if successful, false to use fallback
     */
    static boolean putMap(Message.Builder builder, Descriptors.FieldDescriptor fd, Object key, Object value) {
        if (!isGenerated(builder)) return false;

        MethodHandle mh = getOrCacheMapPutter(builder, fd);
        if (mh == null) return false;

        try {
            mh.invoke(builder, key, value);
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    // ========== PRIVATE HELPERS ==========

    private static boolean isGenerated(Message.Builder builder) {
        if (builder == null) return false;

        return GENERATED_CLASS_CACHE.computeIfAbsent(builder.getClass(), cls -> {
            Class<?> current = cls;
            while (current != null) {
                String name = current.getName();
                if (name.startsWith("com.google.protobuf.GeneratedMessageV3") ||
                    name.contains("$Builder")) {
                    return true;
                }
                current = current.getSuperclass();
            }
            return false;
        });
    }

    private static MethodHandle getOrCacheSetter(Message.Builder builder, Descriptors.FieldDescriptor fd) {
        String methodName = "set" + toCamelCase(fd.getName());
        String key = builder.getClass().getName() + "#" + methodName;

        return METHOD_CACHE.computeIfAbsent(key, k ->
            findMethod(builder.getClass(), methodName, getJavaClass(fd)));
    }

    private static MethodHandle getOrCacheAdder(Message.Builder builder, Descriptors.FieldDescriptor fd) {
        String methodName = "add" + toCamelCase(fd.getName());
        String key = builder.getClass().getName() + "#" + methodName;

        return METHOD_CACHE.computeIfAbsent(key, k ->
            findMethod(builder.getClass(), methodName, getJavaClass(fd)));
    }

    private static MethodHandle getOrCacheMapPutter(Message.Builder builder, Descriptors.FieldDescriptor fd) {
        String methodName = "put" + toCamelCase(fd.getName());
        String key = builder.getClass().getName() + "#" + methodName + "_map";

        return METHOD_CACHE.computeIfAbsent(key, k -> {
            Descriptors.Descriptor entryDesc = fd.getMessageType();
            Descriptors.FieldDescriptor keyFd = entryDesc.findFieldByName("key");
            Descriptors.FieldDescriptor valFd = entryDesc.findFieldByName("value");

            Class<?> keyClass = getJavaClass(keyFd);
            Class<?> valClass = getJavaClass(valFd);

            try {
                MethodHandles.Lookup lookup = MethodHandles.lookup();
                MethodType type = MethodType.methodType(builder.getClass(), keyClass, valClass);
                return lookup.findVirtual(builder.getClass(), methodName, type);
            } catch (Exception e) {
                return null;
            }
        });
    }

    private static MethodHandle findMethod(Class<?> builderClass, String methodName, Class<?> paramType) {
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            MethodType type = MethodType.methodType(builderClass, paramType);
            return lookup.findVirtual(builderClass, methodName, type);
        } catch (Exception e) {
            return null;
        }
    }

    private static String toCamelCase(String protoFieldName) {
        if (protoFieldName == null || protoFieldName.isEmpty()) {
            return protoFieldName;
        }

        StringBuilder result = new StringBuilder();
        boolean capitalizeNext = true;

        for (int i = 0; i < protoFieldName.length(); i++) {
            char c = protoFieldName.charAt(i);

            if (c == '_') {
                capitalizeNext = true;
            } else if (capitalizeNext) {
                result.append(Character.toUpperCase(c));
                capitalizeNext = false;
            } else {
                result.append(c);
            }
        }

        return result.toString();
    }

    private static Class<?> getJavaClass(Descriptors.FieldDescriptor fd) {
        return switch (fd.getJavaType()) {
            case INT -> Integer.class;
            case LONG -> Long.class;
            case FLOAT -> Float.class;
            case DOUBLE -> Double.class;
            case BOOLEAN -> Boolean.class;
            case STRING -> String.class;
            case BYTE_STRING -> com.google.protobuf.ByteString.class;
            case ENUM -> com.google.protobuf.Descriptors.EnumValueDescriptor.class;
            case MESSAGE -> {
                // For MESSAGE type, we need to return Message class
                // The actual type will be checked at runtime
                yield com.google.protobuf.Message.class;
            }
        };
    }

    static void clearCaches() {
        METHOD_CACHE.clear();
        GENERATED_CLASS_CACHE.clear();
    }
}
