package org.apache.camel.component.protojson.benchmark;

import org.apache.camel.component.protojson.engine.ProtoJsonEngine;
import org.apache.camel.component.protojson.engine.ProtoJsonEngineConfig;
import org.apache.camel.component.protojson.test.proto.SimpleUser;
import org.openjdk.jmh.annotations.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * Benchmark for simple message parsing and printing.
 * Tests baseline performance with minimal complexity.
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 3)
@Fork(1)
@State(Scope.Benchmark)
public class SimpleMessageBenchmark {

    private ProtoJsonEngine engine;
    private byte[] simpleJson;
    private SimpleUser simpleUser;

    @Setup
    public void setup() {
        ProtoJsonEngineConfig config = ProtoJsonEngineConfig.newBuilder().build();
        engine = new ProtoJsonEngine(config);

        // Prepare test data
        simpleJson = """
            {
              "name": "John Doe",
              "age": 30,
              "email": "john.doe@example.com",
              "active": true
            }
            """.getBytes(StandardCharsets.UTF_8);

        simpleUser = SimpleUser.newBuilder()
                .setName("John Doe")
                .setAge(30)
                .setEmail("john.doe@example.com")
                .setActive(true)
                .build();
    }

    @Benchmark
    public SimpleUser parseSimpleMessage() throws Exception {
        return engine.parse(new ByteArrayInputStream(simpleJson), SimpleUser.class);
    }

    @Benchmark
    public byte[] printSimpleMessage() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream(256);
        engine.print(simpleUser, out);
        return out.toByteArray();
    }

    @Benchmark
    public SimpleUser parseAndPrint() throws Exception {
        SimpleUser parsed = engine.parse(new ByteArrayInputStream(simpleJson), SimpleUser.class);
        ByteArrayOutputStream out = new ByteArrayOutputStream(256);
        engine.print(parsed, out);
        return parsed;
    }
}
