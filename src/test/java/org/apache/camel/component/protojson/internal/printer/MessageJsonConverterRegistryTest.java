package org.apache.camel.component.protojson.internal.printer;

import com.google.protobuf.Descriptors;
import org.apache.camel.component.protojson.converter.MessageJsonConverter;
import org.apache.camel.component.protojson.test.proto.SimpleUser;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for MessageJsonConverterRegistry.
 */
class MessageJsonConverterRegistryTest {

    @Test
    void testWithDefaultRegistry() {
        // When
        MessageJsonConverterRegistry registry = MessageJsonConverterRegistry.withDefault();

        // Then
        assertThat(registry).isNotNull();
    }

    @Test
    void testGetDefaultConverter() {
        // Given
        MessageJsonConverterRegistry registry = MessageJsonConverterRegistry.withDefault();
        Descriptors.Descriptor descriptor = SimpleUser.getDescriptor();

        // When
        MessageJsonConverter converter = registry.get(descriptor);

        // Then
        assertThat(converter).isNotNull();
        assertThat(converter).isInstanceOf(DefaultMessageJsonConverter.class);
    }

    @Test
    void testGetConverterMultipleTimes() {
        // Given
        MessageJsonConverterRegistry registry = MessageJsonConverterRegistry.withDefault();
        Descriptors.Descriptor descriptor = SimpleUser.getDescriptor();

        // When
        MessageJsonConverter converter1 = registry.get(descriptor);
        MessageJsonConverter converter2 = registry.get(descriptor);

        // Then
        assertThat(converter1).isSameAs(converter2);
    }

    @Test
    void testCustomConverter() {
        // Given
        MessageJsonConverter customConverter = new DefaultMessageJsonConverter();
        Descriptors.Descriptor descriptor = SimpleUser.getDescriptor();

        MessageJsonConverterRegistry registry = new MessageJsonConverterRegistry(
                desc -> customConverter
        );

        // When
        MessageJsonConverter result = registry.get(descriptor);

        // Then
        assertThat(result).isSameAs(customConverter);
    }
}
