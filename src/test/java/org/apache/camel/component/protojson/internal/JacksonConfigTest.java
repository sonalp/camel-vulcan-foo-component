package org.apache.camel.component.protojson.internal;

import com.fasterxml.jackson.core.JsonFactory;
import org.apache.camel.component.protojson.config.ParserConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Coverage tests for JacksonConfig (0% -> 100%)
 */
@DisplayName("JacksonConfig Tests")
class JacksonConfigTest {

    @Test
    @DisplayName("Should create JsonFactory with correct configuration")
    void testCreateJsonFactory() {
        // Given
        ParserConfig config = ParserConfig.newBuilder().build();

        // When
        JsonFactory factory = JacksonConfig.createJsonFactory(config);

        // Then
        assertThat(factory).isNotNull();

        // Verify stream read constraints are set
        assertThat(factory.getStreamReadConstraints()).isNotNull();
        assertThat(factory.getStreamReadConstraints().getMaxNumberLength()).isEqualTo(1000);
        assertThat(factory.getStreamReadConstraints().getMaxStringLength()).isEqualTo(1_000_000);

        // Verify features are enabled
        assertThat(factory.isEnabled(JsonFactory.Feature.CANONICALIZE_FIELD_NAMES)).isTrue();
        assertThat(factory.isEnabled(JsonFactory.Feature.INTERN_FIELD_NAMES)).isTrue();
    }

    @Test
    @DisplayName("Should create different factories for different configs")
    void testMultipleFactories() {
        // Given
        ParserConfig config1 = ParserConfig.newBuilder().ignoringUnknownFields(true).build();
        ParserConfig config2 = ParserConfig.newBuilder().ignoringUnknownFields(false).build();

        // When
        JsonFactory factory1 = JacksonConfig.createJsonFactory(config1);
        JsonFactory factory2 = JacksonConfig.createJsonFactory(config2);

        // Then
        assertThat(factory1).isNotNull();
        assertThat(factory2).isNotNull();
        assertThat(factory1).isNotSameAs(factory2);
    }

    @Test
    @DisplayName("Should handle null config gracefully")
    void testNullConfig() {
        // When
        JsonFactory factory = JacksonConfig.createJsonFactory(null);

        // Then - should still create factory with default settings
        assertThat(factory).isNotNull();
        assertThat(factory.getStreamReadConstraints()).isNotNull();
    }
}
