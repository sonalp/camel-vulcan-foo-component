package org.apache.camel.component.protojson.engine;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for ProtoJsonException.
 */
class ProtoJsonExceptionTest {

    @Test
    void testExceptionWithMessageAndCode() {
        // Given
        String message = "Test error";
        ProtoJsonException.ErrorCode code = ProtoJsonException.ErrorCode.INVALID_JSON;

        // When
        ProtoJsonException exception = new ProtoJsonException(code, message, "test.field");

        // Then
        assertThat(exception.getMessage()).contains(message);
        assertThat(exception.getErrorCode()).isEqualTo(code);
        assertThat(exception.getFieldPath()).isEqualTo("test.field");
    }

    @Test
    void testExceptionWithCause() {
        // Given
        String message = "Test error";
        Throwable cause = new RuntimeException("Original cause");

        // When
        ProtoJsonException exception = new ProtoJsonException(
                ProtoJsonException.ErrorCode.TYPE_MISMATCH,
                message,
                "test.field",
                cause
        );

        // Then
        assertThat(exception.getMessage()).contains(message);
        assertThat(exception.getCause()).isEqualTo(cause);
    }

    @Test
    void testErrorCodeValues() {
        // Ensure all error codes are accessible
        assertThat(ProtoJsonException.ErrorCode.INVALID_JSON).isNotNull();
        assertThat(ProtoJsonException.ErrorCode.UNKNOWN_FIELD).isNotNull();
        assertThat(ProtoJsonException.ErrorCode.TYPE_MISMATCH).isNotNull();
        assertThat(ProtoJsonException.ErrorCode.INVALID_ENUM_VALUE).isNotNull();
        assertThat(ProtoJsonException.ErrorCode.CUSTOM_CONVERTER_ERROR).isNotNull();
    }

    @Test
    void testFieldPathInMessage() {
        // Given
        ProtoJsonException exception = new ProtoJsonException(
                ProtoJsonException.ErrorCode.TYPE_MISMATCH,
                "Type error",
                "user.address.street"
        );

        // Then
        assertThat(exception.getFieldPath()).isEqualTo("user.address.street");
    }

    @Test
    void testExceptionToString() {
        // Given
        ProtoJsonException exception = new ProtoJsonException(
                ProtoJsonException.ErrorCode.INVALID_JSON,
                "Invalid JSON syntax",
                "root"
        );

        // When
        String toString = exception.toString();

        // Then
        assertThat(toString).contains("ProtoJsonException");
        assertThat(toString).contains("INVALID_JSON");
    }
}
