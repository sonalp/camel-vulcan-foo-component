package org.apache.camel.component.protojson.converter;

import com.google.protobuf.Message;
import com.google.protobuf.Descriptors;
import com.fasterxml.jackson.core.JsonGenerator;
import java.io.IOException;

public interface JsonOutFieldConverter {

    /**
     * Does this converter want to handle this field?
     */
    boolean supports(Descriptors.FieldDescriptor field);

    /**
     * Read field value from Proto Message and write to JSON.
     */
    void write(JsonGenerator gen,
            Message message,
            Descriptors.FieldDescriptor field) throws IOException;
}