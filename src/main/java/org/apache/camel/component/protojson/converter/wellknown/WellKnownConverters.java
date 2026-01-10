package org.apache.camel.component.protojson.converter.wellknown;

import org.apache.camel.component.protojson.converter.JsonInFieldConverter;
import org.apache.camel.component.protojson.converter.JsonOutFieldConverter;

import java.util.List;

/**
 * Factory for well-known type converters.
 *
 * Supported types:
 * - google.protobuf.Timestamp  -> RFC 3339 string
 * - google.protobuf.Duration   -> string with 's' suffix
 * - google.protobuf.Struct     -> JSON object
 * - google.protobuf.Value      -> any JSON value
 * - google.protobuf.ListValue  -> JSON array
 * - google.protobuf.*Value     -> primitive JSON values (wrappers)
 *
 * Note: google.protobuf.Any requires explicit registration via AnyConverter bean.
 */
public final class WellKnownConverters {

    private WellKnownConverters() {}

    private static final TimestampConverter TIMESTAMP = new TimestampConverter();
    private static final DurationConverter DURATION = new DurationConverter();
    private static final StructConverter STRUCT = new StructConverter();
    private static final WrapperConverters WRAPPERS = new WrapperConverters();

    public static List<JsonInFieldConverter> allInConverters() {
        return List.of(TIMESTAMP, DURATION, STRUCT, WRAPPERS);
    }

    public static List<JsonOutFieldConverter> allOutConverters() {
        return List.of(TIMESTAMP, DURATION, STRUCT, WRAPPERS);
    }

    public static TimestampConverter timestamp() {
        return TIMESTAMP;
    }

    public static DurationConverter duration() {
        return DURATION;
    }

    public static StructConverter struct() {
        return STRUCT;
    }

    public static WrapperConverters wrappers() {
        return WRAPPERS;
    }
}