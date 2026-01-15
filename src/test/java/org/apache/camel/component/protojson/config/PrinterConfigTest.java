package org.apache.camel.component.protojson.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for PrinterConfig.
 */
class PrinterConfigTest {

    @Test
    void testDefaultConfig() {
        // When
        PrinterConfig config = PrinterConfig.defaultConfig();

        // Then
        assertThat(config.isIncludingDefaultValueFields()).isFalse();
        assertThat(config.isPreservingProtoFieldNames()).isFalse();
        assertThat(config.isPrintingEnumsAsInts()).isFalse();
    }

    @Test
    void testBuilderWithAllOptions() {
        // When
        PrinterConfig config = PrinterConfig.newBuilder()
                .includingDefaultValueFields(true)
                .preservingProtoFieldNames(true)
                .printingEnumsAsInts(true)
                .build();

        // Then
        assertThat(config.isIncludingDefaultValueFields()).isTrue();
        assertThat(config.isPreservingProtoFieldNames()).isTrue();
        assertThat(config.isPrintingEnumsAsInts()).isTrue();
    }

    @Test
    void testBuilderChaining() {
        // When
        PrinterConfig.Builder builder = PrinterConfig.newBuilder();
        PrinterConfig config = builder
                .includingDefaultValueFields(true)
                .preservingProtoFieldNames(false)
                .printingEnumsAsInts(true)
                .build();

        // Then
        assertThat(config).isNotNull();
    }

    @Test
    void testGetOutConverterRegistry() {
        // Given
        PrinterConfig config = PrinterConfig.defaultConfig();

        // When
        var registry = config.getOutConverterRegistry();

        // Then
        assertThat(registry).isNotNull();
    }

    @Test
    void testBuilderDefaults() {
        // When
        PrinterConfig config = PrinterConfig.newBuilder().build();

        // Then - Should have default values
        assertThat(config).isNotNull();
    }

    @Test
    void testIncludingDefaultFields() {
        // When
        PrinterConfig includeDefaults = PrinterConfig.newBuilder()
                .includingDefaultValueFields(true)
                .build();
        PrinterConfig excludeDefaults = PrinterConfig.newBuilder()
                .includingDefaultValueFields(false)
                .build();

        // Then
        assertThat(includeDefaults.isIncludingDefaultValueFields()).isTrue();
        assertThat(excludeDefaults.isIncludingDefaultValueFields()).isFalse();
    }

    @Test
    void testProtoFieldNamePreservation() {
        // When
        PrinterConfig preserve = PrinterConfig.newBuilder()
                .preservingProtoFieldNames(true)
                .build();
        PrinterConfig jsonNames = PrinterConfig.newBuilder()
                .preservingProtoFieldNames(false)
                .build();

        // Then
        assertThat(preserve.isPreservingProtoFieldNames()).isTrue();
        assertThat(jsonNames.isPreservingProtoFieldNames()).isFalse();
    }

    @Test
    void testEnumPrintingFormat() {
        // When
        PrinterConfig enumAsInt = PrinterConfig.newBuilder()
                .printingEnumsAsInts(true)
                .build();
        PrinterConfig enumAsString = PrinterConfig.newBuilder()
                .printingEnumsAsInts(false)
                .build();

        // Then
        assertThat(enumAsInt.isPrintingEnumsAsInts()).isTrue();
        assertThat(enumAsString.isPrintingEnumsAsInts()).isFalse();
    }
}
