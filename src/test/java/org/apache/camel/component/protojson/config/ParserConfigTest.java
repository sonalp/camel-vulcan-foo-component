package org.apache.camel.component.protojson.config;

import org.apache.camel.component.protojson.converter.JsonInFieldConverter;
import org.apache.camel.component.protojson.converter.JsonInMapConverter;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for ParserConfig.
 */
class ParserConfigTest {

    @Test
    void testDefaultConfig() {
        // When
        ParserConfig config = ParserConfig.defaultConfig();

        // Then
        assertThat(config.isIgnoringUnknownFields()).isFalse();
        assertThat(config.isAcceptNumericEnums()).isTrue();
        assertThat(config.isAllowNullForScalars()).isTrue();
    }

    @Test
    void testBuilderWithAllOptions() {
        // When
        ParserConfig config = ParserConfig.newBuilder()
                .ignoringUnknownFields(true)
                .acceptNumericEnums(false)
                .allowNullForScalars(false)
                .build();

        // Then
        assertThat(config.isIgnoringUnknownFields()).isTrue();
        assertThat(config.isAcceptNumericEnums()).isFalse();
        assertThat(config.isAllowNullForScalars()).isFalse();
    }

    @Test
    void testBuilderChaining() {
        // When
        ParserConfig.Builder builder = ParserConfig.newBuilder();
        ParserConfig config = builder
                .ignoringUnknownFields(true)
                .acceptNumericEnums(true)
                .allowNullForScalars(true)
                .build();

        // Then
        assertThat(config).isNotNull();
    }

    @Test
    void testGetInConverterRegistry() {
        // Given
        ParserConfig config = ParserConfig.defaultConfig();

        // When
        var registry = config.getInConverterRegistry();

        // Then
        assertThat(registry).isNotNull();
    }

    @Test
    void testGetMapConverterRegistry() {
        // Given
        ParserConfig config = ParserConfig.defaultConfig();

        // When
        var registry = config.getMapConverterRegistry();

        // Then
        assertThat(registry).isNotNull();
    }

    @Test
    void testBuilderDefaults() {
        // When
        ParserConfig config = ParserConfig.newBuilder().build();

        // Then - Should have some default values
        assertThat(config).isNotNull();
    }
}
