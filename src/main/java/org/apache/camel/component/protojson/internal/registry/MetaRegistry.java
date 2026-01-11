package org.apache.camel.component.protojson.internal.registry;

import com.google.protobuf.Descriptors;
import com.google.protobuf.Message;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Internal metadata cache for Protobuf message descriptors.
 *
 * <p><strong>INTERNAL USE ONLY</strong> - This class is not part of the public API
 * and may change without notice.
 *
 * <p>This registry caches field metadata for fast lookup during JSON parsing,
 * supporting both JSON names and proto names with case-insensitive fallback.
 *
 * <p>Also caches default instances of generated messages for fast builder creation.
 */
public final class MetaRegistry {

    private final ConcurrentHashMap<String, MessageMeta> cache = new ConcurrentHashMap<>();

    /**
     * Register a generated message class and all its nested message types recursively.
     * This enables zero-DynamicMessage parsing by caching generated builders.
     */
    public void register(Class<? extends Message> rootClass) {
        try {
            Message defaultInstance = getDefaultInstance(rootClass);
            registerRecursive(defaultInstance);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to register " + rootClass.getName(), e);
        }
    }

    /**
     * Get or create metadata for the given descriptor.
     */
    public MessageMeta metaFor(Descriptors.Descriptor desc) {
        return cache.computeIfAbsent(desc.getFullName(), k -> new MessageMeta(desc, null));
    }

    /**
     * Clear all cached metadata (useful for testing).
     */
    public void clear() {
        cache.clear();
    }

    // ========== PRIVATE HELPERS ==========

    private void registerRecursive(Message defaultInstance) {
        Descriptors.Descriptor desc = defaultInstance.getDescriptorForType();

        if (cache.containsKey(desc.getFullName())) {
            return; // Already registered
        }

        // Create MessageMeta with defaultInstance for fast builder creation
        MessageMeta meta = new MessageMeta(desc, defaultInstance);
        cache.put(desc.getFullName(), meta);

        // Recursively register nested MESSAGE fields
        for (Descriptors.FieldDescriptor fd : desc.getFields()) {
            if (fd.getJavaType() == Descriptors.FieldDescriptor.JavaType.MESSAGE) {
                if (fd.isMapField()) {
                    // CRITICAL: Register the map entry descriptor itself first!
                    // Map entries are repeated MapEntry messages, and we need their generated builders
                    Descriptors.Descriptor entryDesc = fd.getMessageType();
                    Message entryDefault = getNestedDefault(defaultInstance, fd);
                    if (entryDefault != null) {
                        registerRecursive(entryDefault);
                    }

                    // Also register map value if it's a MESSAGE
                    Descriptors.FieldDescriptor valFd = entryDesc.findFieldByName("value");
                    if (valFd.getJavaType() == Descriptors.FieldDescriptor.JavaType.MESSAGE) {
                        Message valueDefault = getNestedDefault(defaultInstance, valFd);
                        if (valueDefault != null) {
                            registerRecursive(valueDefault);
                        }
                    }
                } else {
                    // Regular MESSAGE field
                    Message nestedDefault = getNestedDefault(defaultInstance, fd);
                    if (nestedDefault != null) {
                        registerRecursive(nestedDefault);
                    }
                }
            }
        }

        // Resolve nested MessageMeta references now that all types are registered
        meta.resolveNestedMeta(this);
    }

    private Message getNestedDefault(Message parent, Descriptors.FieldDescriptor fd) {
        try {
            Message.Builder builder = parent.toBuilder();
            return builder.newBuilderForField(fd).getDefaultInstanceForType();
        } catch (Exception e) {
            return null; // Skip if unable to get nested default
        }
    }

    private static Message getDefaultInstance(Class<? extends Message> clazz) throws Exception {
        Method method = clazz.getMethod("getDefaultInstance");
        return (Message) method.invoke(null);
    }

    // ========== NESTED CLASSES ==========

    /**
     * Cached metadata for a single message type.
     */
    public static final class MessageMeta {

        private final Descriptors.Descriptor descriptor;
        private final Message defaultInstance; // null for DynamicMessage
        private final Map<String, FieldMeta> byName = new HashMap<>();

        public MessageMeta(Descriptors.Descriptor desc, Message defaultInstance) {
            this.descriptor = desc;
            this.defaultInstance = defaultInstance;

            List<Descriptors.FieldDescriptor> fds = desc.getFields();

            for (Descriptors.FieldDescriptor fd : fds) {
                FieldMeta meta = new FieldMeta(fd);
                String jsonName = fd.getJsonName();
                String protoName = fd.getName();

                // Primary lookups
                byName.put(jsonName, meta);
                byName.put(protoName, meta);
                // Lowercase (case-insensitive)
                byName.putIfAbsent(jsonName.toLowerCase(Locale.ROOT), meta);
                byName.putIfAbsent(protoName.toLowerCase(Locale.ROOT), meta);
            }
        }

        /**
         * Resolve nested MessageMeta references for MESSAGE fields.
         * Must be called after the type and its nested types are registered.
         */
        public void resolveNestedMeta(MetaRegistry registry) {
            for (FieldMeta fm : byName.values()) {
                if (fm.nestedMeta == null && fm.fd.getJavaType() == Descriptors.FieldDescriptor.JavaType.MESSAGE) {
                    fm.nestedMeta = registry.metaFor(fm.fd.getMessageType());
                }
            }
        }

        /**
         * Find field by name (supports JSON name, proto name, case-insensitive).
         */
        public FieldMeta find(String name) {
            FieldMeta fm = byName.get(name);
            return fm != null ? fm : byName.get(name.toLowerCase(Locale.ROOT));
        }

        /**
         * Create a new builder for this message type.
         * Uses generated class builder if available, otherwise generic reflection.
         */
        public Message.Builder newBuilder() {
            if (defaultInstance != null) {
                return defaultInstance.toBuilder();
            }
            // Fallback for DynamicMessage (shouldn't happen if registered properly)
            return com.google.protobuf.DynamicMessage.newBuilder(descriptor);
        }

        public Descriptors.Descriptor getDescriptor() {
            return descriptor;
        }
    }

    /**
     * Cached metadata for a single field.
     */
    public static final class FieldMeta {
        public final Descriptors.FieldDescriptor fd;
        public MessageMeta nestedMeta; // Non-null for MESSAGE fields (populated after registration)

        public FieldMeta(Descriptors.FieldDescriptor fd) {
            this.fd = fd;
            this.nestedMeta = null; // Will be resolved after all types are registered
        }
    }
}
