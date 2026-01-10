package org.apache.camel.component.protojson.internal.printer;

import com.google.protobuf.Descriptors;
import org.apache.camel.component.protojson.converter.MessageJsonConverter;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class MessageJsonConverterRegistry {

    private final Map<String, MessageJsonConverter> converters;
    private final MessageJsonConverter defaultConverter;

    public MessageJsonConverterRegistry(MessageJsonConverter defaultConverter,
                                        Map<String, MessageJsonConverter> converters) {
        this.defaultConverter = defaultConverter;
        this.converters = Collections.unmodifiableMap(new HashMap<>(converters));
    }

    public MessageJsonConverter get(Descriptors.Descriptor desc) {
        MessageJsonConverter c = converters.get(desc.getFullName());
        return c != null ? c : defaultConverter;
    }

    public static MessageJsonConverterRegistry withDefault() {
        return new MessageJsonConverterRegistry(new DefaultMessageJsonConverter(), Map.of());
    }

    public static Builder newBuilder(MessageJsonConverter defaultConverter) {
        return new Builder(defaultConverter);
    }

    public static final class Builder {
        private final MessageJsonConverter defaultConverter;
        private final Map<String, MessageJsonConverter> map = new HashMap<>();

        public Builder(MessageJsonConverter defaultConverter) {
            this.defaultConverter = defaultConverter;
        }

        public Builder register(Descriptors.Descriptor desc, MessageJsonConverter c) {
            map.put(desc.getFullName(), c);
            return this;
        }

        public MessageJsonConverterRegistry build() {
            return new MessageJsonConverterRegistry(defaultConverter, map);
        }
    }
}
