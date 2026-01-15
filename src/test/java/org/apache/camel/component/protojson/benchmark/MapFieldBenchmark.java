package org.apache.camel.component.protojson.benchmark;

import org.apache.camel.component.protojson.engine.ProtoJsonEngine;
import org.apache.camel.component.protojson.engine.ProtoJsonEngineConfig;
import org.apache.camel.component.protojson.test.proto.Address;
import org.apache.camel.component.protojson.test.proto.UserWithMetadata;
import org.openjdk.jmh.annotations.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * Benchmark for map field parsing and printing.
 * Tests performance with maps (String allocation overhead for keys).
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 5, time = 5)
@Measurement(iterations = 5, time = 3)
@Fork(3)
@State(Scope.Benchmark)
public class MapFieldBenchmark {

    private ProtoJsonEngine engine;

    @Param({"10", "100", "1000"})
    private int mapSize;

    private byte[] mapJson;
    private UserWithMetadata userWithMetadata;

    @Setup
    public void setup() {
        ProtoJsonEngineConfig config = ProtoJsonEngineConfig.newBuilder().build();
        engine = new ProtoJsonEngine(config);

        // Build JSON with map fields
        StringBuilder jsonBuilder = new StringBuilder();
        jsonBuilder.append("{\n");
        jsonBuilder.append("  \"name\": \"Test User\",\n");
        jsonBuilder.append("  \"stringMeta\": {");
        for (int i = 0; i < mapSize; i++) {
            if (i > 0) jsonBuilder.append(", ");
            jsonBuilder.append("\"key").append(i).append("\": \"value").append(i).append("\"");
        }
        jsonBuilder.append("},\n");
        jsonBuilder.append("  \"intMeta\": {");
        for (int i = 0; i < mapSize; i++) {
            if (i > 0) jsonBuilder.append(", ");
            jsonBuilder.append("\"metric").append(i).append("\": ").append(i * 100);
        }
        jsonBuilder.append("},\n");
        jsonBuilder.append("  \"intKeyMeta\": {");
        for (int i = 0; i < mapSize; i++) {
            if (i > 0) jsonBuilder.append(", ");
            jsonBuilder.append("\"").append(i).append("\": \"value").append(i).append("\"");
        }
        jsonBuilder.append("},\n");
        jsonBuilder.append("  \"addressMap\": {");
        for (int i = 0; i < Math.min(10, mapSize); i++) {
            if (i > 0) jsonBuilder.append(", ");
            jsonBuilder.append("\"loc").append(i).append("\": ")
                    .append(String.format(
                            "{\"street\": \"Street %d\", \"city\": \"City %d\", \"country\": \"Country\", \"zipCode\": %d}",
                            i, i, 10000 + i
                    ));
        }
        jsonBuilder.append("}\n");
        jsonBuilder.append("}");

        mapJson = jsonBuilder.toString().getBytes(StandardCharsets.UTF_8);

        // Build proto message
        UserWithMetadata.Builder builder = UserWithMetadata.newBuilder()
                .setName("Test User");

        for (int i = 0; i < mapSize; i++) {
            builder.putStringMeta("key" + i, "value" + i);
            builder.putIntMeta("metric" + i, i * 100);
            builder.putIntKeyMeta(i, "value" + i);
        }

        for (int i = 0; i < Math.min(10, mapSize); i++) {
            builder.putAddressMap("loc" + i, Address.newBuilder()
                    .setStreet("Street " + i)
                    .setCity("City " + i)
                    .setCountry("Country")
                    .setZipCode(10000 + i)
                    .build());
        }

        userWithMetadata = builder.build();
    }

    @Benchmark
    public UserWithMetadata parseMapFields() throws Exception {
        return engine.parse(new ByteArrayInputStream(mapJson), UserWithMetadata.class);
    }

    @Benchmark
    public byte[] printMapFields() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream(8192);
        engine.print(userWithMetadata, out);
        return out.toByteArray();
    }

    /**
     * Specific test for integer key conversion overhead.
     * This tests String.valueOf() allocation for int keys.
     */
    @Benchmark
    public UserWithMetadata parseIntKeyMap() throws Exception {
        String json = """
            {
              "name": "Test",
              "intKeyMeta": {
                "1": "a", "2": "b", "3": "c", "4": "d", "5": "e",
                "10": "f", "20": "g", "100": "h", "999": "i"
              }
            }
            """;
        return engine.parse(
                new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)),
                UserWithMetadata.class
        );
    }
}
