package org.apache.camel.component.protojson.internal;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonFactoryBuilder;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import org.apache.camel.component.protojson.config.ParserConfig;

/**
 * Jackson JsonFactory ayarlarını merkezi olarak yapan helper.
 */
public final class JacksonConfig {

    private JacksonConfig() {
    }

    public static JsonFactory createJsonFactory(ParserConfig cfg) {

        StreamReadConstraints constraints = StreamReadConstraints.builder()
                .maxNumberLength(1_000)
                .maxStringLength(1_000_000)
                .build();

        return JsonFactory.builder()
                .streamReadConstraints(constraints)

                // SAYISAL OPTİMİZASYON — doğru kullanım
                .enable(StreamReadFeature.USE_FAST_DOUBLE_PARSER)
                // .enable(StreamReadFeature.USE_FAST_BIG_NUMBER_PARSER) // gerekiyorsa

                // FIELD NAME OPTİMİZASYONLARI
                .configure(JsonFactory.Feature.CANONICALIZE_FIELD_NAMES, true)
                .configure(JsonFactory.Feature.INTERN_FIELD_NAMES, true)

                // (opsiyonel tolerans kontrolleri)
                // .disable(JsonReadFeature.ALLOW_SINGLE_QUOTES)

                .build();
    }
}
