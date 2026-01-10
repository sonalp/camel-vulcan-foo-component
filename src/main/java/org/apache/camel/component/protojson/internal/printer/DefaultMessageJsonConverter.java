package org.apache.camel.component.protojson.internal.printer;

import com.fasterxml.jackson.core.JsonGenerator;
import com.google.protobuf.Descriptors;
import com.google.protobuf.Message;
import org.apache.camel.component.protojson.config.PrinterConfig;
import org.apache.camel.component.protojson.converter.JsonOutFieldConverter;
import org.apache.camel.component.protojson.converter.MessageJsonConverter;
import org.apache.camel.component.protojson.internal.registry.FieldConverterRegistry;

import java.io.IOException;
import java.util.List;

public final class DefaultMessageJsonConverter implements MessageJsonConverter {

    @Override
    public void write(Message msg,
            Descriptors.Descriptor desc,
            JsonGenerator gen,
            ProtoToJsonContext ctx) throws IOException {

        PrinterConfig cfg = ctx.getPrinterConfig();
        gen.writeStartObject();

        for (Descriptors.FieldDescriptor fd : desc.getFields()) {
            if (fd.isRepeated()) {
                if (!cfg.isIncludingDefaultValueFields()
                        && msg.getRepeatedFieldCount(fd) == 0) {
                    continue;
                }
            } else {
                if (!msg.hasField(fd)
                        && !cfg.isIncludingDefaultValueFields()) {
                    continue;
                }
            }

            String fieldName = cfg.isPreservingProtoFieldNames()
                    ? fd.getName()
                    : fd.getJsonName();

            // Check for custom converter first - O(1) lookup with caching
            FieldConverterRegistry<JsonOutFieldConverter> registry = cfg.getOutConverterRegistry();
            JsonOutFieldConverter converter = registry.findConverter(fd);

            if (converter != null) {
                try {
                    gen.writeFieldName(fieldName);
                    converter.write(gen, msg, fd);
                    continue;
                } catch (Exception e) {
                    throw new IOException(
                            "Custom converter failed for field: " + fd.getFullName(), e
                    );
                }
            }

            gen.writeFieldName(fieldName);

            if (fd.isMapField()) {
                writeMapField(msg, fd, gen, ctx);
            } else if (fd.isRepeated()) {
                writeRepeatedField(msg, fd, gen, ctx);
            } else {
                Object v = msg.getField(fd);
                writeSingleField(v, fd, gen, ctx);
            }
        }

        gen.writeEndObject();
    }

    private void writeSingleField(Object value,
            Descriptors.FieldDescriptor fd,
            JsonGenerator gen,
            ProtoToJsonContext ctx) throws IOException {
        PrinterConfig cfg = ctx.getPrinterConfig();

        switch (fd.getJavaType()) {
            case STRING  -> gen.writeString((String) value);
            case INT     -> gen.writeNumber((Integer) value);
            case LONG    -> gen.writeNumber((Long) value);
            case DOUBLE  -> gen.writeNumber((Double) value);
            case FLOAT   -> gen.writeNumber((Float) value);
            case BOOLEAN -> gen.writeBoolean((Boolean) value);
            case BYTE_STRING -> {
                com.google.protobuf.ByteString bs = (com.google.protobuf.ByteString) value;
                String base64 = java.util.Base64.getEncoder().encodeToString(bs.toByteArray());
                gen.writeString(base64);
            }
            case ENUM -> {
                Descriptors.EnumValueDescriptor ev = (Descriptors.EnumValueDescriptor) value;
                if (cfg.isPrintingEnumsAsInts()) {
                    gen.writeNumber(ev.getNumber());
                } else {
                    gen.writeString(ev.getName());
                }
            }
            case MESSAGE -> {
                Message nested = (Message) value;
                Descriptors.Descriptor nestedDesc = fd.getMessageType();
                MessageJsonConverter conv = ctx.getRegistry().get(nestedDesc);
                conv.write(nested, nestedDesc, gen, ctx);
            }
        }
    }

    private void writeRepeatedField(Message msg,
            Descriptors.FieldDescriptor fd,
            JsonGenerator gen,
            ProtoToJsonContext ctx) throws IOException {
        gen.writeStartArray();
        int n = msg.getRepeatedFieldCount(fd);
        for (int i = 0; i < n; i++) {
            Object v = msg.getRepeatedField(fd, i);
            writeSingleField(v, fd, gen, ctx);
        }
        gen.writeEndArray();
    }

    @SuppressWarnings("unchecked")
    private void writeMapField(Message msg,
            Descriptors.FieldDescriptor fd,
            JsonGenerator gen,
            ProtoToJsonContext ctx) throws IOException {
        List<Message> entries = (List<Message>) msg.getField(fd);
        Descriptors.Descriptor entryDesc = fd.getMessageType();
        Descriptors.FieldDescriptor keyFd = entryDesc.findFieldByName("key");
        Descriptors.FieldDescriptor valFd = entryDesc.findFieldByName("value");

        gen.writeStartObject();
        for (Message entry : entries) {
            Object keyObj = entry.getField(keyFd);
            String key = String.valueOf(keyObj);
            gen.writeFieldName(key);
            Object val = entry.getField(valFd);
            writeSingleField(val, valFd, gen, ctx);
        }
        gen.writeEndObject();
    }
}