package org.apache.camel.component.protojson.converter.wellknown;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.google.protobuf.*;
import com.google.protobuf.DescriptorProtos.DescriptorProto;
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

class AnyConverterBranchTest {

    private final JsonFactory jsonFactory = new JsonFactory();

    // ----------------------------------------------------------------------
    // JSON -> Proto tarafındaki eksik branchler
    // ----------------------------------------------------------------------

    @Test
    void read_shouldThrowWhenJsonIsNotObject() throws Exception {
        AnyConverter converter = AnyConverter.create();
        JsonParser parser = jsonFactory.createParser("\"not-an-object\"");
        parser.nextToken(); // VALUE_STRING

        IOException ex = assertThrows(
                IOException.class,
                () -> converter.read(parser, null, null) // builder/field burada kullanılmıyor
        );
        assertTrue(ex.getMessage().contains("Any must be a JSON object"));
    }

    @Test
    void readJsonValue_shouldThrowOnUnexpectedToken() throws Exception {
        AnyConverter converter = AnyConverter.create();

        JsonParser p = jsonFactory.createParser("{\"foo\": 1}");
        p.nextToken(); // START_OBJECT
        p.nextToken(); // FIELD_NAME -> beklenmeyen token

        Method m = AnyConverter.class.getDeclaredMethod("readJsonValue", JsonParser.class);
        m.setAccessible(true);

        InvocationTargetException ite = assertThrows(
                InvocationTargetException.class,
                () -> m.invoke(converter, p)
        );
        assertTrue(ite.getCause() instanceof IOException);
        assertTrue(ite.getCause().getMessage().contains("Unexpected token"));
    }

    @Test
    void convertSingleValue_returnsNullWhenValueIsNull() throws Exception {
        AnyConverter converter = AnyConverter.create();

        // StringValue.value alanı (basit bir alan olsun diye)
        Descriptors.FieldDescriptor fd =
                com.google.protobuf.StringValue.getDescriptor().findFieldByName("value");

        Method m = AnyConverter.class.getDeclaredMethod(
                "convertSingleValue",
                Descriptors.FieldDescriptor.class,
                Object.class
        );
        m.setAccessible(true);

        Object result = m.invoke(converter, fd, (Object) null);
        assertNull(result); // value == null branch
    }

    @Test
    void convertSingleValue_byteStringNonString_returnsNull() throws Exception {
        AnyConverter converter = AnyConverter.create();

        Descriptors.FieldDescriptor fd =
                com.google.protobuf.BytesValue.getDescriptor().findFieldByName("value");

        Method m = AnyConverter.class.getDeclaredMethod(
                "convertSingleValue",
                Descriptors.FieldDescriptor.class,
                Object.class
        );
        m.setAccessible(true);

        Object result = m.invoke(converter, fd, 123); // String olmayan bir değer
        assertNull(result); // BYTE_STRING case'deki "yield null" branch
    }

    @Test
    void convertSingleValue_messageNonMap_returnsNull() throws Exception {
        AnyConverter converter = AnyConverter.create();

        // Struct.fields MESSAGE tipinde bir map field (JavaType.MESSAGE)
        Descriptors.FieldDescriptor fd =
                com.google.protobuf.Struct.getDescriptor().findFieldByName("fields");

        Method m = AnyConverter.class.getDeclaredMethod(
                "convertSingleValue",
                Descriptors.FieldDescriptor.class,
                Object.class
        );
        m.setAccessible(true);

        Object result = m.invoke(converter, fd, "not-a-map");
        assertNull(result); // MESSAGE case'in "yield null" branch'i
    }

    @Test
    void findFieldByJsonName_returnsNullWhenFieldNotFound() throws Exception {
        AnyConverter converter = AnyConverter.create();

        Method m = AnyConverter.class.getDeclaredMethod(
                "findFieldByJsonName",
                Descriptors.Descriptor.class,
                String.class
        );
        m.setAccessible(true);

        Descriptors.Descriptor desc = com.google.protobuf.Timestamp.getDescriptor();

        Object result = m.invoke(converter, desc, "doesNotExist");
        assertNull(result); // method'un sonundaki "return null" branch
    }

    // ----------------------------------------------------------------------
    // Proto -> JSON tarafındaki eksik branchler
    // ----------------------------------------------------------------------

    @Test
    void writeJsonValue_writesNullWhenValueIsNull() throws Exception {
        AnyConverter converter = AnyConverter.create();

        // INT tipinde herhangi bir alan (ör: Timestamp.seconds)
        Descriptors.FieldDescriptor fd =
                com.google.protobuf.Timestamp.getDescriptor().findFieldByName("seconds");

        Method m = AnyConverter.class.getDeclaredMethod(
                "writeJsonValue",
                com.fasterxml.jackson.core.JsonGenerator.class,
                Object.class,
                Descriptors.FieldDescriptor.class
        );
        m.setAccessible(true);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        JsonGenerator gen = jsonFactory.createGenerator(baos);

        m.invoke(converter, gen, null, fd); // value == null
        gen.flush();

        String json = baos.toString(StandardCharsets.UTF_8);
        assertEquals("null", json); // JSON null yazılmalı
    }

