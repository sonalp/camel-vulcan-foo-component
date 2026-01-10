// src/test/java/org/apache/camel/component/protojson/BytesFieldTest.java

package org.apache.camel.component.protojson;

import com.google.protobuf.ByteString;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.protojson.test.proto.FileMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;


/**
 * Tests for bytes field handling (Base64 encoding).
 */
@DisplayName("Bytes Field Tests")
class BytesFieldTest extends BaseProtoJsonTest {

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                ProtoJsonDataFormat protoJson = new ProtoJsonDataFormat(FileMessage.class);

                from("direct:marshal")
                        .marshal(protoJson)
                        .convertBodyTo(String.class);

                from("direct:unmarshal")
                        .unmarshal(protoJson);
            }
        };
    }

    @Nested
    @DisplayName("Marshal Bytes Tests")
    class MarshalBytesTests {

        @Test
        @DisplayName("Should marshal bytes as Base64")
        void shouldMarshalBytesAsBase64() throws Exception {
            // Given
            byte[] content = "Hello, World!".getBytes(StandardCharsets.UTF_8);
            String expectedBase64 = Base64.getEncoder().encodeToString(content);

            FileMessage file = FileMessage.newBuilder()
                    .setFilename("test.txt")
                    .setContent(ByteString.copyFrom(content))
                    .build();

            // When
            String json = marshalToJson(file);

            // Then
            assertJsonContains(json, "content", expectedBase64);
        }

        @Test
        @DisplayName("Should marshal binary content")
        void shouldMarshalBinaryContent() throws Exception {
            // Given
            byte[] binary = new byte[]{0x00, 0x01, 0x02, (byte) 0xFF, (byte) 0xFE};
            String expectedBase64 = Base64.getEncoder().encodeToString(binary);

            FileMessage file = FileMessage.newBuilder()
                    .setFilename("binary.dat")
                    .setContent(ByteString.copyFrom(binary))
                    .build();

            // When
            String json = marshalToJson(file);

            // Then
            assertJsonContains(json, "content", expectedBase64);
        }

        @Test
        @DisplayName("Should marshal repeated bytes")
        void shouldMarshalRepeatedBytes() throws Exception {
            // Given
            byte[] chunk1 = "chunk1".getBytes(StandardCharsets.UTF_8);
            byte[] chunk2 = "chunk2".getBytes(StandardCharsets.UTF_8);

            FileMessage file = FileMessage.newBuilder()
                    .setFilename("chunked.dat")
                    .addChunks(ByteString.copyFrom(chunk1))
                    .addChunks(ByteString.copyFrom(chunk2))
                    .build();

            // When
            String json = marshalToJson(file);

            // Then
            assertThat(json).contains("\"chunks\"");
            assertThat(json).contains(Base64.getEncoder().encodeToString(chunk1));
            assertThat(json).contains(Base64.getEncoder().encodeToString(chunk2));
        }
    }

    @Nested
    @DisplayName("Unmarshal Bytes Tests")
    class UnmarshalBytesTests {

        @Test
        @DisplayName("Should unmarshal Base64 to bytes")
        void shouldUnmarshalBase64ToBytes() {
            // Given
            String originalContent = "Decoded content!";
            String base64 = Base64.getEncoder().encodeToString(
                    originalContent.getBytes(StandardCharsets.UTF_8));

            String json = String.format("""
                    {
                        "filename": "decoded.txt",
                        "content": "%s"
                    }
                    """, base64);

            // When
            FileMessage file = unmarshalFromJson(json, FileMessage.class);

            // Then
            assertThat(file.getFilename()).isEqualTo("decoded.txt");
            assertThat(file.getContent().toStringUtf8()).isEqualTo(originalContent);
        }

        @Test
        @DisplayName("Should unmarshal binary Base64")
        void shouldUnmarshalBinaryBase64() {
            // Given
            byte[] binary = new byte[]{0x00, 0x01, 0x02, (byte) 0xFF, (byte) 0xFE};
            String base64 = Base64.getEncoder().encodeToString(binary);

            String json = String.format("""
                    {
                        "filename": "binary.dat",
                        "content": "%s"
                    }
                    """, base64);

            // When
            FileMessage file = unmarshalFromJson(json, FileMessage.class);

            // Then
            assertThat(file.getContent().toByteArray()).isEqualTo(binary);
        }

        @Test
        @DisplayName("Should unmarshal repeated bytes")
        void shouldUnmarshalRepeatedBytes() {
            // Given
            String chunk1Base64 = Base64.getEncoder().encodeToString("first".getBytes());
            String chunk2Base64 = Base64.getEncoder().encodeToString("second".getBytes());

            String json = String.format("""
                    {
                        "filename": "multi.dat",
                        "chunks": ["%s", "%s"]
                    }
                    """, chunk1Base64, chunk2Base64);

            // When
            FileMessage file = unmarshalFromJson(json, FileMessage.class);

            // Then
            assertThat(file.getChunksList()).hasSize(2);
            assertThat(file.getChunks(0).toStringUtf8()).isEqualTo("first");
            assertThat(file.getChunks(1).toStringUtf8()).isEqualTo("second");
        }

        @Test
        @DisplayName("Should handle empty bytes")
        void shouldHandleEmptyBytes() {
            // Given
            String json = """
                    {
                        "filename": "empty.dat",
                        "content": ""
                    }
                    """;

            // When
            FileMessage file = unmarshalFromJson(json, FileMessage.class);

            // Then
            assertThat(file.getContent().isEmpty()).isTrue();
        }
    }

    @Nested
    @DisplayName("Round-Trip Bytes Tests")
    class RoundTripBytesTests {

        @Test
        @DisplayName("Should round-trip bytes field")
        void shouldRoundTripBytesField() {
            // Given
            byte[] content = "Round trip content with special chars: äöü".getBytes(StandardCharsets.UTF_8);

            FileMessage original = FileMessage.newBuilder()
                    .setFilename("roundtrip.txt")
                    .setContent(ByteString.copyFrom(content))
                    .addChunks(ByteString.copyFrom("chunk1".getBytes()))
                    .addChunks(ByteString.copyFrom("chunk2".getBytes()))
                    .build();

            // When
            FileMessage result = roundTrip(original);

            // Then
            assertThat(result).isEqualTo(original);
        }

        @Test
        @DisplayName("Should round-trip large binary content")
        void shouldRoundTripLargeBinaryContent() {
            // Given - 1KB of random-ish binary data
            byte[] largeContent = new byte[1024];
            for (int i = 0; i < largeContent.length; i++) {
                largeContent[i] = (byte) (i % 256);
            }

            FileMessage original = FileMessage.newBuilder()
                    .setFilename("large.bin")
                    .setContent(ByteString.copyFrom(largeContent))
                    .build();

            // When
            FileMessage result = roundTrip(original);

            // Then
            assertThat(result.getContent().toByteArray()).isEqualTo(largeContent);
        }
    }
}