package org.apache.camel.component.protojson.benchmark;

import com.google.protobuf.Timestamp;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.component.protojson.engine.ProtoJsonEngine;
import org.apache.camel.component.protojson.engine.ProtoJsonEngineConfig;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.component.protojson.ProtoJsonDataFormat;
import org.apache.camel.component.protojson.test.proto.*;
import org.openjdk.jmh.annotations.*;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3)
@Measurement(iterations = 5)
@Fork(1)
public class ProtoJsonDataFormatBenchmark {

    @State(Scope.Benchmark)
    public static class S {
        ProtoJsonEngine engine;
        ComplexMessage message; // marshal bench için
        String json;            // unmarshal bench için

        @Setup(Level.Trial)
        public void setup() throws Exception {

            ProtoJsonEngineConfig build = ProtoJsonEngineConfig.newBuilder().build();
            build.getMetaRegistry().register(ComplexMessage.class);

            engine = new ProtoJsonEngine(ProtoJsonEngineConfig.newBuilder().build());

            // Medium bir ComplexMessage
            message = createSampleMessage(5, 5);

            // DataFormat ile JSON üret, unmarshal bench’inde kullan
            json = engine.printToString(message);
        }


        private ComplexMessage createSampleMessage(int eventCount, int userMapSize) {
            Instant now = Instant.now();

            ComplexMessage.Builder builder = ComplexMessage.newBuilder()
                    .setId("dataformat-bench")
                    .setUser(SimpleUser.newBuilder()
                            .setName("Bench User")
                            .setAge(30)
                            .setActive(true)
                            .build())
                    .setUpdatedAt(Timestamp.newBuilder()
                            .setSeconds(now.getEpochSecond())
                            .build());

            for (int i = 0; i < eventCount; i++) {
                builder.addEvents(EventMessage.newBuilder()
                        .setEventId("evt-" + i)
                        .setCreatedAt(Timestamp.newBuilder()
                                .setSeconds(now.minusSeconds(i * 10L).getEpochSecond())
                                .build())
                        .build());
            }

            for (int i = 0; i < userMapSize; i++) {
                builder.putUserMap("user-" + i,
                        UserWithAddress.newBuilder()
                                .setName("User " + i)
                                .setAddress(Address.newBuilder()
                                        .setCity("City-" + i)
                                        .setStreet("Street-" + i)
                                        .setZipCode( i)
                                        .build())
                                .build());
            }

            return builder.build();
        }
    }

    /**
     * ProtoJsonDataFormat ile marshal (Proto -> JSON)
     */
    @Benchmark
    public String benchDataFormatMarshal(S s) throws Exception {

     return    s.engine.printToString(s.message);
    }

    /**
     * ProtoJsonDataFormat ile unmarshal (JSON -> Proto)
     */
    @Benchmark
    public ComplexMessage benchDataFormatUnmarshal(S s) throws Exception {
        return s.engine.parse(s.json.getBytes(), ComplexMessage.class);
    }
}
