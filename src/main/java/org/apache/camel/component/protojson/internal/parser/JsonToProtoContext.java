package org.apache.camel.component.protojson.internal.parser;

import org.apache.camel.component.protojson.config.ParserConfig;
import org.apache.camel.component.protojson.engine.ProtoJsonException;
import org.apache.camel.component.protojson.internal.registry.MetaRegistry;

/**
 * Context object passed through the parsing chain.
 * Tracks parsing state including nesting depth for security.
 */
public final class JsonToProtoContext {

    private final ParserConfig parserConfig;
    private final MessageTypeConverterRegistry registry;
    private final MetaRegistry metaRegistry;

    /** Current nesting depth - mutable for tracking during parse */
    private int currentDepth = 0;

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

    /**
     * Increment nesting depth and check against maximum.
     * Call this when entering a nested message.
     *
     * @param fieldName the field being entered (for error messages)
     * @throws ProtoJsonException if max depth exceeded
     */
    public void enterNested(String fieldName) throws ProtoJsonException {
        currentDepth++;
        int maxDepth = parserConfig.getMaxNestingDepth();
        if (currentDepth > maxDepth) {
            throw new ProtoJsonException(
                    ProtoJsonException.ErrorCode.NESTING_TOO_DEEP,
                    "Maximum nesting depth exceeded: " + currentDepth + " > " + maxDepth +
                            " (field: " + fieldName + ")",
                    fieldName
            );
        }
    }

    /**
     * Decrement nesting depth.
     * Call this when exiting a nested message.
     */
    public void exitNested() {
        if (currentDepth > 0) {
            currentDepth--;
        }
    }

    /**
     * Get current nesting depth.
     */
    public int getCurrentDepth() {
        return currentDepth;
    }

    /**
     * Check if adding more elements to a repeated field is allowed.
     *
     * @param currentSize current number of elements
     * @param fieldName the field being checked (for error messages)
     * @throws ProtoJsonException if max size exceeded
     */
    public void checkRepeatedFieldSize(int currentSize, String fieldName) throws ProtoJsonException {
        int maxSize = parserConfig.getMaxRepeatedFieldSize();
        if (currentSize >= maxSize) {
            throw new ProtoJsonException(
                    ProtoJsonException.ErrorCode.REPEATED_FIELD_TOO_LARGE,
                    "Repeated field size limit exceeded: " + currentSize + " >= " + maxSize +
                            " (field: " + fieldName + ")",
                    fieldName
            );
        }
    }
}
