package org.apache.camel.component.protojson;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.Message;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.protojson.converter.JsonInFieldConverter;
import org.apache.camel.component.protojson.converter.JsonInMapConverter;
import org.apache.camel.component.protojson.converter.JsonOutFieldConverter;
import org.apache.camel.component.protojson.engine.ProtoJsonEngine;
import org.apache.camel.component.protojson.engine.ProtoJsonEngineConfig;
import org.apache.camel.component.protojson.test.proto.SimpleUser;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Comprehensive tests for ProtoJsonDataFormat to increase code coverage.
 * Tests constructors, getters, setters, edge cases, and error scenarios.
 */
@DisplayName("ProtoJsonDataFormat Coverage Tests")
class ProtoJsonDataFormatTest extends BaseProtoJsonTest {

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                ProtoJsonDataFormat format = new ProtoJsonDataFormat(SimpleUser.class);

                from("direct:marshal")
                        .marshal(format)
                        .convertBodyTo(String.class);

                from("direct:unmarshal")
                        .unmarshal(format);
            }
        };
    }

    @Nested
    @DisplayName("Constructor Tests")
    class ConstructorTests {

        @Test
        @DisplayName("Should create with default constructor")
        void shouldCreateWithDefaultConstructor() {
            // When
            ProtoJsonDataFormat format = new ProtoJsonDataFormat();

            // Then
            assertThat(format).isNotNull();
            assertThat(format.getInstanceClass()).isNull();
            assertThat(format.getInstanceClassName()).isNull();
        }

        @Test
        @DisplayName("Should create with Class parameter")
        void shouldCreateWithClassParameter() {
            // When
            ProtoJsonDataFormat format = new ProtoJsonDataFormat(SimpleUser.class);

            // Then
            assertThat(format).isNotNull();
            assertThat(format.getInstanceClass()).isEqualTo(SimpleUser.class);
        }

        @Test
        @DisplayName("Should create with String className parameter")
        void shouldCreateWithStringClassNameParameter() {
            // Given
            String className = "org.apache.camel.component.protojson.test.proto.SimpleUser";

            // When
            ProtoJsonDataFormat format = new ProtoJsonDataFormat(className);

            // Then
            assertThat(format).isNotNull();
            assertThat(format.getInstanceClassName()).isEqualTo(className);
        }
    }

    @Nested
    @DisplayName("Getter/Setter Tests")
    class GetterSetterTests {

        @Test
        @DisplayName("Should get and set instance class name")
        void shouldGetAndSetInstanceClassName() {
            // Given
            ProtoJsonDataFormat format = new ProtoJsonDataFormat();
            String className = "org.apache.camel.component.protojson.test.proto.SimpleUser";

            // When
            format.setInstanceClassName(className);

            // Then
            assertThat(format.getInstanceClassName()).isEqualTo(className);
        }

        @Test
        @DisplayName("Should get and set instance class")
        void shouldGetAndSetInstanceClass() {
            // Given
            ProtoJsonDataFormat format = new ProtoJsonDataFormat();

            // When
            format.setInstanceClass(SimpleUser.class);

            // Then
            assertThat(format.getInstanceClass()).isEqualTo(SimpleUser.class);
        }

        @Test
        @DisplayName("Should get and set engine")
        void shouldGetAndSetEngine() throws Exception {
            // Given
            ProtoJsonDataFormat format = new ProtoJsonDataFormat(SimpleUser.class);
            ProtoJsonEngine customEngine = new ProtoJsonEngine(
                    ProtoJsonEngineConfig.newBuilder().build()
            );

            // When
            format.setEngine(customEngine);

            // Then
            assertThat(format.getEngine()).isEqualTo(customEngine);
        }

        @Test
        @DisplayName("Should get and set ignoring unknown fields")
        void shouldGetAndSetIgnoringUnknownFields() {
            // Given
            ProtoJsonDataFormat format = new ProtoJsonDataFormat();

            // When
            format.setIgnoringUnknownFields(true);

            // Then
            assertThat(format.getIgnoringUnknownFields()).isTrue();
        }

        @Test
        @DisplayName("Should get and set accept numeric enums")
        void shouldGetAndSetAcceptNumericEnums() {
            // Given
            ProtoJsonDataFormat format = new ProtoJsonDataFormat();

            // When
            format.setAcceptNumericEnums(false);

            // Then
            assertThat(format.getAcceptNumericEnums()).isFalse();
        }

        @Test
        @DisplayName("Should get and set allow null for scalars")
        void shouldGetAndSetAllowNullForScalars() {
            // Given
            ProtoJsonDataFormat format = new ProtoJsonDataFormat();

            // When
            format.setAllowNullForScalars(false);

            // Then
            assertThat(format.getAllowNullForScalars()).isFalse();
        }

        @Test
        @DisplayName("Should get and set including default value fields")
        void shouldGetAndSetIncludingDefaultValueFields() {
            // Given
            ProtoJsonDataFormat format = new ProtoJsonDataFormat();

            // When
            format.setIncludingDefaultValueFields(true);

            // Then
            assertThat(format.getIncludingDefaultValueFields()).isTrue();
        }

        @Test
        @DisplayName("Should get and set preserving proto field names")
        void shouldGetAndSetPreservingProtoFieldNames() {
            // Given
            ProtoJsonDataFormat format = new ProtoJsonDataFormat();

            // When
            format.setPreservingProtoFieldNames(true);

            // Then
            assertThat(format.getPreservingProtoFieldNames()).isTrue();
        }

        @Test
        @DisplayName("Should get and set printing enums as ints")
        void shouldGetAndSetPrintingEnumsAsInts() {
            // Given
            ProtoJsonDataFormat format = new ProtoJsonDataFormat();

            // When
            format.setPrintingEnumsAsInts(true);

            // Then
            assertThat(format.getPrintingEnumsAsInts()).isTrue();
        }

        @Test
        @DisplayName("Should get and set object mapper")
        void shouldGetAndSetObjectMapper() {
            // Given
            ProtoJsonDataFormat format = new ProtoJsonDataFormat();
            ObjectMapper customMapper = new ObjectMapper();

            // When
            format.setObjectMapper(customMapper);

            // Then
            assertThat(format.getObjectMapper()).isEqualTo(customMapper);
        }

        @Test
        @DisplayName("Should get and set auto discover converters")
        void shouldGetAndSetAutoDiscoverConverters() {
            // Given
            ProtoJsonDataFormat format = new ProtoJsonDataFormat();

            // When
            format.setAutoDiscoverConverters(false);

            // Then
            assertThat(format.getAutoDiscoverConverters()).isFalse();
        }

        @Test
        @DisplayName("Should get and set in field converters")
        void shouldGetAndSetInFieldConverters() {
            // Given
            ProtoJsonDataFormat format = new ProtoJsonDataFormat();
            List<JsonInFieldConverter> converters = new ArrayList<>();

            // When
            format.setInFieldConverters(converters);

            // Then
            assertThat(format.getInFieldConverters()).isEqualTo(converters);
        }

        @Test
        @DisplayName("Should get and set in map converters")
        void shouldGetAndSetInMapConverters() {
            // Given
            ProtoJsonDataFormat format = new ProtoJsonDataFormat();
            List<JsonInMapConverter> converters = new ArrayList<>();

            // When
            format.setInMapConverters(converters);

            // Then
            assertThat(format.getInMapConverters()).isEqualTo(converters);
        }

        @Test
        @DisplayName("Should get and set out field converters")
        void shouldGetAndSetOutFieldConverters() {
            // Given
            ProtoJsonDataFormat format = new ProtoJsonDataFormat();
            List<JsonOutFieldConverter> converters = new ArrayList<>();

            // When
            format.setOutFieldConverters(converters);

            // Then
            assertThat(format.getOutFieldConverters()).isEqualTo(converters);
        }
    }

    @Nested
    @DisplayName("CamelContext Tests")
    class CamelContextTests {

        @Test
        @DisplayName("Should get and set CamelContext")
        void shouldGetAndSetCamelContext() {
            // Given
            ProtoJsonDataFormat format = new ProtoJsonDataFormat();
            CamelContext camelContext = new DefaultCamelContext();

            // When
            format.setCamelContext(camelContext);

            // Then
            assertThat(format.getCamelContext()).isEqualTo(camelContext);
        }

        @Test
        @DisplayName("Should return data format name")
        void shouldReturnDataFormatName() {
            // Given
            ProtoJsonDataFormat format = new ProtoJsonDataFormat();

            // When
            String name = format.getDataFormatName();

            // Then
            assertThat(name).isEqualTo("protojson");
        }
    }

    @Nested
    @DisplayName("Engine Configuration Tests")
    class EngineConfigurationTests {

        @Test
        @DisplayName("Should use pre-configured engine when set")
        void shouldUsePreConfiguredEngine() throws Exception {
            // Given
            ProtoJsonDataFormat format = new ProtoJsonDataFormat(SimpleUser.class);
            ProtoJsonEngine customEngine = new ProtoJsonEngine(
                    ProtoJsonEngineConfig.newBuilder().build()
            );
            format.setEngine(customEngine);
            format.setCamelContext(context);

            // When
            format.start();

            // Then - should not create new engine
            assertThat(format.getEngine()).isEqualTo(customEngine);
            format.stop();
        }

        @Test
        @DisplayName("Should disable auto-discovery when configured")
        void shouldDisableAutoDiscoveryWhenConfigured() throws Exception {
            // Given
            ProtoJsonDataFormat format = new ProtoJsonDataFormat(SimpleUser.class);
            format.setAutoDiscoverConverters(false);
            format.setCamelContext(context);

            // When
            format.start();

            // Then - should start successfully without discovering converters
            assertThat(format.getAutoDiscoverConverters()).isFalse();
            format.stop();
        }

        @Test
        @DisplayName("Should use custom ObjectMapper")
        void shouldUseCustomObjectMapper() throws Exception {
            // Given
            ObjectMapper customMapper = new ObjectMapper();
            ProtoJsonDataFormat format = new ProtoJsonDataFormat(SimpleUser.class);
            format.setObjectMapper(customMapper);
            format.setCamelContext(context);

            // When
            format.start();

            // Then
            assertThat(format.getObjectMapper()).isEqualTo(customMapper);
            format.stop();
        }
    }

    @Nested
    @DisplayName("Programmatic Converter Registration Tests")
    class ProgrammaticConverterTests {

        @Test
        @DisplayName("Should register programmatic in-field converters")
        void shouldRegisterProgrammaticInFieldConverters() throws Exception {
            // Given
            ProtoJsonDataFormat format = new ProtoJsonDataFormat(SimpleUser.class);
            List<JsonInFieldConverter> inConverters = new ArrayList<>();
            inConverters.add(new TestInFieldConverter());
            format.setInFieldConverters(inConverters);
            format.setCamelContext(context);

            // When
            format.start();

            // Then
            assertThat(format.getInFieldConverters()).hasSize(1);
            format.stop();
        }

        @Test
        @DisplayName("Should register programmatic in-map converters")
        void shouldRegisterProgrammaticInMapConverters() throws Exception {
            // Given
            ProtoJsonDataFormat format = new ProtoJsonDataFormat(SimpleUser.class);
            List<JsonInMapConverter> mapConverters = new ArrayList<>();
            mapConverters.add(new TestInMapConverter());
            format.setInMapConverters(mapConverters);
            format.setCamelContext(context);

            // When
            format.start();

            // Then
            assertThat(format.getInMapConverters()).hasSize(1);
            format.stop();
        }

        @Test
        @DisplayName("Should register programmatic out-field converters")
        void shouldRegisterProgrammaticOutFieldConverters() throws Exception {
            // Given
            ProtoJsonDataFormat format = new ProtoJsonDataFormat(SimpleUser.class);
            List<JsonOutFieldConverter> outConverters = new ArrayList<>();
            outConverters.add(new TestOutFieldConverter());
            format.setOutFieldConverters(outConverters);
            format.setCamelContext(context);

            // When
            format.start();

            // Then
            assertThat(format.getOutFieldConverters()).hasSize(1);
            format.stop();
        }
    }

    @Nested
    @DisplayName("Marshal Error Tests")
    class MarshalErrorTests {

        @Test
        @DisplayName("Should throw exception when marshaling non-Message object")
        void shouldThrowExceptionWhenMarshalingNonMessage() throws Exception {
            // Given
            ProtoJsonDataFormat format = new ProtoJsonDataFormat(SimpleUser.class);
            format.setCamelContext(context);
            format.start();

            Exchange exchange = new DefaultExchange(context);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            String notAMessage = "This is not a protobuf message";

            // When/Then
            assertThatThrownBy(() -> format.marshal(exchange, notAMessage, out))
                    .isInstanceOf(CamelExecutionException.class)
                    .hasMessageContaining("ProtoJsonDataFormat marshal expects a Protobuf Message");

            format.stop();
        }
    }

    @Nested
    @DisplayName("Unmarshal with InstanceClassName Tests")
    class UnmarshalInstanceClassNameTests {

        @Test
        @DisplayName("Should resolve target type from instanceClassName")
        void shouldResolveTargetTypeFromInstanceClassName() throws Exception {
            // Given
            ProtoJsonDataFormat format = new ProtoJsonDataFormat();
            format.setInstanceClassName("org.apache.camel.component.protojson.test.proto.SimpleUser");
            format.setCamelContext(context);
            format.start();

            String json = "{\"name\":\"John\",\"age\":30}";
            Exchange exchange = new DefaultExchange(context);
            ByteArrayInputStream in = new ByteArrayInputStream(json.getBytes());

            // When
            Object result = format.unmarshal(exchange, in);

            // Then
            assertThat(result).isInstanceOf(SimpleUser.class);
            SimpleUser user = (SimpleUser) result;
            assertThat(user.getName()).isEqualTo("John");
            assertThat(user.getAge()).isEqualTo(30);

            format.stop();
        }

        @Test
        @DisplayName("Should throw exception when className is invalid")
        void shouldThrowExceptionWhenClassNameIsInvalid() throws Exception {
            // Given
            ProtoJsonDataFormat format = new ProtoJsonDataFormat();
            format.setInstanceClassName("invalid.NonExistentClass");
            format.setCamelContext(context);
            format.start();

            String json = "{\"name\":\"John\"}";
            Exchange exchange = new DefaultExchange(context);
            ByteArrayInputStream in = new ByteArrayInputStream(json.getBytes());

            // When/Then
            assertThatThrownBy(() -> format.unmarshal(exchange, in))
                    .hasMessageContaining("Cannot resolve Protobuf Message class");

            format.stop();
        }

        @Test
        @DisplayName("Should throw exception when className is not a Message type")
        void shouldThrowExceptionWhenClassNameIsNotMessageType() throws Exception {
            // Given
            ProtoJsonDataFormat format = new ProtoJsonDataFormat();
            format.setInstanceClassName("java.lang.String");
            format.setCamelContext(context);
            format.start();

            String json = "{\"name\":\"John\"}";
            Exchange exchange = new DefaultExchange(context);
            ByteArrayInputStream in = new ByteArrayInputStream(json.getBytes());

            // When/Then
            assertThatThrownBy(() -> format.unmarshal(exchange, in))
                    .hasMessageContaining("is not a Protobuf Message type");

            format.stop();
        }

        @Test
        @DisplayName("Should throw exception when no instance class configured")
        void shouldThrowExceptionWhenNoInstanceClassConfigured() throws Exception {
            // Given
            ProtoJsonDataFormat format = new ProtoJsonDataFormat();
            format.setCamelContext(context);
            format.start();

            String json = "{\"name\":\"John\"}";
            Exchange exchange = new DefaultExchange(context);
            ByteArrayInputStream in = new ByteArrayInputStream(json.getBytes());

            // When/Then
            assertThatThrownBy(() -> format.unmarshal(exchange, in))
                    .hasMessageContaining("ProtoJsonDataFormat requires instanceClass/instanceClassName");

            format.stop();
        }
    }

    @Nested
    @DisplayName("Lifecycle Tests")
    class LifecycleTests {

        @Test
        @DisplayName("Should start and stop successfully")
        void shouldStartAndStopSuccessfully() throws Exception {
            // Given
            ProtoJsonDataFormat format = new ProtoJsonDataFormat(SimpleUser.class);
            format.setCamelContext(context);

            // When
            format.start();
            format.stop();

            // Then - no exception should be thrown
        }

        @Test
        @DisplayName("Should handle multiple start/stop cycles")
        void shouldHandleMultipleStartStopCycles() throws Exception {
            // Given
            ProtoJsonDataFormat format = new ProtoJsonDataFormat(SimpleUser.class);
            format.setCamelContext(context);

            // When/Then
            format.start();
            format.stop();
            format.start();
            format.stop();
        }
    }

    // Test helper converters

    private static class TestInFieldConverter implements JsonInFieldConverter {
        @Override
        public boolean canConvert(String fieldName, com.google.protobuf.Descriptors.FieldDescriptor.Type fieldType) {
            return false;
        }

        @Override
        public Object convert(com.fasterxml.jackson.core.JsonParser parser,
                            com.google.protobuf.Descriptors.FieldDescriptor.Type fieldType) {
            return null;
        }
    }

    private static class TestInMapConverter implements JsonInMapConverter {
        @Override
        public boolean canConvert(String fieldName,
                                com.google.protobuf.Descriptors.FieldDescriptor.Type keyType,
                                com.google.protobuf.Descriptors.FieldDescriptor.Type valueType) {
            return false;
        }

        @Override
        public void convert(com.fasterxml.jackson.core.JsonParser parser,
                          com.google.protobuf.Descriptors.FieldDescriptor.Type keyType,
                          com.google.protobuf.Descriptors.FieldDescriptor.Type valueType,
                          java.util.function.BiConsumer<Object, Object> consumer) {
        }
    }

    private static class TestOutFieldConverter implements JsonOutFieldConverter {
        @Override
        public boolean canConvert(String fieldName, Object value) {
            return false;
        }

        @Override
        public void convert(Object value, com.fasterxml.jackson.core.JsonGenerator generator) {
        }
    }
}
