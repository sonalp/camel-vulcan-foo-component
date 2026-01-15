package org.apache.camel.component.protojson.internal.parser;

import com.google.protobuf.Descriptors;
import org.apache.camel.component.protojson.converter.MessageTypeConverter;
import org.apache.camel.component.protojson.test.proto.SimpleUser;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for MessageTypeConverterRegistry.
 */
class MessageTypeConverterRegistryTest {

    @Test
    void testWithDefaultRegistry() {
        // When
        MessageTypeConverterRegistry registry = MessageTypeConverterRegistry.withDefault();

        // Then
        assertThat(registry).isNotNull();
    }

    @Test
    void testGetDefaultConverter() {
        // Given
        MessageTypeConverterRegistry registry = MessageTypeConverterRegistry.withDefault();
        Descriptors.Descriptor descriptor = SimpleUser.getDescriptor();

        // When
        MessageTypeConverter converter = registry.get(descriptor);

        // Then
        assertThat(converter).isNotNull();
        assertThat(converter).isInstanceOf(DefaultMessageTypeConverter.class);
    }

    @Test
    void testGetConverterMultipleTimes() {
        // Given
        MessageTypeConverterRegistry registry = MessageTypeConverterRegistry.withDefault();
        Descriptors.Descriptor descriptor = SimpleUser.getDescriptor();

        // When
        MessageTypeConverter converter1 = registry.get(descriptor);
        MessageTypeConverter converter2 = registry.get(descriptor);

        // Then
        assertThat(converter1).isSameAs(converter2);
    }

    @Test
    void testCustomConverter() {
        // Given
        MessageTypeConverter customConverter = new DefaultMessageTypeConverter();
        Descriptors.Descriptor descriptor = SimpleUser.getDescriptor();

        MessageTypeConverterRegistry registry = new MessageTypeConverterRegistry(
                desc -> customConverter
        );

        // When
        MessageTypeConverter result = registry.get(descriptor);

        // Then
        assertThat(result).isSameAs(customConverter);
    }
}
