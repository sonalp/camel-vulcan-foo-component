package org.apache.camel.component.protojson.internal.registry;

import com.google.protobuf.Descriptors;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * High-performance registry for field-level converters.
 * Uses caching to avoid repeated list iterations - O(1) lookup after first access.
 *
 * <p><strong>INTERNAL USE ONLY</strong> - This class is not part of the public API
 * and may change without notice.
 */
public final class FieldConverterRegistry<T> {

    private final List<T> converters;
    private final ConcurrentHashMap<String, Optional<T>> cache;  // Optional ile
    private final ConverterMatcher<T> matcher;

    @FunctionalInterface
    public interface ConverterMatcher<T> {
        boolean matches(T converter, Descriptors.FieldDescriptor field);
    }

    public FieldConverterRegistry(List<T> converters, ConverterMatcher<T> matcher) {
        this.converters = List.copyOf(converters);
        this.cache = new ConcurrentHashMap<>();
        this.matcher = matcher;
    }

    /**
     * Find converter for the given field descriptor.
     * Returns null if no converter supports this field.
     *
     * Performance: O(1) after first lookup per field (cached)
     */
    public T findConverter(Descriptors.FieldDescriptor fd) {
        if (converters.isEmpty()) {
            return null;
        }

        String fieldKey = fd.getFullName();

        // computeIfAbsent ensures single computation per field (thread-safe)
        Optional<T> result = cache.computeIfAbsent(fieldKey, key -> {
            for (T converter : converters) {
                if (matcher.matches(converter, fd)) {
                    return Optional.of(converter);
                }
            }
            return Optional.empty();  // Negative cache
        });

        return result.orElse(null);
    }

    /**
     * Check if any converter supports the given field.
     */
    public boolean hasConverter(Descriptors.FieldDescriptor fd) {
        return findConverter(fd) != null;
    }

    /**
     * Get the number of registered converters.
     */
    public int size() {
        return converters.size();
    }

    /**
     * Clear the cache. Useful for testing or if converters are added dynamically.
     */
    public void clearCache() {
        cache.clear();
    }

    // Builder for convenience
    public static <T> Builder<T> newBuilder(ConverterMatcher<T> matcher) {
        return new Builder<>(matcher);
    }

    public static final class Builder<T> {
        private final List<T> converters = new ArrayList<>();
        private final ConverterMatcher<T> matcher;

        Builder(ConverterMatcher<T> matcher) {
            this.matcher = matcher;
        }

        public Builder<T> add(T converter) {
            converters.add(converter);
            return this;
        }

        public Builder<T> addAll(List<T> converters) {
            this.converters.addAll(converters);
            return this;
        }

        public FieldConverterRegistry<T> build() {
            return new FieldConverterRegistry<>(converters, matcher);
        }
    }
}