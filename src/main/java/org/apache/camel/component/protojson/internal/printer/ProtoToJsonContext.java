package org.apache.camel.component.protojson.internal.printer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.component.protojson.config.PrinterConfig;

public final class ProtoToJsonContext {

    private final ObjectMapper mapper;
    private final MessageJsonConverterRegistry registry;
    private final PrinterConfig printerConfig;

    public ProtoToJsonContext(ObjectMapper mapper,
                              MessageJsonConverterRegistry registry,
                              PrinterConfig config) {
        this.mapper = mapper;
        this.registry = registry;
        this.printerConfig = config;
    }

    public ObjectMapper getMapper() { return mapper; }
    public MessageJsonConverterRegistry getRegistry() { return registry; }
    public PrinterConfig getPrinterConfig() { return printerConfig; }
}
