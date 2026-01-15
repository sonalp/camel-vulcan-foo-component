package org.apache.camel.component.protojson.converter.wellknown;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for WellKnownConverters factory methods.
 */
class WellKnownConvertersTest {

    @Test
    void testWellKnownConvertersAllInConverters() {
        // When
        var converters = WellKnownConverters.allInConverters();

        // Then
        assertThat(converters).isNotEmpty();
        assertThat(converters).hasSize(4); // Timestamp, Duration, Struct, Wrappers
    }

    @Test
    void testWellKnownConvertersAllOutConverters() {
        // When
        var converters = WellKnownConverters.allOutConverters();

        // Then
        assertThat(converters).isNotEmpty();
        assertThat(converters).hasSize(4); // Timestamp, Duration, Struct, Wrappers
    }

    @Test
    void testWellKnownConvertersContainsTimestamp() {
        // When
        var converters = WellKnownConverters.allInConverters();

        // Then
        assertThat(converters).anyMatch(c -> c instanceof TimestampConverter);
    }

    @Test
    void testWellKnownConvertersContainsDuration() {
        // When
        var converters = WellKnownConverters.allInConverters();

        // Then
        assertThat(converters).anyMatch(c -> c instanceof DurationConverter);
    }

    @Test
    void testWellKnownConvertersContainsStruct() {
        // When
        var converters = WellKnownConverters.allInConverters();

        // Then
        assertThat(converters).anyMatch(c -> c instanceof StructConverter);
    }

    @Test
    void testWellKnownConvertersContainsWrappers() {
        // When
        var converters = WellKnownConverters.allInConverters();

        // Then - WrapperConverters is a single class handling all wrapper types
        assertThat(converters).anyMatch(c -> c instanceof WrapperConverters);
    }

    @Test
    void testWellKnownConvertersTimestampFactory() {
        // When
        TimestampConverter converter = WellKnownConverters.timestamp();

        // Then
        assertThat(converter).isNotNull();
        assertThat(converter).isInstanceOf(TimestampConverter.class);
    }

    @Test
    void testWellKnownConvertersDurationFactory() {
        // When
        DurationConverter converter = WellKnownConverters.duration();

        // Then
        assertThat(converter).isNotNull();
        assertThat(converter).isInstanceOf(DurationConverter.class);
    }

    @Test
    void testWellKnownConvertersStructFactory() {
        // When
        StructConverter converter = WellKnownConverters.struct();

        // Then
        assertThat(converter).isNotNull();
        assertThat(converter).isInstanceOf(StructConverter.class);
    }

    @Test
    void testWellKnownConvertersWrappersFactory() {
        // When
        WrapperConverters converter = WellKnownConverters.wrappers();

        // Then
        assertThat(converter).isNotNull();
        assertThat(converter).isInstanceOf(WrapperConverters.class);
    }
}
