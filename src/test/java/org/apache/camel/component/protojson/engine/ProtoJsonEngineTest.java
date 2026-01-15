package org.apache.camel.component.protojson.engine;

import org.apache.camel.component.protojson.config.ParserConfig;
import org.apache.camel.component.protojson.config.PrinterConfig;
import org.apache.camel.component.protojson.test.proto.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.*;

/**
 * Comprehensive tests for ProtoJsonEngine.
 * Tests parsing, printing, configurations, and error handling.
 */
class ProtoJsonEngineTest {

    private ProtoJsonEngine engine;

    @BeforeEach
    void setUp() {
        engine = new ProtoJsonEngine(ProtoJsonEngineConfig.newBuilder().build());
    }

    // ==================== Parse Tests ====================

    @Test
    void testParseSimpleMessage() throws Exception {
        // Given
        String json = """
                {
                  "name": "John",
                  "age": 30,
                  "email": "john@example.com",
                  "active": true
                }
                """;

        // When
        SimpleUser user = engine.parse(
                new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)),
                SimpleUser.class
        );

        // Then
        assertThat(user.getName()).isEqualTo("John");
        assertThat(user.getAge()).isEqualTo(30);
        assertThat(user.getEmail()).isEqualTo("john@example.com");
        assertThat(user.getActive()).isTrue();
    }

    @Test
    void testParseFromByteArray() throws Exception {
        // Given
        String json = """
                {
                  "name": "Alice",
                  "age": 25
                }
                """;
        byte[] jsonBytes = json.getBytes(StandardCharsets.UTF_8);

        // When
        SimpleUser user = engine.parse(jsonBytes, SimpleUser.class);

        // Then
        assertThat(user.getName()).isEqualTo("Alice");
        assertThat(user.getAge()).isEqualTo(25);
    }

    @Test
    void testParseNestedMessage() throws Exception {
        // Given
        String json = """
                {
                  "name": "John",
                  "address": {
                    "street": "123 Main St",
                    "city": "NYC",
                    "country": "USA",
                    "zipCode": 10001
                  }
                }
                """;

        // When
        UserWithAddress user = engine.parse(
                new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)),
                UserWithAddress.class
        );

        // Then
        assertThat(user.getName()).isEqualTo("John");
        assertThat(user.hasAddress()).isTrue();
        assertThat(user.getAddress().getStreet()).isEqualTo("123 Main St");
        assertThat(user.getAddress().getCity()).isEqualTo("NYC");
        assertThat(user.getAddress().getZipCode()).isEqualTo(10001);
    }

    @Test
    void testParseRepeatedFields() throws Exception {
        // Given
        String json = """
                {
                  "name": "John",
                  "tags": ["tag1", "tag2", "tag3"],
                  "scores": [10, 20, 30]
                }
                """;

        // When
        UserWithTags user = engine.parse(
                new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)),
                UserWithTags.class
        );

        // Then
        assertThat(user.getTagsList()).containsExactly("tag1", "tag2", "tag3");
        assertThat(user.getScoresList()).containsExactly(10, 20, 30);
    }

    @Test
    void testParseMapFields() throws Exception {
        // Given
        String json = """
                {
                  "name": "John",
                  "stringMeta": {
                    "key1": "value1",
                    "key2": "value2"
                  },
                  "intMeta": {
                    "count": 42,
                    "total": 100
                  }
                }
                """;

        // When
        UserWithMetadata user = engine.parse(
                new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)),
                UserWithMetadata.class
        );

        // Then
        assertThat(user.getStringMetaMap())
                .containsEntry("key1", "value1")
                .containsEntry("key2", "value2");
        assertThat(user.getIntMetaMap())
                .containsEntry("count", 42)
                .containsEntry("total", 100);
    }

    @Test
    void testParseEnumField() throws Exception {
        // Given
        String json = """
                {
                  "name": "John",
                  "status": "ACTIVE"
                }
                """;

        // When
        UserWithStatus user = engine.parse(
                new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)),
                UserWithStatus.class
        );

        // Then
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void testParseEmptyMessage() throws Exception {
        // Given
        String json = "{}";

        // When
        SimpleUser user = engine.parse(
                new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)),
                SimpleUser.class
        );

        // Then
        assertThat(user.getName()).isEmpty();
        assertThat(user.getAge()).isZero();
        assertThat(user.getActive()).isFalse();
    }

    @Test
    void testParseWithUnknownFields() throws Exception {
        // Given
        ParserConfig config = ParserConfig.newBuilder()
                .ignoringUnknownFields(true)
                .build();
        ProtoJsonEngine customEngine = new ProtoJsonEngine(
                ProtoJsonEngineConfig.newBuilder()
                        .parserConfig(config)
                        .build()
        );

        String json = """
                {
                  "name": "John",
                  "age": 30,
                  "unknownField": "should be ignored"
                }
                """;

        // When
        SimpleUser user = customEngine.parse(
                new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)),
                SimpleUser.class
        );

        // Then
        assertThat(user.getName()).isEqualTo("John");
        assertThat(user.getAge()).isEqualTo(30);
    }

    @Test
    void testParseInvalidJson() {
        // Given
        String invalidJson = "{not valid json}";

        // When/Then
        assertThatThrownBy(() -> engine.parse(
                new ByteArrayInputStream(invalidJson.getBytes(StandardCharsets.UTF_8)),
                SimpleUser.class
        )).isInstanceOf(Exception.class);
    }

    @Test
    void testParseTypeMismatch() {
        // Given
        String json = """
                {
                  "name": "John",
                  "age": "not a number"
                }
                """;

        // When/Then
        assertThatThrownBy(() -> engine.parse(
                new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)),
                SimpleUser.class
        )).isInstanceOf(Exception.class);
    }

    // ==================== Print Tests ====================

    @Test
    void testPrintSimpleMessage() throws Exception {
        // Given
        SimpleUser user = SimpleUser.newBuilder()
                .setName("John")
                .setAge(30)
                .setEmail("john@example.com")
                .setActive(true)
                .build();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // When
        engine.print(user, baos);

        // Then
        String json = baos.toString(StandardCharsets.UTF_8);
        assertThat(json).contains("\"name\"");
        assertThat(json).contains("\"John\"");
        assertThat(json).contains("\"age\"");
        assertThat(json).contains("30");
        assertThat(json).contains("\"active\"");
        assertThat(json).contains("true");
    }

    @Test
    void testPrintToString() throws Exception {
        // Given
        SimpleUser user = SimpleUser.newBuilder()
                .setName("Alice")
                .setAge(25)
                .build();

        // When
        String json = engine.printToString(user);

        // Then
        assertThat(json).contains("\"name\"");
        assertThat(json).contains("\"Alice\"");
        assertThat(json).contains("\"age\"");
        assertThat(json).contains("25");
    }

    @Test
    void testPrintNestedMessage() throws Exception {
        // Given
        UserWithAddress user = UserWithAddress.newBuilder()
                .setName("John")
                .setAddress(Address.newBuilder()
                        .setStreet("123 Main St")
                        .setCity("NYC")
                        .build())
                .build();

        // When
        String json = engine.printToString(user);

        // Then
        assertThat(json).contains("\"name\"");
        assertThat(json).contains("\"John\"");
        assertThat(json).contains("\"address\"");
        assertThat(json).contains("\"street\"");
        assertThat(json).contains("\"123 Main St\"");
    }

    @Test
    void testPrintRepeatedFields() throws Exception {
        // Given
        UserWithTags user = UserWithTags.newBuilder()
                .setName("John")
                .addTags("tag1")
                .addTags("tag2")
                .addScores(10)
                .addScores(20)
                .build();

        // When
        String json = engine.printToString(user);

        // Then
        assertThat(json).contains("\"tags\"");
        assertThat(json).contains("[\"tag1\",\"tag2\"]");
        assertThat(json).contains("\"scores\"");
        assertThat(json).contains("[10,20]");
    }

    @Test
    void testPrintMapFields() throws Exception {
        // Given
        UserWithMetadata user = UserWithMetadata.newBuilder()
                .setName("John")
                .putStringMeta("key1", "value1")
                .putStringMeta("key2", "value2")
                .build();

        // When
        String json = engine.printToString(user);

        // Then
        assertThat(json).contains("\"stringMeta\"");
        assertThat(json).contains("\"key1\"");
        assertThat(json).contains("\"value1\"");
        assertThat(json).contains("\"key2\"");
        assertThat(json).contains("\"value2\"");
    }

    @Test
    void testPrintEnumAsString() throws Exception {
        // Given
        PrinterConfig config = PrinterConfig.newBuilder()
                .printingEnumsAsInts(false)
                .build();
        ProtoJsonEngine customEngine = new ProtoJsonEngine(
                ProtoJsonEngineConfig.newBuilder()
                        .printerConfig(config)
                        .build()
        );

        UserWithStatus user = UserWithStatus.newBuilder()
                .setName("John")
                .setStatus(UserStatus.ACTIVE)
                .build();

        // When
        String json = customEngine.printToString(user);

        // Then
        assertThat(json).contains("\"status\"");
        assertThat(json).contains("\"ACTIVE\"");
    }

    @Test
    void testPrintEnumAsInt() throws Exception {
        // Given
        PrinterConfig config = PrinterConfig.newBuilder()
                .printingEnumsAsInts(true)
                .build();
        ProtoJsonEngine customEngine = new ProtoJsonEngine(
                ProtoJsonEngineConfig.newBuilder()
                        .printerConfig(config)
                        .build()
        );

        UserWithStatus user = UserWithStatus.newBuilder()
                .setName("John")
                .setStatus(UserStatus.ACTIVE)
                .build();

        // When
        String json = customEngine.printToString(user);

        // Then
        assertThat(json).contains("\"status\"");
        assertThat(json).contains(":1"); // ACTIVE = 1
    }

    @Test
    void testPrintWithDefaultValues() throws Exception {
        // Given
        PrinterConfig config = PrinterConfig.newBuilder()
                .includingDefaultValueFields(true)
                .build();
        ProtoJsonEngine customEngine = new ProtoJsonEngine(
                ProtoJsonEngineConfig.newBuilder()
                        .printerConfig(config)
                        .build()
        );

        SimpleUser user = SimpleUser.newBuilder().build(); // All defaults

        // When
        String json = customEngine.printToString(user);

        // Then
        assertThat(json).contains("\"name\"");
        assertThat(json).contains("\"\"");
        assertThat(json).contains("\"age\"");
        assertThat(json).contains("0");
        assertThat(json).contains("\"active\"");
        assertThat(json).contains("false");
    }

    @Test
    void testPrintWithoutDefaultValues() throws Exception {
        // Given
        PrinterConfig config = PrinterConfig.newBuilder()
                .includingDefaultValueFields(false)
                .build();
        ProtoJsonEngine customEngine = new ProtoJsonEngine(
                ProtoJsonEngineConfig.newBuilder()
                        .printerConfig(config)
                        .build()
        );

        SimpleUser user = SimpleUser.newBuilder().build(); // All defaults

        // When
        String json = customEngine.printToString(user);

        // Then
        assertThat(json).isEqualTo("{}");
    }

    @Test
    void testPrintWithProtoFieldNames() throws Exception {
        // Given
        PrinterConfig config = PrinterConfig.newBuilder()
                .preservingProtoFieldNames(true)
                .build();
        ProtoJsonEngine customEngine = new ProtoJsonEngine(
                ProtoJsonEngineConfig.newBuilder()
                        .printerConfig(config)
                        .build()
        );

        SimpleUser user = SimpleUser.newBuilder()
                .setName("John")
                .setAge(30)
                .build();

        // When
        String json = customEngine.printToString(user);

        // Then
        assertThat(json).contains("\"name\""); // Proto field name
        assertThat(json).contains("\"age\"");
    }

    // ==================== Round-trip Tests ====================

    @Test
    void testRoundTripSimpleMessage() throws Exception {
        // Given
        SimpleUser original = SimpleUser.newBuilder()
                .setName("John")
                .setAge(30)
                .setEmail("john@example.com")
                .setActive(true)
                .build();

        // When
        String json = engine.printToString(original);
        SimpleUser result = engine.parse(
                new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)),
                SimpleUser.class
        );

        // Then
        assertThat(result).isEqualTo(original);
    }

    @Test
    void testRoundTripNestedMessage() throws Exception {
        // Given
        UserWithAddress original = UserWithAddress.newBuilder()
                .setName("John")
                .setAddress(Address.newBuilder()
                        .setStreet("123 Main St")
                        .setCity("NYC")
                        .setCountry("USA")
                        .setZipCode(10001)
                        .build())
                .build();

        // When
        String json = engine.printToString(original);
        UserWithAddress result = engine.parse(
                new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)),
                UserWithAddress.class
        );

        // Then
        assertThat(result).isEqualTo(original);
    }

    @Test
    void testRoundTripComplexMessage() throws Exception {
        // Given
        UserWithMetadata original = UserWithMetadata.newBuilder()
                .setName("John")
                .putStringMeta("key1", "value1")
                .putStringMeta("key2", "value2")
                .putIntMeta("count", 42)
                .putIntMeta("total", 100)
                .build();

        // When
        String json = engine.printToString(original);
        UserWithMetadata result = engine.parse(
                new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)),
                UserWithMetadata.class
        );

        // Then
        assertThat(result).isEqualTo(original);
    }

    // ==================== Performance Tests ====================

    @Test
    void testParseLargeMessage() throws Exception {
        // Given
        StringBuilder jsonBuilder = new StringBuilder("{\"name\":\"John\",\"tags\":[");
        for (int i = 0; i < 10000; i++) {
            if (i > 0) jsonBuilder.append(",");
            jsonBuilder.append("\"tag").append(i).append("\"");
        }
        jsonBuilder.append("]}");
        String json = jsonBuilder.toString();

        // When
        long startTime = System.nanoTime();
        UserWithTags user = engine.parse(
                new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)),
                UserWithTags.class
        );
        long duration = System.nanoTime() - startTime;

        // Then
        assertThat(user.getTagsCount()).isEqualTo(10000);
        assertThat(duration).isLessThan(1_000_000_000); // < 1 second
    }

    @Test
    void testPrintLargeMessage() throws Exception {
        // Given
        UserWithTags.Builder builder = UserWithTags.newBuilder()
                .setName("John");
        for (int i = 0; i < 10000; i++) {
            builder.addTags("tag" + i);
        }
        UserWithTags user = builder.build();

        // When
        long startTime = System.nanoTime();
        String json = engine.printToString(user);
        long duration = System.nanoTime() - startTime;

        // Then
        assertThat(json).isNotEmpty();
        assertThat(duration).isLessThan(1_000_000_000); // < 1 second
    }

    // ==================== Error Handling ====================

    @Test
    void testParseNullInputStream() {
        // When/Then
        assertThatThrownBy(() -> engine.parse((InputStream) null, SimpleUser.class))
                .isInstanceOf(Exception.class);
    }

    @Test
    void testParseNullByteArray() {
        // When/Then
        assertThatThrownBy(() -> engine.parse((byte[]) null, SimpleUser.class))
                .isInstanceOf(Exception.class);
    }

    @Test
    void testPrintNullMessage() {
        // Given
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // When/Then
        assertThatThrownBy(() -> engine.print(null, baos))
                .isInstanceOf(Exception.class);
    }

    @Test
    void testPrintNullOutputStream() throws Exception {
        // Given
        SimpleUser user = SimpleUser.newBuilder()
                .setName("John")
                .build();

        // When/Then
        assertThatThrownBy(() -> engine.print(user, null))
                .isInstanceOf(Exception.class);
    }

    @Test
    void testPrintToStringNullMessage() {
        // When/Then
        assertThatThrownBy(() -> engine.printToString(null))
                .isInstanceOf(Exception.class);
    }

    // ==================== Configuration Tests ====================

    @Test
    void testEngineWithCustomConfig() {
        // Given
        ParserConfig parserConfig = ParserConfig.newBuilder()
                .ignoringUnknownFields(true)
                .acceptNumericEnums(true)
                .build();

        PrinterConfig printerConfig = PrinterConfig.newBuilder()
                .includingDefaultValueFields(true)
                .printingEnumsAsInts(true)
                .build();

        ProtoJsonEngineConfig config = ProtoJsonEngineConfig.newBuilder()
                .parserConfig(parserConfig)
                .printerConfig(printerConfig)
                .build();

        // When
        ProtoJsonEngine customEngine = new ProtoJsonEngine(config);

        // Then
        assertThat(customEngine).isNotNull();
    }
}
