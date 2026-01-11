package org.apache.camel.component.protojson.internal.parser;

import com.google.protobuf.Descriptors;
import com.google.protobuf.Message;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Helper for accessing native methods on generated message builders.
 * Uses getMutableXxx() for map fields to achieve native performance.
 *
 * <p><strong>INTERNAL USE ONLY</strong>
 */
final class GeneratedMessageHelper {

    private static final ConcurrentHashMap<String, MethodHandle> METHOD_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Class<?>, Boolean> GENERATED_CLASS_CACHE = new ConcurrentHashMap<>();

    private GeneratedMessageHelper() {}

    /**
     * Tries to get mutable map for direct put operations.
     * @return Map<K,V> if generated class, null otherwise
     */
    @SuppressWarnings("unchecked")
    static <K, V> Map<K, V> getMutableMap(Message.Builder builder, Descriptors.FieldDescriptor fd) {
        if (!isGenerated(builder)) return null;

        MethodHandle mh = getOrCacheMapGetter(builder, fd);
        if (mh == null) return null;

        try {
            return (Map<K, V>) mh.invoke(builder);
        } catch (Throwable e) {
            return null;
        }
    }

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

    private static MethodHandle getOrCacheMapGetter(Message.Builder builder, Descriptors.FieldDescriptor fd) {
        String methodName = "getMutable" + toCamelCase(fd.getName());
        String key = builder.getClass().getName() + "#" + methodName;

        return METHOD_CACHE.computeIfAbsent(key, k -> {
            try {
                MethodHandles.Lookup lookup = MethodHandles.lookup();
                MethodType type = MethodType.methodType(java.util.Map.class);
                return lookup.findVirtual(builder.getClass(), methodName, type);
            } catch (Exception e) {
                return null;
            }
        });
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

    static void clearCaches() {
        METHOD_CACHE.clear();
        GENERATED_CLASS_CACHE.clear();
    }
}
