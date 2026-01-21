/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.protojson;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.Message;
import org.apache.camel.*;
import org.apache.camel.component.jackson.SchemaHelper;
import org.apache.camel.component.protojson.config.ParserConfig;
import org.apache.camel.component.protojson.config.PrinterConfig;
import org.apache.camel.component.protojson.converter.JsonInFieldConverter;
import org.apache.camel.component.protojson.converter.JsonInMapConverter;
import org.apache.camel.component.protojson.converter.JsonOutFieldConverter;
import org.apache.camel.component.protojson.converter.wellknown.WellKnownConverters;
import org.apache.camel.component.protojson.engine.ProtoJsonEngine;
import org.apache.camel.component.protojson.engine.ProtoJsonEngineConfig;
import org.apache.camel.component.protojson.engine.ProtoJsonException;
import org.apache.camel.component.protojson.internal.registry.BuilderFactory;
import org.apache.camel.component.protojson.internal.registry.MetaRegistry;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.DataFormatName;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Dataformat;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ProtoJsonDataFormat
 *
 * DataFormat that marshals/unmarshals between Protobuf {@link com.google.protobuf.Message}
 * and JSON using a streaming {@link ProtoJsonEngine}.
 *
 * <p>Custom converters can be registered in two ways:
 * <ul>
 *   <li>Programmatically via setter methods</li>
 *   <li>Auto-discovery from CamelContext Registry (Spring beans, CDI beans, etc.)</li>
 * </ul>
 *
 * <p>Auto-discovery looks for the following types in the registry:
 * <ul>
 *   <li>{@link JsonInFieldConverter} - Custom JSON to Proto field converters</li>
 *   <li>{@link JsonInMapConverter} - Custom JSON to Proto map converters</li>
 *   <li>{@link JsonOutFieldConverter} - Custom Proto to JSON field converters</li>
 * </ul>
 */
