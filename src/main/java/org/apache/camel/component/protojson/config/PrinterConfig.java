package org.apache.camel.component.protojson.config;

import org.apache.camel.component.protojson.internal.registry.FieldConverterRegistry;
import org.apache.camel.component.protojson.converter.JsonOutFieldConverter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class PrinterConfig {

    private final boolean includingDefaultValueFields;
    private final boolean preservingProtoFieldNames;
    private final boolean printingEnumsAsInts;

    // High-performance converter registry with O(1) lookup
    private final FieldConverterRegistry<JsonOutFieldConverter> outConverterRegistry;

    private PrinterConfig(Builder b) {
        this.includingDefaultValueFields = b.includingDefaultValueFields;
        this.preservingProtoFieldNames   = b.preservingProtoFieldNames;
        this.printingEnumsAsInts         = b.printingEnumsAsInts;
        this.outConverterRegistry        = b.buildConverterRegistry();
    }

    public boolean isIncludingDefaultValueFields() { return includingDefaultValueFields; }
    public boolean isPreservingProtoFieldNames()   { return preservingProtoFieldNames; }
    public boolean isPrintingEnumsAsInts()         { return printingEnumsAsInts; }

    public FieldConverterRegistry<JsonOutFieldConverter> getOutConverterRegistry() {
        return outConverterRegistry;
    }

    public static PrinterConfig defaultConfig() {
        return newBuilder()
                .includingDefaultValueFields(false)
                .preservingProtoFieldNames(false)
                .printingEnumsAsInts(false)
                .build();
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public static final class Builder {

        private boolean includingDefaultValueFields;
        private boolean preservingProtoFieldNames;
        private boolean printingEnumsAsInts;

        // Converter list
        private final List<JsonOutFieldConverter> outConverters = new ArrayList<>();

        // existing methods
        public Builder includingDefaultValueFields(boolean v) {
            this.includingDefaultValueFields = v;
            return this;
        }

        public Builder preservingProtoFieldNames(boolean v) {
            this.preservingProtoFieldNames = v;
            return this;
        }

        public Builder printingEnumsAsInts(boolean v) {
            this.printingEnumsAsInts = v;
            return this;
        }

        // Converter methods
        public Builder addOutConverter(JsonOutFieldConverter c) {
            this.outConverters.add(c);
            return this;
        }

        public Builder addAllOutConverters(Collection<JsonOutFieldConverter> cs) {
            this.outConverters.addAll(cs);
            return this;
        }

        // Build the high-performance registry
        private FieldConverterRegistry<JsonOutFieldConverter> buildConverterRegistry() {
            return FieldConverterRegistry.<JsonOutFieldConverter>newBuilder(
                            (converter, field) -> converter.supports(field)
                    )
                    .addAll(outConverters)
                    .build();
        }

        public PrinterConfig build() {
            return new PrinterConfig(this);
        }
    }
}