package org.apache.camel.component.protojson.converter;

import com.fasterxml.jackson.core.JsonGenerator;
import com.google.protobuf.Descriptors;
import com.google.protobuf.Message;
import org.apache.camel.component.protojson.internal.printer.ProtoToJsonContext;

import java.io.IOException;

public interface MessageJsonConverter {
    void write(Message msg,
               Descriptors.Descriptor desc,
               JsonGenerator gen,
               ProtoToJsonContext ctx) throws IOException;
}
