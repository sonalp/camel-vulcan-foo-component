package org.apache.camel.component.protojson;

import org.apache.camel.component.protojson.test.proto.SimpleUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Coverage tests for ProtoJsonDataFormatDefinition (0% -> 100%)
 */
@DisplayName("ProtoJsonDataFormatDefinition Tests")
class ProtoJsonDataFormatDefinitionTest {

    @Test
    @DisplayName("Should create with default constructor")
    void testDefaultConstructor() {
        // When
        ProtoJsonDataFormatDefinition definition = new ProtoJsonDataFormatDefinition();

        // Then
        assertThat(definition).isNotNull();
        assertThat(definition.getInstanceClassName()).isNull();
    }

    @Test
    @DisplayName("Should create with Class parameter")
    void testClassConstructor() {
        // When
        ProtoJsonDataFormatDefinition definition = new ProtoJsonDataFormatDefinition(SimpleUser.class);

        // Then
        assertThat(definition).isNotNull();
        assertThat(definition.getInstanceClassName())
                .isEqualTo("org.apache.camel.component.protojson.test.proto.SimpleUser");
    }

    @Test
    @DisplayName("Should get and set instance class name")
    void testGetAndSetInstanceClassName() {
        // Given
        ProtoJsonDataFormatDefinition definition = new ProtoJsonDataFormatDefinition();
        String className = "org.apache.camel.component.protojson.test.proto.SimpleUser";

        // When
        definition.setInstanceClassName(className);

        // Then
        assertThat(definition.getInstanceClassName()).isEqualTo(className);
    }

    @Test
    @DisplayName("Should handle null class name")
    void testNullClassName() {
        // Given
        ProtoJsonDataFormatDefinition definition = new ProtoJsonDataFormatDefinition();

        // When
        definition.setInstanceClassName(null);

        // Then
        assertThat(definition.getInstanceClassName()).isNull();
    }
}
