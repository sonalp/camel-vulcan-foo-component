package org.apache.camel.component.protojson;

import org.apache.camel.model.DataFormatDefinition;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Dataformat;

@Dataformat("protojson")
public class ProtoJsonDataFormatDefinition extends DataFormatDefinition {

    @Metadata(label = "common", javaType = "java.lang.String")
    private String instanceClassName;

    public ProtoJsonDataFormatDefinition() {
        super("protojson");
    }

    public ProtoJsonDataFormatDefinition(Class<?> clazz) {
        super("protojson");
        this.instanceClassName = clazz.getName();
    }

    public void setInstanceClassName(String name) {
        this.instanceClassName = name;
    }

    public String getInstanceClassName() {
        return instanceClassName;
    }
}