    @Test
    void writeJsonValue_messageDynamicMessageBranchIsHit() throws Exception {
        AnyConverter converter = AnyConverter.create();

        // ListValue.values -> MESSAGE (Value) ve repeated
        Descriptors.FieldDescriptor fd =
                com.google.protobuf.ListValue.getDescriptor().findFieldByName("values");

        // İçeride yazılacak VALUE message'ı (DynamicMessage)
        DynamicMessage valueMsg = DynamicMessage.newBuilder(
                com.google.protobuf.Value.getDescriptor()
        ).build();

        Method m = AnyConverter.class.getDeclaredMethod(
                "writeJsonValue",
                com.fasterxml.jackson.core.JsonGenerator.class,
                Object.class,
                Descriptors.FieldDescriptor.class
        );
        m.setAccessible(true);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        JsonGenerator gen = jsonFactory.createGenerator(baos);

        // value instanceof DynamicMessage -> ilk MESSAGE branch
        m.invoke(converter, gen, valueMsg, fd);
        gen.flush();

        String json = baos.toString(StandardCharsets.UTF_8);
        // Boş bir obje beklenir: {}
        assertEquals("{}", json.trim());
    }

    @Test
    void writeJsonValue_messageNormalMessageBranchIsHit() throws Exception {
        AnyConverter converter = AnyConverter.create();

        // Yine MESSAGE tipinde bir field descriptor kullanalım
        Descriptors.FieldDescriptor fd =
                com.google.protobuf.ListValue.getDescriptor().findFieldByName("values");

        // Bu sefer normal bir Message instance verelim (Any default instance)
        Message msgValue = Any.getDefaultInstance();

        Method m = AnyConverter.class.getDeclaredMethod(
                "writeJsonValue",
                com.fasterxml.jackson.core.JsonGenerator.class,
                Object.class,
                Descriptors.FieldDescriptor.class
        );
        m.setAccessible(true);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        JsonGenerator gen = jsonFactory.createGenerator(baos);

        // value instanceof DynamicMessage false, instanceof Message true -> ikinci MESSAGE branch
        m.invoke(converter, gen, msgValue, fd);
        gen.flush();

        String json = baos.toString(StandardCharsets.UTF_8);
        // İçteki Any boş olduğundan yine {} beklenir
        assertEquals("{}", json.trim());
    }

    // ----------------------------------------------------------------------
    // write(...) içindeki eksik branchler:
    //  - typeUrl prefixsiz (typeUrl.startsWith(...) false)
    //  - parseFrom -> InvalidProtocolBufferException catch branch
    // ----------------------------------------------------------------------

    @Test
    void write_usesNonPrefixedTypeUrlAndFallsBackToBase64OnInvalidBytes() throws Exception {
        AnyConverter converter = AnyConverter.create();

        // 1. typeRegistry'ye Any descriptor'ını elle ekleyelim
        Field registryField = AnyConverter.class.getDeclaredField("typeRegistry");
        registryField.setAccessible(true);
        @SuppressWarnings("unchecked")
        ConcurrentHashMap<String, Descriptors.Descriptor> registry =
                (ConcurrentHashMap<String, Descriptors.Descriptor>) registryField.get(converter);

        Descriptors.Descriptor anyDesc = Any.getDescriptor();
        registry.put(anyDesc.getFullName(), anyDesc);

        // 2. google.protobuf.Any mesajını DynamicMessage olarak inşa et
        DynamicMessage.Builder anyBuilder = DynamicMessage.newBuilder(anyDesc);
        Descriptors.FieldDescriptor typeUrlFd = anyDesc.findFieldByName("type_url");
        Descriptors.FieldDescriptor valueFd = anyDesc.findFieldByName("value");

        // Prefixsiz typeUrl -> ternary'nin ":" kolu çalışsın
        String nonPrefixedTypeUrl = anyDesc.getFullName(); // "google.protobuf.Any"
        anyBuilder.setField(typeUrlFd, nonPrefixedTypeUrl);

        // Parser'ı patlatacak rastgele / bozuk bytes (tek 0x80 vb.)
        ByteString invalidBytes = ByteString.copyFrom(new byte[]{(byte) 0x80});
        anyBuilder.setField(valueFd, invalidBytes);

        DynamicMessage anyMessage = anyBuilder.build();

        // 3. Bu Any'yi taşıyacak runtime'da bir "AnyContainer" descriptor'ı üret
        Descriptors.Descriptor anyContainerDesc = buildAnyContainerDescriptor(anyDesc);
        DynamicMessage.Builder containerBuilder = DynamicMessage.newBuilder(anyContainerDesc);
        Descriptors.FieldDescriptor anyField =
                anyContainerDesc.findFieldByName("any");

        containerBuilder.setField(anyField, anyMessage);
        DynamicMessage containerMsg = containerBuilder.build();

        // 4. write(...) çağrısı -> typeUrl prefixsiz, parseFrom(Any, invalidBytes) patlayacak,
        //    catch bloğunda "value" alanı base64 string olarak yazılacak
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        JsonGenerator gen = jsonFactory.createGenerator(baos);

        converter.write(gen, containerMsg, anyField);
        gen.flush();

        String json = baos.toString(StandardCharsets.UTF_8);

        // @type prefixsiz, value ise base64 encoded olmalı
        assertTrue(json.contains("\"@type\""));
        assertTrue(json.contains(nonPrefixedTypeUrl));   // prefixsiz typeUrl
        assertTrue(json.contains("\"value\""));          // fallback branch'i
    }

