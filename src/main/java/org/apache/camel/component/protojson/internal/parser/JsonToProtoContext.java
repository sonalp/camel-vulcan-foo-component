package org.apache.camel.component.protojson.internal.parser;

import org.apache.camel.component.protojson.config.ParserConfig;
import org.apache.camel.component.protojson.internal.registry.MetaRegistry;

public final class JsonToProtoContext {

    private final ParserConfig parserConfig;
    private final MessageTypeConverterRegistry registry;
    private final MetaRegistry metaRegistry;

    public JsonToProtoContext(ParserConfig parserConfig,
                              MessageTypeConverterRegistry registry,
                              MetaRegistry metaRegistry) {
        this.parserConfig = parserConfig;
        this.registry = registry;
        this.metaRegistry = metaRegistry;
    }

    public ParserConfig getParserConfig() { return parserConfig; }
    public MessageTypeConverterRegistry getRegistry() { return registry; }
    public MetaRegistry getMetaRegistry() { return metaRegistry; }
}
