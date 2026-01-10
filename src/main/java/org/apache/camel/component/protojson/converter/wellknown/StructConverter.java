package org.apache.camel.component.protojson.converter.wellknown;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.google.protobuf.Descriptors;
import com.google.protobuf.ListValue;
import com.google.protobuf.Message;
import com.google.protobuf.NullValue;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import org.apache.camel.component.protojson.converter.JsonInFieldConverter;
import org.apache.camel.component.protojson.converter.JsonOutFieldConverter;

import java.io.IOException;
import java.util.List;

/**
 * Converter for google.protobuf.Struct, Value, and ListValue.
 * 
 * JSON mapping:
 * - Struct    -> JSON object
 * - Value     -> any JSON value (null, bool, number, string, array, object)
 * - ListValue -> JSON array
 */
public class StructConverter implements JsonInFieldConverter, JsonOutFieldConverter {

    private static final String STRUCT_TYPE = "google.protobuf.Struct";
    private static final String VALUE_TYPE = "google.protobuf.Value";
    private static final String LIST_VALUE_TYPE = "google.protobuf.ListValue";

    @Override
    public boolean supports(Descriptors.FieldDescriptor field) {
        if (field.getJavaType() != Descriptors.FieldDescriptor.JavaType.MESSAGE) {
            return false;
        }
        String typeName = field.getMessageType().getFullName();
        return STRUCT_TYPE.equals(typeName) 
                || VALUE_TYPE.equals(typeName) 
                || LIST_VALUE_TYPE.equals(typeName);
    }

    // ==================== JSON -> Proto ====================

    @Override
    public void read(JsonParser parser, Message.Builder builder,
                     Descriptors.FieldDescriptor field) throws IOException {
        
        String typeName = field.getMessageType().getFullName();
        
        Message value = switch (typeName) {
            case STRUCT_TYPE -> parseStruct(parser);
            case VALUE_TYPE -> parseValue(parser);
            case LIST_VALUE_TYPE -> parseListValue(parser);
            default -> throw new IOException("Unsupported type: " + typeName);
        };
        
        if (value != null) {
            builder.setField(field, value);
        }
    }

    private Struct parseStruct(JsonParser p) throws IOException {
        JsonToken token = p.currentToken();
        
        if (token == JsonToken.VALUE_NULL) {
            return null;
        }
        
        if (token != JsonToken.START_OBJECT) {
            throw new IOException("Struct must be a JSON object, got: " + token);
        }

        Struct.Builder structBuilder = Struct.newBuilder();

        while (p.nextToken() != JsonToken.END_OBJECT) {
            String fieldName = p.getCurrentName();
            p.nextToken(); // move to value
            
            Value value = parseValue(p);
            if (value != null) {
                structBuilder.putFields(fieldName, value);
            }
        }

        return structBuilder.build();
    }

    private Value parseValue(JsonParser p) throws IOException {
        Value.Builder valueBuilder = Value.newBuilder();
        JsonToken token = p.currentToken();

        switch (token) {
            case VALUE_NULL -> valueBuilder.setNullValue(NullValue.NULL_VALUE);
            case VALUE_TRUE -> valueBuilder.setBoolValue(true);
            case VALUE_FALSE -> valueBuilder.setBoolValue(false);
            case VALUE_NUMBER_INT, VALUE_NUMBER_FLOAT -> 
                valueBuilder.setNumberValue(p.getDoubleValue());
            case VALUE_STRING -> valueBuilder.setStringValue(p.getText());
            case START_ARRAY -> {
                ListValue listValue = parseListValue(p);
                if (listValue != null) {
                    valueBuilder.setListValue(listValue);
                }
            }
            case START_OBJECT -> {
                Struct struct = parseStruct(p);
                if (struct != null) {
                    valueBuilder.setStructValue(struct);
                }
            }
            default -> throw new IOException("Unexpected token for Value: " + token);
        }

        return valueBuilder.build();
    }

    private ListValue parseListValue(JsonParser p) throws IOException {
        JsonToken token = p.currentToken();
        
        if (token == JsonToken.VALUE_NULL) {
            return null;
        }
        
        if (token != JsonToken.START_ARRAY) {
            throw new IOException("ListValue must be a JSON array, got: " + token);
        }

        ListValue.Builder listBuilder = ListValue.newBuilder();

        while (p.nextToken() != JsonToken.END_ARRAY) {
            Value value = parseValue(p);
            if (value != null) {
                listBuilder.addValues(value);
            }
        }

        return listBuilder.build();
    }

    // ==================== Proto -> JSON ====================

    @Override
    public void write(JsonGenerator gen, Message message,
                      Descriptors.FieldDescriptor field) throws IOException {
        Message msg = (Message) message.getField(field);
        String typeName = field.getMessageType().getFullName();

        switch (typeName) {
            case STRUCT_TYPE -> writeStruct(gen, msg);
            case VALUE_TYPE -> writeValueMessage(gen, msg);
            case LIST_VALUE_TYPE -> writeListValue(gen, msg);
            default -> throw new IOException("Unsupported type: " + typeName);
        }
    }

    private void writeStruct(JsonGenerator gen, Message struct) throws IOException {
        gen.writeStartObject();
        
        Descriptors.FieldDescriptor fieldsDesc = struct.getDescriptorForType()
                .findFieldByName("fields");
        
        @SuppressWarnings("unchecked")
        List<Message> entries = (List<Message>) struct.getField(fieldsDesc);
        
        Descriptors.Descriptor entryDesc = fieldsDesc.getMessageType();
        Descriptors.FieldDescriptor keyFd = entryDesc.findFieldByName("key");
        Descriptors.FieldDescriptor valueFd = entryDesc.findFieldByName("value");

        for (Message entry : entries) {
            String key = (String) entry.getField(keyFd);
            Message value = (Message) entry.getField(valueFd);
            
            gen.writeFieldName(key);
            writeValueMessage(gen, value);
        }
        
        gen.writeEndObject();
    }

    private void writeValueMessage(JsonGenerator gen, Message value) throws IOException {
        Descriptors.Descriptor desc = value.getDescriptorForType();
        Descriptors.OneofDescriptor kindOneof = desc.getOneofs().get(0); // "kind" oneof
        
        Descriptors.FieldDescriptor setField = value.getOneofFieldDescriptor(kindOneof);
        
        if (setField == null) {
            gen.writeNull();
            return;
        }

        switch (setField.getName()) {
            case "null_value" -> gen.writeNull();
            case "bool_value" -> gen.writeBoolean((Boolean) value.getField(setField));
            case "number_value" -> gen.writeNumber((Double) value.getField(setField));
            case "string_value" -> gen.writeString((String) value.getField(setField));
            case "list_value" -> writeListValue(gen, (Message) value.getField(setField));
            case "struct_value" -> writeStruct(gen, (Message) value.getField(setField));
            default -> throw new IOException("Unknown Value kind: " + setField.getName());
        }
    }

    private void writeListValue(JsonGenerator gen, Message listValue) throws IOException {
        gen.writeStartArray();
        
        Descriptors.FieldDescriptor valuesDesc = listValue.getDescriptorForType()
                .findFieldByName("values");
        
        int count = listValue.getRepeatedFieldCount(valuesDesc);
        for (int i = 0; i < count; i++) {
            Message value = (Message) listValue.getRepeatedField(valuesDesc, i);
            writeValueMessage(gen, value);
        }
        
        gen.writeEndArray();
    }
}
