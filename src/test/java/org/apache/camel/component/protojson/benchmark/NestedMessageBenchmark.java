package org.apache.camel.component.protojson.benchmark;

import org.apache.camel.component.protojson.engine.ProtoJsonEngine;
import org.apache.camel.component.protojson.engine.ProtoJsonEngineConfig;
import org.apache.camel.component.protojson.test.proto.Address;
import org.apache.camel.component.protojson.test.proto.UserWithAddress;
import org.openjdk.jmh.annotations.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * Benchmark for nested message parsing and printing.
 * Tests performance with message nesting (builder allocation overhead).
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 3)
@Fork(1)
@State(Scope.Benchmark)
public class NestedMessageBenchmark {

    private ProtoJsonEngine engine;
    private byte[] nestedJson;
    private UserWithAddress userWithAddress;

    @Setup
    public void setup() {
        ProtoJsonEngineConfig config = ProtoJsonEngineConfig.builder().build();
        engine = new ProtoJsonEngine(config);

        // Prepare test data
        nestedJson = """
            {
              "name": "Alice Johnson",
              "address": {
                "street": "123 Main Street",
                "city": "San Francisco",
                "country": "USA",
                "zipCode": 94102
              }
            }
            """.getBytes(StandardCharsets.UTF_8);

        userWithAddress = UserWithAddress.newBuilder()
                .setName("Alice Johnson")
                .setAddress(Address.newBuilder()
                        .setStreet("123 Main Street")
                        .setCity("San Francisco")
                        .setCountry("USA")
                        .setZipCode(94102)
                        .build())
                .build();
    }

    @Benchmark
    public UserWithAddress parseNestedMessage() throws Exception {
        return engine.parse(new ByteArrayInputStream(nestedJson), UserWithAddress.class);
    }

    @Benchmark
    public byte[] printNestedMessage() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream(512);
        engine.print(userWithAddress, out);
        return out.toByteArray();
    }

    @Benchmark
    public UserWithAddress parseAndPrint() throws Exception {
        UserWithAddress parsed = engine.parse(new ByteArrayInputStream(nestedJson), UserWithAddress.class);
        ByteArrayOutputStream out = new ByteArrayOutputStream(512);
        engine.print(parsed, out);
        return parsed;
    }
}
