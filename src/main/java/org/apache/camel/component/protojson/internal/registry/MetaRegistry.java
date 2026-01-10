package org.apache.camel.component.protojson.internal.registry;

import com.google.protobuf.Descriptors;

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
 */
public final class MetaRegistry {

    private final ConcurrentHashMap<String, MessageMeta> cache = new ConcurrentHashMap<>();

    /**
     * Get or create metadata for the given descriptor.
     */
    public MessageMeta metaFor(Descriptors.Descriptor desc) {
        return cache.computeIfAbsent(desc.getFullName(), k -> new MessageMeta(desc));
    }

    /**
     * Clear all cached metadata (useful for testing).
     */
    public void clear() {
        cache.clear();
    }

    /**
     * Cached metadata for a single message type.
     */
    public static final class MessageMeta {

        public static final class FieldMeta {
            public final Descriptors.FieldDescriptor fd;
            public FieldMeta(Descriptors.FieldDescriptor fd) {
                this.fd = fd;
            }
        }

        private final Map<String, FieldMeta> byName = new HashMap<>();

        public MessageMeta(Descriptors.Descriptor desc) {
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
         * Find field by name (supports JSON name, proto name, case-insensitive).
         */
        public FieldMeta find(String name) {
            FieldMeta fm = byName.get(name);
            return fm != null ? fm : byName.get(name.toLowerCase(Locale.ROOT));
        }
    }
}