package org.apache.camel.component.protojson.engine;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.Message;
import org.apache.camel.component.protojson.internal.parser.JsonToProtoContext;
import org.apache.camel.component.protojson.converter.MessageJsonConverter;
import org.apache.camel.component.protojson.internal.parser.ProtoJsonStreamer;
import org.apache.camel.component.protojson.internal.printer.ProtoToJsonContext;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public final class ProtoJsonEngine {

    private final ProtoJsonEngineConfig config;
    private final JsonFactory jsonFactory;

    public ProtoJsonEngine(ProtoJsonEngineConfig config) {
        this.config = config;
        this.jsonFactory = config.getObjectMapper().getFactory();
    }

    // ==== JSON -> Proto (tip biliniyorsa) ====

    public <T extends Message> T parse(InputStream in, Class<T> type)
            throws IOException, ProtoJsonException {

        Message.Builder builder = config.getBuilderFactory().newBuilder(type);
        Descriptors.Descriptor desc = builder.getDescriptorForType();

        try (JsonParser parser = jsonFactory.createParser(in)) {
            JsonToProtoContext ctx = new JsonToProtoContext(
                    config.getParserConfig(),
                    config.getInRegistry(),
                    config.getMetaRegistry()
            );
            ProtoJsonStreamer.merge(parser, desc, builder, ctx);

            @SuppressWarnings("unchecked")
            T result = (T) builder.build();
            return result;
        }
    }

    public <T extends Message> T parse(byte[] data, Class<T> type)
            throws IOException, ProtoJsonException {

        Message.Builder builder = config.getBuilderFactory().newBuilder(type);
        Descriptors.Descriptor desc = builder.getDescriptorForType();

        try (JsonParser parser = jsonFactory.createParser(data)) {
            JsonToProtoContext ctx = new JsonToProtoContext(
                    config.getParserConfig(),
                    config.getInRegistry(),
                    config.getMetaRegistry()
            );
            ProtoJsonStreamer.merge(parser, desc, builder, ctx);

            @SuppressWarnings("unchecked")
            T result = (T) builder.build();
            return result;
        }
    }

    // ==== JSON -> DynamicMessage (sadece descriptor biliniyorsa) ====

    public DynamicMessage parseDynamic(InputStream in,
                                       Descriptors.Descriptor desc)
            throws IOException, ProtoJsonException {

        DynamicMessage.Builder builder = DynamicMessage.newBuilder(desc);

        try (JsonParser parser = jsonFactory.createParser(in)) {
            JsonToProtoContext ctx = new JsonToProtoContext(
                    config.getParserConfig(),
                    config.getInRegistry(),
                    config.getMetaRegistry()
            );
            ProtoJsonStreamer.merge(parser, desc, builder, ctx);
            return builder.build();
        }
    }

    public DynamicMessage parseDynamic(byte[] data,
                                       Descriptors.Descriptor desc)
            throws IOException, ProtoJsonException {

        DynamicMessage.Builder builder = DynamicMessage.newBuilder(desc);

        try (JsonParser parser = jsonFactory.createParser(data)) {
            JsonToProtoContext ctx = new JsonToProtoContext(
                    config.getParserConfig(),
                    config.getInRegistry(),
                    config.getMetaRegistry()
            );
            ProtoJsonStreamer.merge(parser, desc, builder, ctx);
            return builder.build();
        }
    }

    // ==== Proto -> JSON ====

    public void print(Message msg, OutputStream out) throws IOException {
        Descriptors.Descriptor desc = msg.getDescriptorForType();

        try (JsonGenerator gen = jsonFactory.createGenerator(out)) {
            ProtoToJsonContext ctx = new ProtoToJsonContext(
                    config.getObjectMapper(),
                    config.getOutRegistry(),
                    config.getPrinterConfig()
            );
            MessageJsonConverter conv = config.getOutRegistry().get(desc);
            conv.write(msg, desc, gen, ctx);
            gen.flush();
        }
    }

    public String printToString(Message msg) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        print(msg, baos);
        return baos.toString(StandardCharsets.UTF_8);
    }
}