@Dataformat("protojson")
@Metadata(firstVersion = "1.0.0", title = "Proto JSON", label = "dataformat,transformation,protobuf,json")
public class ProtoJsonDataFormat extends ServiceSupport
        implements DataFormat, DataFormatName, CamelContextAware {

    private static final Logger LOG = LoggerFactory.getLogger(ProtoJsonDataFormat.class);

    /**
     * Static cache for discovered converters per CamelContext.
     * Key: CamelContext identity hash code, Value: DiscoveredConverters
     * This eliminates repeated registry scanning on each doStart() call.
     */
    private static final ConcurrentHashMap<Integer, DiscoveredConverters> DISCOVERY_CACHE = new ConcurrentHashMap<>();

    private CamelContext camelContext;

    // ---- configuration properties ----

    @Metadata(description = "Fully qualified Protobuf Message class name used for unmarshal.")
    private String instanceClassName;

    @Metadata(label = "advanced", description = "Protobuf Message class used for unmarshal.")
    private Class<? extends Message> instanceClass;

    @Metadata(label = "advanced", description = "Custom ProtoJsonEngine instance.")
    private ProtoJsonEngine engine;

    // Parser configuration options
    @Metadata(description = "Ignore unknown fields in JSON when unmarshalling (default: false)")
    private Boolean ignoringUnknownFields;

    @Metadata(description = "Accept numeric enum values in JSON (default: true)")
    private Boolean acceptNumericEnums;

    @Metadata(description = "Allow null values for scalar fields (default: true)")
    private Boolean allowNullForScalars;

    // Printer configuration options
    @Metadata(description = "Include fields with default values in JSON output (default: false)")
    private Boolean includingDefaultValueFields;

    @Metadata(description = "Preserve proto field names instead of using JSON names (default: false)")
    private Boolean preservingProtoFieldNames;

    @Metadata(description = "Print enums as integer values instead of string names (default: false)")
    private Boolean printingEnumsAsInts;

    @Metadata(label = "advanced", description = "Custom ObjectMapper for JSON processing")
    private ObjectMapper objectMapper;

    // Auto-discovery control
    @Metadata(description = "Whether to auto-discover converters from the registry (default: true)")
    private Boolean autoDiscoverConverters = true;

    // Programmatically registered converters
    @Metadata(label = "advanced", description = "Custom JSON to Proto field converters")
    private List<JsonInFieldConverter> inFieldConverters;

    @Metadata(label = "advanced", description = "Custom JSON to Proto map converters")
    private List<JsonInMapConverter> inMapConverters;

    @Metadata(label = "advanced", description = "Custom Proto to JSON field converters")
    private List<JsonOutFieldConverter> outFieldConverters;

    // internal
    private ProtoJsonEngineConfig engineConfig;

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    public ProtoJsonDataFormat() {
    }

    public ProtoJsonDataFormat(Class<? extends Message> instanceClass) {
        this.instanceClass = instanceClass;
    }

    public ProtoJsonDataFormat(String instanceClassName) {
        this.instanceClassName = instanceClassName;
    }

    // -------------------------------------------------------------------------
    // DataFormatName
    // -------------------------------------------------------------------------

    @Override
    public String getDataFormatName() {
        return "protojson";
    }

    // -------------------------------------------------------------------------
    // CamelContextAware
    // -------------------------------------------------------------------------

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    // -------------------------------------------------------------------------
    // Properties (getters/setters)
    // -------------------------------------------------------------------------

    public String getInstanceClassName() {
        return instanceClassName;
    }

    public void setInstanceClassName(String instanceClassName) {
        this.instanceClassName = instanceClassName;
    }

    public Class<? extends Message> getInstanceClass() {
        return instanceClass;
    }

    public void setInstanceClass(Class<? extends Message> instanceClass) {
        this.instanceClass = instanceClass;
    }

    public ProtoJsonEngine getEngine() {
        return engine;
    }

    public void setEngine(ProtoJsonEngine engine) {
        this.engine = engine;
    }

    public Boolean getIgnoringUnknownFields() {
        return ignoringUnknownFields;
    }

    public void setIgnoringUnknownFields(Boolean ignoringUnknownFields) {
        this.ignoringUnknownFields = ignoringUnknownFields;
    }

    public Boolean getAcceptNumericEnums() {
        return acceptNumericEnums;
    }

    public void setAcceptNumericEnums(Boolean acceptNumericEnums) {
        this.acceptNumericEnums = acceptNumericEnums;
    }

    public Boolean getAllowNullForScalars() {
        return allowNullForScalars;
    }

    public void setAllowNullForScalars(Boolean allowNullForScalars) {
        this.allowNullForScalars = allowNullForScalars;
    }

    public Boolean getIncludingDefaultValueFields() {
        return includingDefaultValueFields;
    }

    public void setIncludingDefaultValueFields(Boolean includingDefaultValueFields) {
        this.includingDefaultValueFields = includingDefaultValueFields;
    }

    public Boolean getPreservingProtoFieldNames() {
        return preservingProtoFieldNames;
    }

    public void setPreservingProtoFieldNames(Boolean preservingProtoFieldNames) {
        this.preservingProtoFieldNames = preservingProtoFieldNames;
    }

    public Boolean getPrintingEnumsAsInts() {
        return printingEnumsAsInts;
    }

    public void setPrintingEnumsAsInts(Boolean printingEnumsAsInts) {
        this.printingEnumsAsInts = printingEnumsAsInts;
    }

    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }

    public void setObjectMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Boolean getAutoDiscoverConverters() {
        return autoDiscoverConverters;
    }

    public void setAutoDiscoverConverters(Boolean autoDiscoverConverters) {
        this.autoDiscoverConverters = autoDiscoverConverters;
    }

    public List<JsonInFieldConverter> getInFieldConverters() {
        return inFieldConverters;
    }

    public void setInFieldConverters(List<JsonInFieldConverter> inFieldConverters) {
        this.inFieldConverters = inFieldConverters;
    }

    public List<JsonInMapConverter> getInMapConverters() {
        return inMapConverters;
    }

    public void setInMapConverters(List<JsonInMapConverter> inMapConverters) {
        this.inMapConverters = inMapConverters;
    }

    public List<JsonOutFieldConverter> getOutFieldConverters() {
        return outFieldConverters;
    }

    public void setOutFieldConverters(List<JsonOutFieldConverter> outFieldConverters) {
        this.outFieldConverters = outFieldConverters;
    }

    // -------------------------------------------------------------------------
    // ServiceSupport lifecycle
    // -------------------------------------------------------------------------

    @Override
    protected void doStart() throws Exception {
        if (engine != null) {
            LOG.debug("Using pre-configured ProtoJsonEngine");
            return;
        }

        // Collect all converters
        List<JsonInFieldConverter> allInFieldConverters = new ArrayList<>();
        List<JsonInMapConverter> allInMapConverters = new ArrayList<>();
        List<JsonOutFieldConverter> allOutFieldConverters = new ArrayList<>();

        // 1. Add well-known converters first (lowest priority)
        addWellKnownConverters(allInFieldConverters, allOutFieldConverters);

        // 2. Add programmatically registered converters
        if (inFieldConverters != null && !inFieldConverters.isEmpty()) {
            allInFieldConverters.addAll(inFieldConverters);
            LOG.debug("Added {} programmatically registered JsonInFieldConverters", inFieldConverters.size());
        }
        if (inMapConverters != null && !inMapConverters.isEmpty()) {
            allInMapConverters.addAll(inMapConverters);
            LOG.debug("Added {} programmatically registered JsonInMapConverters", inMapConverters.size());
        }
        if (outFieldConverters != null && !outFieldConverters.isEmpty()) {
            allOutFieldConverters.addAll(outFieldConverters);
            LOG.debug("Added {} programmatically registered JsonOutFieldConverters", outFieldConverters.size());
        }

        // 3. Auto-discover from registry (highest priority - added last)
        if (Boolean.TRUE.equals(autoDiscoverConverters) && camelContext != null) {
            discoverConvertersFromRegistry(allInFieldConverters, allInMapConverters, allOutFieldConverters);
        }

        // Build parser config
        ParserConfig.Builder parserBuilder = ParserConfig.newBuilder()
                .ignoringUnknownFields(ignoringUnknownFields != null ? ignoringUnknownFields : false)
                .acceptNumericEnums(acceptNumericEnums != null ? acceptNumericEnums : true)
                .allowNullForScalars(allowNullForScalars != null ? allowNullForScalars : true)
                .addAllInConverters(allInFieldConverters)
                .addAllMapConverters(allInMapConverters);

        // Build printer config
        PrinterConfig.Builder printerBuilder = PrinterConfig.newBuilder()
                .includingDefaultValueFields(includingDefaultValueFields != null ? includingDefaultValueFields : false)
                .preservingProtoFieldNames(preservingProtoFieldNames != null ? preservingProtoFieldNames : false)
                .printingEnumsAsInts(printingEnumsAsInts != null ? printingEnumsAsInts : false)
                .addAllOutConverters(allOutFieldConverters);

        // Build engine config
        engineConfig = ProtoJsonEngineConfig.newBuilder()
                .objectMapper(objectMapper != null ? objectMapper : new ObjectMapper())
                .parserConfig(parserBuilder.build())
                .printerConfig(printerBuilder.build())
                .builderFactory(new BuilderFactory())
                .metaRegistry(new MetaRegistry())
                .build();

        engine = new ProtoJsonEngine(engineConfig);

        // WARMUP: Pre-register root message class if known
        // This ensures zero-DynamicMessage parsing by caching all message types and nested types
        if (instanceClass != null) {
            engineConfig.getMetaRegistry().register(instanceClass);
            LOG.info("ProtoJsonDataFormat warmup: Registered {} and all nested types in MetaRegistry",
                    instanceClass.getSimpleName());
        }

        LOG.info("ProtoJsonDataFormat started with {} in-field converters, {} map converters, {} out-field converters",
                allInFieldConverters.size(), allInMapConverters.size(), allOutFieldConverters.size());
    }

    /**
     * Discover converters from CamelContext registry with caching.
     * This allows users to register converters as Spring beans, CDI beans, etc.
     *
     * <p><strong>Performance optimization:</strong> Results are cached per CamelContext
     * to eliminate repeated registry scanning (50-500ms savings per restart).
     */
    private void discoverConvertersFromRegistry(
            List<JsonInFieldConverter> inFieldList,
            List<JsonInMapConverter> inMapList,
            List<JsonOutFieldConverter> outFieldList) {

        // Use CamelContext identity hash as cache key
        int contextKey = System.identityHashCode(camelContext);

        DiscoveredConverters cached = DISCOVERY_CACHE.computeIfAbsent(contextKey, k -> {
            LOG.debug("Performing converter discovery for CamelContext (first time)");
            return doDiscoverConverters();
        });

        // Add cached converters to the lists
        if (!cached.inFieldConverters.isEmpty()) {
            inFieldList.addAll(cached.inFieldConverters);
            LOG.info("Using cached {} JsonInFieldConverter(s) from registry", cached.inFieldConverters.size());
        }
        if (!cached.inMapConverters.isEmpty()) {
            inMapList.addAll(cached.inMapConverters);
            LOG.info("Using cached {} JsonInMapConverter(s) from registry", cached.inMapConverters.size());
        }
        if (!cached.outFieldConverters.isEmpty()) {
            outFieldList.addAll(cached.outFieldConverters);
            LOG.info("Using cached {} JsonOutFieldConverter(s) from registry", cached.outFieldConverters.size());
        }
    }

    /**
     * Actually perform the converter discovery from registry (expensive operation).
     */
    private DiscoveredConverters doDiscoverConverters() {
        List<JsonInFieldConverter> inFieldList = new ArrayList<>();
        List<JsonInMapConverter> inMapList = new ArrayList<>();
        List<JsonOutFieldConverter> outFieldList = new ArrayList<>();

        // Discover JsonInFieldConverter beans
        Set<JsonInFieldConverter> discoveredInField = camelContext.getRegistry()
                .findByType(JsonInFieldConverter.class);
        if (!discoveredInField.isEmpty()) {
            inFieldList.addAll(discoveredInField);
            LOG.info("Auto-discovered {} JsonInFieldConverter(s) from registry: {}",
                    discoveredInField.size(), getBeanNames(discoveredInField));
        }

        // Discover JsonInMapConverter beans
        Set<JsonInMapConverter> discoveredInMap = camelContext.getRegistry()
                .findByType(JsonInMapConverter.class);
        if (!discoveredInMap.isEmpty()) {
            inMapList.addAll(discoveredInMap);
            LOG.info("Auto-discovered {} JsonInMapConverter(s) from registry: {}",
                    discoveredInMap.size(), getBeanNames(discoveredInMap));
        }

        // Discover JsonOutFieldConverter beans
        Set<JsonOutFieldConverter> discoveredOutField = camelContext.getRegistry()
                .findByType(JsonOutFieldConverter.class);
        if (!discoveredOutField.isEmpty()) {
            outFieldList.addAll(discoveredOutField);
            LOG.info("Auto-discovered {} JsonOutFieldConverter(s) from registry: {}",
                    discoveredOutField.size(), getBeanNames(discoveredOutField));
        }

        return new DiscoveredConverters(inFieldList, inMapList, outFieldList);
    }

    /**
     * Clear the discovery cache (useful for testing or when registry changes).
     */
    public static void clearDiscoveryCache() {
        DISCOVERY_CACHE.clear();
        LOG.debug("Converter discovery cache cleared");
    }

    /**
     * Invalidate cache for a specific CamelContext.
     */
    public static void invalidateDiscoveryCache(CamelContext context) {
        if (context != null) {
            DISCOVERY_CACHE.remove(System.identityHashCode(context));
            LOG.debug("Converter discovery cache invalidated for CamelContext");
        }
    }

    /**
     * Immutable holder for discovered converters.
     */
    private static final class DiscoveredConverters {
        final List<JsonInFieldConverter> inFieldConverters;
        final List<JsonInMapConverter> inMapConverters;
        final List<JsonOutFieldConverter> outFieldConverters;

        DiscoveredConverters(List<JsonInFieldConverter> inField,
                            List<JsonInMapConverter> inMap,
                            List<JsonOutFieldConverter> outField) {
            this.inFieldConverters = List.copyOf(inField);
            this.inMapConverters = List.copyOf(inMap);
            this.outFieldConverters = List.copyOf(outField);
        }
    }

    private void addWellKnownConverters(
            List<JsonInFieldConverter> inFieldList,
            List<JsonOutFieldConverter> outFieldList) {
        try {
            inFieldList.addAll(WellKnownConverters.allInConverters());
            outFieldList.addAll(WellKnownConverters.allOutConverters());
            LOG.debug("Added well-known type converters");
        } catch (NoClassDefFoundError | Exception e) {
            LOG.debug("Well-known converters not available: {}", e.getMessage());
        }
    }

    private <T> String getBeanNames(Set<T> beans) {
        return beans.stream()
                .map(b -> b.getClass().getSimpleName())
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
    }

    @Override
    protected void doStop() {
        // nothing special; engine is pure in-memory
    }

    // -------------------------------------------------------------------------
    // DataFormat
    // -------------------------------------------------------------------------

    @Override
    public void marshal(Exchange exchange, Object graph, OutputStream stream) throws Exception {
        if (!(graph instanceof Message protoMsg)) {
            throw new CamelExecutionException(
                    "ProtoJsonDataFormat marshal expects a Protobuf Message, got: " + ObjectHelper.type(graph),
                    exchange);
        }

        try {
            engine.print(protoMsg, stream);
        } catch (ProtoJsonException e) {
            throw new CamelExecutionException(
                    "Error while marshalling Protobuf Message to JSON using ProtoJsonEngine", exchange, e);
        }
    }

    @Override
    public Object unmarshal(Exchange exchange, InputStream stream) throws Exception {
        Class<? extends Message> targetType = resolveTargetType(exchange);

        try {
            return engine.parse(stream, targetType);
        } catch (ProtoJsonException e) {
            throw new CamelExecutionException(
                    "Error while unmarshalling JSON to Protobuf Message using ProtoJsonEngine", exchange, e);
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    protected Class<? extends Message> resolveTargetType(Exchange exchange) {
        if (instanceClass != null) {
            return instanceClass;
        }

        String clazzName = instanceClassName;
        if (clazzName == null) {
            clazzName = SchemaHelper.resolveContentClass(exchange, null);
        }

        if (clazzName == null) {
            throw new RuntimeCamelException(
                    "ProtoJsonDataFormat requires instanceClass/instanceClassName or "
                            + "SchemaHelper.CONTENT_CLASS exchange property/header to be set");
        }

        try {
            Class<?> resolved = exchange.getContext()
                    .getClassResolver()
                    .resolveMandatoryClass(clazzName);

            if (!Message.class.isAssignableFrom(resolved)) {
                throw new RuntimeCamelException(
                        "Resolved content class " + clazzName + " is not a Protobuf Message type");
            }

            return (Class<? extends Message>) resolved;
        } catch (ClassNotFoundException e) {
            throw new RuntimeCamelException(
                    "Cannot resolve Protobuf Message class " + clazzName, e);
        }
    }
}