package org.apache.camel.component.protojson.internal.parser;

import com.fasterxml.jackson.core.JsonParser;
import com.google.protobuf.Descriptors;
import com.google.protobuf.Message;
import org.apache.camel.component.protojson.converter.MessageTypeConverter;
import org.apache.camel.component.protojson.engine.ProtoJsonException;

import java.io.IOException;

public final class DefaultMessageTypeConverter implements MessageTypeConverter {

    @Override
    public void mergeInto(JsonParser p,
                          Descriptors.Descriptor desc,
                          Message.Builder builder,
                          JsonToProtoContext ctx) throws IOException, ProtoJsonException {
        ProtoJsonStreamer.merge(p, desc, builder, ctx);
    }
}
