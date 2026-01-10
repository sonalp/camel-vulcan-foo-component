// Yeni dosya: converter/JsonInMapConverter.java

package org.apache.camel.component.protojson.converter;

import com.fasterxml.jackson.core.JsonParser;
import com.google.protobuf.Descriptors;
import com.google.protobuf.Message;

import java.io.IOException;

/**
 * Converter for map field values during JSON to Proto conversion.
 */
public interface JsonInMapConverter {

    /**
     * Does this converter want to handle values of this map field?
     */
    boolean supports(Descriptors.FieldDescriptor mapField);

    /**
     * Read map value from current JSON token and return the converted value.
     * The key is already parsed and provided.
     *
     * @param parser JSON parser positioned at value token
     * @param mapField The map field descriptor
     * @param valueField The value field descriptor within the map entry
     * @param key The already-parsed map key
     * @return The converted value to be set in the map entry
     */
    Object readValue(JsonParser parser,
                     Descriptors.FieldDescriptor mapField,
                     Descriptors.FieldDescriptor valueField,
                     Object key) throws IOException;
}