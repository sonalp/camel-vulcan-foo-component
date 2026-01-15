package org.apache.camel.component.protojson.benchmark;

import org.apache.camel.component.protojson.engine.ProtoJsonEngine;
import org.apache.camel.component.protojson.engine.ProtoJsonEngineConfig;
import org.apache.camel.component.protojson.test.proto.Address;
import org.apache.camel.component.protojson.test.proto.UserStatus;
import org.apache.camel.component.protojson.test.proto.UserWithTags;
import org.openjdk.jmh.annotations.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * Benchmark for repeated field parsing and printing.
 * Tests performance with arrays of various sizes (ArrayList resizing overhead).
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 3)
@Fork(1)
@State(Scope.Benchmark)
public class RepeatedFieldBenchmark {

    private ProtoJsonEngine engine;

    @Param({"10", "100", "1000"})
    private int arraySize;

    private byte[] repeatedJson;
    private UserWithTags userWithTags;

    @Setup
    public void setup() {
        ProtoJsonEngineConfig config = ProtoJsonEngineConfig.newBuilder().build();
        engine = new ProtoJsonEngine(config);

        // Build JSON with repeated fields
        StringBuilder jsonBuilder = new StringBuilder();
        jsonBuilder.append("{\n");
        jsonBuilder.append("  \"name\": \"Test User\",\n");
        jsonBuilder.append("  \"tags\": [");
        for (int i = 0; i < arraySize; i++) {
            if (i > 0) jsonBuilder.append(", ");
            jsonBuilder.append("\"tag").append(i).append("\"");
        }
        jsonBuilder.append("],\n");
        jsonBuilder.append("  \"scores\": [");
        for (int i = 0; i < arraySize; i++) {
            if (i > 0) jsonBuilder.append(", ");
            jsonBuilder.append(i * 10);
        }
        jsonBuilder.append("],\n");
        jsonBuilder.append("  \"addresses\": [");
        for (int i = 0; i < Math.min(10, arraySize); i++) {
            if (i > 0) jsonBuilder.append(", ");
            jsonBuilder.append(String.format(
                "{\"street\": \"Street %d\", \"city\": \"City %d\", \"country\": \"Country\", \"zipCode\": %d}",
                i, i, 10000 + i
            ));
        }
        jsonBuilder.append("],\n");
        jsonBuilder.append("  \"statuses\": [");
        for (int i = 0; i < Math.min(5, arraySize); i++) {
            if (i > 0) jsonBuilder.append(", ");
            jsonBuilder.append("\"ACTIVE\"");
        }
        jsonBuilder.append("]\n");
        jsonBuilder.append("}");

        repeatedJson = jsonBuilder.toString().getBytes(StandardCharsets.UTF_8);

        // Build proto message
        UserWithTags.Builder builder = UserWithTags.newBuilder()
                .setName("Test User");

        for (int i = 0; i < arraySize; i++) {
            builder.addTags("tag" + i);
            builder.addScores(i * 10);
        }

        for (int i = 0; i < Math.min(10, arraySize); i++) {
            builder.addAddresses(Address.newBuilder()
                    .setStreet("Street " + i)
                    .setCity("City " + i)
                    .setCountry("Country")
                    .setZipCode(10000 + i)
                    .build());
        }

        for (int i = 0; i < Math.min(5, arraySize); i++) {
            builder.addStatuses(UserStatus.ACTIVE);
        }

        userWithTags = builder.build();
    }

    @Benchmark
    public UserWithTags parseRepeatedFields() throws Exception {
        return engine.parse(new ByteArrayInputStream(repeatedJson), UserWithTags.class);
    }

    @Benchmark
    public byte[] printRepeatedFields() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream(4096);
        engine.print(userWithTags, out);
        return out.toByteArray();
    }
}
