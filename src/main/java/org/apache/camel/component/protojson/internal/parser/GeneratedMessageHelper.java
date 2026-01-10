package org.apache.camel.component.protojson.internal.parser;

import com.google.protobuf.Descriptors;
import com.google.protobuf.GeneratedMessageV3;
import com.google.protobuf.Message;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Helper for optimizing operations on protoc-generated message classes.
 *
 * <p>Provides fast-path optimizations by using native builder methods
 * instead of generic Message.Builder reflection-based operations.
 *
 * <p><strong>INTERNAL USE ONLY</strong> - This class is not part of the public API
 * and may change without notice.
 */
final class GeneratedMessageHelper {

    /**
     * Cache for map field put methods.
     * Key format: "ClassName#putFieldName"
     * Value: MethodHandle for putFieldName(K key, V value) method
     */
    private static final ConcurrentHashMap<String, MethodHandle> MAP_PUT_CACHE
        = new ConcurrentHashMap<>();

    /**
     * Cache for checking if a builder class is generated.
     * Avoids repeated instanceof checks.
     */
    private static final ConcurrentHashMap<Class<?>, Boolean> IS_GENERATED_CACHE
        = new ConcurrentHashMap<>();

    private GeneratedMessageHelper() {
        // Utility class
    }

    /**
     * Checks if the given builder is from a protoc-generated class.
     * Generated classes extend GeneratedMessageV3.Builder.
     *
     * @param builder the message builder to check
     * @return true if the builder is from a generated class
     */
    static boolean isGeneratedBuilder(Message.Builder builder) {
        if (builder == null) {
            return false;
        }

        Class<?> builderClass = builder.getClass();
        return IS_GENERATED_CACHE.computeIfAbsent(builderClass, cls -> {
            // Check if this builder extends GeneratedMessageV3.Builder
            Class<?> current = cls;
            while (current != null && current != Object.class) {
                if (current.getName().startsWith("com.google.protobuf.GeneratedMessageV3$Builder")) {
                    return true;
                }
                // Check interfaces and superclass
                for (Class<?> iface : current.getInterfaces()) {
                    if (iface.getName().contains("GeneratedMessageV3")) {
                        return true;
                    }
                }
                current = current.getSuperclass();
            }
            return false;
        });
    }

    /**
     * Gets a cached MethodHandle for the map field's native put method.
     *
     * <p>For a map field "string_meta", the put method would be "putStringMeta(String, String)".
     * Using the native put method is much faster than creating DynamicMessage entries.
     *
     * @param builder the generated message builder
     * @param fieldDescriptor the map field descriptor
     * @param keyType the Java type of the map key (Integer, Long, Boolean, String)
     * @param valueType the Java type of the map value
     * @return MethodHandle for the put method, or null if not found
     */
    static MethodHandle getMapPutMethod(
            Message.Builder builder,
            Descriptors.FieldDescriptor fieldDescriptor,
            Class<?> keyType,
            Class<?> valueType) {

        Class<?> builderClass = builder.getClass();
        String methodName = "put" + toCamelCase(fieldDescriptor.getName());
        String cacheKey = builderClass.getName() + "#" + methodName;

        return MAP_PUT_CACHE.computeIfAbsent(cacheKey, key -> {
            try {
                // Find the putXxx(K, V) method
                MethodHandles.Lookup lookup = MethodHandles.lookup();
                MethodType methodType = MethodType.methodType(
                    builderClass, // Returns builder for chaining
                    keyType,
                    valueType
                );

                return lookup.findVirtual(builderClass, methodName, methodType);
            } catch (NoSuchMethodException | IllegalAccessException e) {
                // Method not found - this can happen for:
                // 1. DynamicMessage (no generated methods)
                // 2. Generated class without map fields
                // Return null to fall back to DynamicMessage path
                return null;
            }
        });
    }

