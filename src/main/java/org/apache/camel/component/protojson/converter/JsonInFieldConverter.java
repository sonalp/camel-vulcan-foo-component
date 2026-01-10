package org.apache.camel.component.protojson.converter;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonParser;
import com.google.protobuf.Descriptors;
import com.google.protobuf.Message;

public interface JsonInFieldConverter {

    /**
     * Does this converter want to handle this field?
     */
    boolean supports(Descriptors.FieldDescriptor field);

    /**
     * Read value from current JSON token and set it in the builder.
     */
    void read(JsonParser parser,
            Message.Builder builder,
            Descriptors.FieldDescriptor field) throws IOException;
}