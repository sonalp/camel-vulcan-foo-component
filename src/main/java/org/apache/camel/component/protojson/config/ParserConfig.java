package org.apache.camel.component.protojson.config;


import org.apache.camel.component.protojson.converter.JsonInMapConverter;
import org.apache.camel.component.protojson.internal.registry.FieldConverterRegistry;
import org.apache.camel.component.protojson.converter.JsonInFieldConverter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class ParserConfig {

    /** Default maximum nesting depth to prevent stack overflow attacks */
    public static final int DEFAULT_MAX_NESTING_DEPTH = 100;

    /** Default maximum repeated field size to prevent memory exhaustion attacks */
    public static final int DEFAULT_MAX_REPEATED_FIELD_SIZE = 100_000;

    private final boolean ignoringUnknownFields;
    private final boolean acceptNumericEnums;
    private final boolean allowNullForScalars;

    // Security limits
    private final int maxNestingDepth;
    private final int maxRepeatedFieldSize;

    // High-performance converter registry with O(1) lookup
    private final FieldConverterRegistry<JsonInFieldConverter> inConverterRegistry;
    private final FieldConverterRegistry<JsonInMapConverter> mapConverterRegistry;

    private ParserConfig(Builder b) {
        this.ignoringUnknownFields = b.ignoringUnknownFields;
        this.acceptNumericEnums = b.acceptNumericEnums;
        this.allowNullForScalars = b.allowNullForScalars;
        this.maxNestingDepth = b.maxNestingDepth;
        this.maxRepeatedFieldSize = b.maxRepeatedFieldSize;
        this.inConverterRegistry = b.buildConverterRegistry();
        this.mapConverterRegistry  = b.buildMapConverterRegistry();
    }

    // getters
    public boolean isIgnoringUnknownFields()    { return ignoringUnknownFields; }
    public boolean isAcceptNumericEnums()       { return acceptNumericEnums; }
    public boolean isAllowNullForScalars()      { return allowNullForScalars; }
    public int getMaxNestingDepth()             { return maxNestingDepth; }
    public int getMaxRepeatedFieldSize()        { return maxRepeatedFieldSize; }

    public FieldConverterRegistry<JsonInFieldConverter> getInConverterRegistry() {
        return inConverterRegistry;
    }
    public FieldConverterRegistry<JsonInMapConverter> getMapConverterRegistry() {return mapConverterRegistry;}


    // Default config
    public static ParserConfig defaultConfig() {
        return newBuilder()
                .ignoringUnknownFields(false)
                .acceptNumericEnums(true)
                .allowNullForScalars(true)
                .maxNestingDepth(DEFAULT_MAX_NESTING_DEPTH)
                .maxRepeatedFieldSize(DEFAULT_MAX_REPEATED_FIELD_SIZE)
                .build();
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public static final class Builder {

        private boolean ignoringUnknownFields;
        private boolean acceptNumericEnums;
        private boolean allowNullForScalars;
        private int maxNestingDepth = DEFAULT_MAX_NESTING_DEPTH;
        private int maxRepeatedFieldSize = DEFAULT_MAX_REPEATED_FIELD_SIZE;

        // Converter list
        private final List<JsonInFieldConverter> inConverters = new ArrayList<>();
        private final List<JsonInMapConverter> mapConverters = new ArrayList<>();

        // existing setters
        public Builder ignoringUnknownFields(boolean v) {
            this.ignoringUnknownFields = v;
            return this;
        }

        public Builder acceptNumericEnums(boolean v) {
            this.acceptNumericEnums = v;
            return this;
        }

        public Builder allowNullForScalars(boolean v) {
            this.allowNullForScalars = v;
            return this;
        }

        /**
         * Set maximum nesting depth for nested messages.
         * Prevents stack overflow attacks from deeply nested JSON.
         * Default: {@value #DEFAULT_MAX_NESTING_DEPTH}
         */
        public Builder maxNestingDepth(int v) {
            if (v < 1) {
                throw new IllegalArgumentException("maxNestingDepth must be >= 1");
            }
            this.maxNestingDepth = v;
            return this;
        }

        /**
         * Set maximum size for repeated fields (arrays).
         * Prevents memory exhaustion attacks from huge arrays.
         * Default: {@value #DEFAULT_MAX_REPEATED_FIELD_SIZE}
         */
        public Builder maxRepeatedFieldSize(int v) {
            if (v < 1) {
                throw new IllegalArgumentException("maxRepeatedFieldSize must be >= 1");
            }
            this.maxRepeatedFieldSize = v;
            return this;
        }

        // Converter methods
        public Builder addInConverter(JsonInFieldConverter c) {
            this.inConverters.add(c);
            return this;
        }

        public Builder addAllInConverters(Collection<JsonInFieldConverter> cs) {
            this.inConverters.addAll(cs);
            return this;
        }

        public Builder addMapConverter(JsonInMapConverter c) {
            this.mapConverters.add(c);
            return this;
        }

        public Builder addAllMapConverters(Collection<JsonInMapConverter> cs) {
            this.mapConverters.addAll(cs);
            return this;
        }

        private FieldConverterRegistry<JsonInMapConverter> buildMapConverterRegistry() {
            return FieldConverterRegistry.<JsonInMapConverter>newBuilder(
                            (converter, field) -> converter.supports(field)
                    )
                    .addAll(mapConverters)
                    .build();
        }
        // Build the high-performance registry
        private FieldConverterRegistry<JsonInFieldConverter> buildConverterRegistry() {
            return FieldConverterRegistry.<JsonInFieldConverter>newBuilder(
                            (converter, field) -> converter.supports(field)
                    )
                    .addAll(inConverters)
                    .build();
        }

        public ParserConfig build() {
            return new ParserConfig(this);
        }
    }
}