    // ----------------------------------------------------------------------
    // Yardımcı: runtime'da "message AnyContainer { google.protobuf.Any any = 1; }"
    // descriptor'ını üretir.
    // ----------------------------------------------------------------------
    private static Descriptors.Descriptor buildAnyContainerDescriptor(
            Descriptors.Descriptor anyDesc
    ) throws Descriptors.DescriptorValidationException {

        FieldDescriptorProto anyFieldProto = FieldDescriptorProto.newBuilder()
                .setName("any")
                .setNumber(1)
                .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
                .setType(FieldDescriptorProto.Type.TYPE_MESSAGE)
                .setTypeName(anyDesc.getFullName()) // google.protobuf.Any
                .build();

        DescriptorProto msgProto = DescriptorProto.newBuilder()
                .setName("AnyContainer")
                .addField(anyFieldProto)
                .build();

        FileDescriptorProto fileProto = FileDescriptorProto.newBuilder()
                .setName("any_container_test.proto")
                .setPackage("test")
                .addDependency(anyDesc.getFile().getName())
                .addMessageType(msgProto)
                .build();

        Descriptors.FileDescriptor fileDesc =
                Descriptors.FileDescriptor.buildFrom(
                        fileProto,
                        new Descriptors.FileDescriptor[]{anyDesc.getFile()}
                );

        return fileDesc.findMessageTypeByName("AnyContainer");
    }

    // ----------------------------------------------------------------------
    // (İstersen) DESCRIPTOR_CACHE'deki catch branch'i için:
    // register(...) çağrısında getDescriptor metodu olmayan bir Message sınıfı
    // ----------------------------------------------------------------------

    @Test
    void register_throwsIllegalArgumentExceptionWhenDescriptorIsMissing() {
        AnyConverter converter = AnyConverter.create();

        assertThrows(IllegalArgumentException.class, () -> converter.register(BadMessage.class));
    }

    /**
     * Sadece register(...) içindeki reflection hatasını tetiklemek için
     * minimal bir Message implementasyonu. getDescriptor() static metodu yok.
     * Protobuf versiyonuna göre eksik methodlar kalırsa IDE uyarılarına göre
     * tamamlayabilirsin.
     */
    public static class BadMessage implements Message {

        @Override
        public Descriptors.Descriptor getDescriptorForType() {
            return Any.getDescriptor(); // önemli değil
        }

        @Override
        public Map<Descriptors.FieldDescriptor, Object> getAllFields() {
            return Map.of();
        }

        @Override
        public boolean hasOneof(Descriptors.OneofDescriptor oneof) {
            return false;
        }

        @Override
        public Descriptors.FieldDescriptor getOneofFieldDescriptor(Descriptors.OneofDescriptor oneof) {
            return null;
        }

        @Override
        public boolean hasField(Descriptors.FieldDescriptor field) {
            return false;
        }

        @Override
        public Object getField(Descriptors.FieldDescriptor field) {
            return null;
        }

        @Override
        public int getRepeatedFieldCount(Descriptors.FieldDescriptor field) {
            return 0;
        }

        @Override
        public Object getRepeatedField(Descriptors.FieldDescriptor field, int index) {
            return null;
        }

        @Override
        public UnknownFieldSet getUnknownFields() {
            return UnknownFieldSet.getDefaultInstance();
        }

        @Override
        public boolean isInitialized() {
            return true;
        }

        @Override
        public Builder newBuilderForType() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Builder toBuilder() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Message getDefaultInstanceForType() {
            return this;
        }

        @Override
        public List<String> findInitializationErrors() {
            return null;
        }

        @Override
        public String getInitializationErrorString() {
            return null;
        }

        @Override
        public Parser<? extends Message> getParserForType() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ByteString toByteString() {
            return null;
        }

        @Override
        public byte[] toByteArray() {
            return new byte[0];
        }

        @Override
        public void writeTo(OutputStream output) throws IOException {

        }

        @Override
        public void writeDelimitedTo(OutputStream output) throws IOException {

        }

        @Override
        public void writeTo(CodedOutputStream output) {
            // no-op
        }

        @Override
        public int getSerializedSize() {
            return 0;
        }
    }
}