    /**
     * Converts a proto field name to camelCase for method lookup.
     *
     * Examples:
     * - "string_meta" -> "StringMeta"
     * - "int_key_meta" -> "IntKeyMeta"
     * - "addressMap" -> "AddressMap"
     *
     * @param protoFieldName the proto field name (snake_case or camelCase)
     * @return camelCase version with first letter capitalized
     */
    static String toCamelCase(String protoFieldName) {
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

    /**
     * Gets the Java class for a protobuf field descriptor.
     * Used for MethodHandle parameter type matching.
     *
     * @param fd the field descriptor
     * @return the Java class representing this field type
     */
    static Class<?> getJavaClass(Descriptors.FieldDescriptor fd) {
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

    /**
     * Cache for single field setter methods.
     * Key format: "ClassName#setFieldName"
     */
    private static final ConcurrentHashMap<String, MethodHandle> SINGLE_FIELD_SETTER_CACHE
        = new ConcurrentHashMap<>();

    /**
     * Cache for repeated field add methods.
     * Key format: "ClassName#addFieldName"
     */
    private static final ConcurrentHashMap<String, MethodHandle> REPEATED_FIELD_ADD_CACHE
        = new ConcurrentHashMap<>();

    /**
     * Gets a cached MethodHandle for a single field's native setter method.
     *
     * <p>For a field "user_name", the setter method would be "setUserName(String)".
     * Using native setters can be slightly faster than builder.setField().
     *
     * @param builder the generated message builder
     * @param fieldDescriptor the field descriptor
     * @param valueType the Java type of the field value
     * @return MethodHandle for the setter method, or null if not found
     */
    static MethodHandle getSingleFieldSetter(
            Message.Builder builder,
            Descriptors.FieldDescriptor fieldDescriptor,
            Class<?> valueType) {

        Class<?> builderClass = builder.getClass();
        String methodName = "set" + toCamelCase(fieldDescriptor.getName());
        String cacheKey = builderClass.getName() + "#" + methodName;

        return SINGLE_FIELD_SETTER_CACHE.computeIfAbsent(cacheKey, key -> {
            try {
                MethodHandles.Lookup lookup = MethodHandles.lookup();
                MethodType methodType = MethodType.methodType(
                    builderClass, // Returns builder for chaining
                    valueType
                );
                return lookup.findVirtual(builderClass, methodName, methodType);
            } catch (NoSuchMethodException | IllegalAccessException e) {
                return null; // Fallback to builder.setField()
            }
        });
    }

    /**
     * Gets a cached MethodHandle for a repeated field's native add method.
     *
     * <p>For a repeated field "tags", the add method would be "addTags(String)".
     *
     * @param builder the generated message builder
     * @param fieldDescriptor the field descriptor
     * @param valueType the Java type of the field value
     * @return MethodHandle for the add method, or null if not found
     */
    static MethodHandle getRepeatedFieldAdder(
            Message.Builder builder,
            Descriptors.FieldDescriptor fieldDescriptor,
            Class<?> valueType) {

        Class<?> builderClass = builder.getClass();
        String methodName = "add" + toCamelCase(fieldDescriptor.getName());
        String cacheKey = builderClass.getName() + "#" + methodName;

        return REPEATED_FIELD_ADD_CACHE.computeIfAbsent(cacheKey, key -> {
            try {
                MethodHandles.Lookup lookup = MethodHandles.lookup();
                MethodType methodType = MethodType.methodType(
                    builderClass, // Returns builder for chaining
                    valueType
                );
                return lookup.findVirtual(builderClass, methodName, methodType);
            } catch (NoSuchMethodException | IllegalAccessException e) {
                return null; // Fallback to builder.addRepeatedField()
            }
        });
    }

    /**
     * Clears all caches. Useful for testing.
     */
    static void clearCaches() {
        MAP_PUT_CACHE.clear();
        IS_GENERATED_CACHE.clear();
        SINGLE_FIELD_SETTER_CACHE.clear();
        REPEATED_FIELD_ADD_CACHE.clear();
    }
}
