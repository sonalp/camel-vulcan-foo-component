package org.apache.camel.component.protojson.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.Descriptors;
import com.google.protobuf.Message;
import org.apache.camel.component.protojson.config.ParserConfig;
import org.apache.camel.component.protojson.config.PrinterConfig;
import org.apache.camel.component.protojson.internal.parser.MessageTypeConverterRegistry;
import org.apache.camel.component.protojson.internal.printer.MessageJsonConverterRegistry;
import org.apache.camel.component.protojson.internal.registry.BuilderFactory;
import org.apache.camel.component.protojson.internal.registry.MetaRegistry;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class ProtoJsonEngineConfig {

    private final ObjectMapper objectMapper;
    private final ParserConfig parserConfig;
    private final PrinterConfig printerConfig;
    private final MessageTypeConverterRegistry inRegistry;
    private final MessageJsonConverterRegistry outRegistry;
    private final BuilderFactory builderFactory;
    private final MetaRegistry metaRegistry;
    private final Map<Descriptors.Descriptor, Class<? extends Message>> descriptorToClass;

    private ProtoJsonEngineConfig(Builder b) {
        this.objectMapper = b.objectMapper;
        this.parserConfig = b.parserConfig;
        this.printerConfig = b.printerConfig;
        this.inRegistry = b.inRegistry;
        this.outRegistry = b.outRegistry;
        this.builderFactory = b.builderFactory;
        this.metaRegistry = b.metaRegistry;
        this.descriptorToClass = Collections.unmodifiableMap(new HashMap<>(b.descriptorToClass));
    }

    public ObjectMapper getObjectMapper()                { return objectMapper; }
    public ParserConfig getParserConfig()                { return parserConfig; }
    public PrinterConfig getPrinterConfig()              { return printerConfig; }
    public MessageTypeConverterRegistry getInRegistry()  { return inRegistry; }
    public MessageJsonConverterRegistry getOutRegistry() { return outRegistry; }
    public BuilderFactory getBuilderFactory()            { return builderFactory; }
    public MetaRegistry getMetaRegistry()                { return metaRegistry; }
    public Map<Descriptors.Descriptor, Class<? extends Message>> getDescriptorToClass() {
        return descriptorToClass;
    }

    public static Builder newBuilder() { return new Builder(); }

    public static final class Builder {
        private ObjectMapper objectMapper;
        private ParserConfig parserConfig;
        private PrinterConfig printerConfig;
        private MessageTypeConverterRegistry inRegistry;
        private MessageJsonConverterRegistry outRegistry;
        private BuilderFactory builderFactory;
        private MetaRegistry metaRegistry;
        private final Map<Descriptors.Descriptor, Class<? extends Message>> descriptorToClass =
                new HashMap<>();

        public Builder objectMapper(ObjectMapper mapper) {
            this.objectMapper = mapper;
            return this;
        }

        public Builder parserConfig(ParserConfig cfg) {
            this.parserConfig = cfg;
            return this;
        }

        public Builder printerConfig(PrinterConfig cfg) {
            this.printerConfig = cfg;
            return this;
        }

        public Builder inRegistry(MessageTypeConverterRegistry reg) {
            this.inRegistry = reg;
            return this;
        }

        public Builder outRegistry(MessageJsonConverterRegistry reg) {
            this.outRegistry = reg;
            return this;
        }

        public Builder builderFactory(BuilderFactory f) {
            this.builderFactory = f;
            return this;
        }

        public Builder metaRegistry(MetaRegistry m) {
            this.metaRegistry = m;
            return this;
        }

        public Builder registerDescriptorClass(Descriptors.Descriptor desc,
                                               Class<? extends Message> type) {
            this.descriptorToClass.put(desc, type);
            return this;
        }

        public ProtoJsonEngineConfig build() {
            if (objectMapper == null)  objectMapper = new ObjectMapper();
            if (parserConfig == null)  parserConfig = ParserConfig.defaultConfig();
            if (printerConfig == null) printerConfig = PrinterConfig.defaultConfig();
            if (builderFactory == null) builderFactory = new BuilderFactory();
            if (metaRegistry == null)   metaRegistry = new MetaRegistry();
            if (inRegistry == null)     inRegistry = MessageTypeConverterRegistry.withDefault();
            if (outRegistry == null)    outRegistry = MessageJsonConverterRegistry.withDefault();
            return new ProtoJsonEngineConfig(this);
        }
    }
}
