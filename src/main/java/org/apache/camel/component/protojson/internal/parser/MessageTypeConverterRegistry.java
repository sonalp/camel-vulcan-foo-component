package org.apache.camel.component.protojson.internal.parser;

import com.google.protobuf.Descriptors;
import org.apache.camel.component.protojson.converter.MessageTypeConverter;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class MessageTypeConverterRegistry {

    private final Map<String, MessageTypeConverter> converters;
    private final MessageTypeConverter defaultConverter;

    public MessageTypeConverterRegistry(MessageTypeConverter defaultConverter,
                                        Map<String, MessageTypeConverter> converters) {
        this.defaultConverter = defaultConverter;
        this.converters = Collections.unmodifiableMap(new HashMap<>(converters));
    }

    public MessageTypeConverter get(Descriptors.Descriptor desc) {
        MessageTypeConverter c = converters.get(desc.getFullName());
        return c != null ? c : defaultConverter;
    }

    public static MessageTypeConverterRegistry withDefault() {
        // boş registry + default generic converter
        return new MessageTypeConverterRegistry(new DefaultMessageTypeConverter(), Map.of());
    }

    public static Builder newBuilder(MessageTypeConverter defaultConverter) {
        return new Builder(defaultConverter);
    }

    public static final class Builder {
        private final MessageTypeConverter defaultConverter;
        private final Map<String, MessageTypeConverter> map = new HashMap<>();

        public Builder(MessageTypeConverter defaultConverter) {
            this.defaultConverter = defaultConverter;
        }

        public Builder register(Descriptors.Descriptor desc, MessageTypeConverter c) {
            map.put(desc.getFullName(), c);
            return this;
        }

        public MessageTypeConverterRegistry build() {
            return new MessageTypeConverterRegistry(defaultConverter, map);
        }
    }
}
