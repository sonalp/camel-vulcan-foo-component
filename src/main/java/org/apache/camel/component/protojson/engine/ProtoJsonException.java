package org.apache.camel.component.protojson.engine;

import java.io.IOException;

public class ProtoJsonException extends IOException {

    public enum ErrorCode {
        INVALID_JSON,
        UNKNOWN_FIELD,
        INVALID_ENUM_VALUE,
        TYPE_MISMATCH,
        CUSTOM_CONVERTER_ERROR,
        /** Security: Maximum nesting depth exceeded */
        NESTING_TOO_DEEP,
        /** Security: Repeated field size limit exceeded */
        REPEATED_FIELD_TOO_LARGE
    }

    private final ErrorCode code;
    private final String path;

    public ProtoJsonException(ErrorCode code, String message, String path, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.path = path;
    }

    public ProtoJsonException(ErrorCode code, String message, String path) {
        this(code, message, path, null);
    }

    public ErrorCode getCode() {
        return code;
    }

    public String getPath() {
        return path;
    }
}
