package org.apache.camel.component.protojson.converter;

import com.fasterxml.jackson.core.JsonParser;
import com.google.protobuf.Descriptors;
import com.google.protobuf.Message;
import org.apache.camel.component.protojson.engine.ProtoJsonException;
import org.apache.camel.component.protojson.internal.parser.JsonToProtoContext;

import java.io.IOException;

public interface MessageTypeConverter {
    void mergeInto(JsonParser p,
                   Descriptors.Descriptor desc,
                   Message.Builder builder,
                   JsonToProtoContext ctx) throws IOException, ProtoJsonException;
}
