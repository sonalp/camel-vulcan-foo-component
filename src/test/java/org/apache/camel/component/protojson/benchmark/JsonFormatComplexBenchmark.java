package org.apache.camel.component.protojson.benchmark;

import com.google.protobuf.util.JsonFormat;
import org.apache.camel.component.protojson.test.proto.ComplexMessage;
import org.apache.camel.component.protojson.test.proto.SimpleUser;
import org.apache.camel.component.protojson.test.proto.EventMessage;
import org.apache.camel.component.protojson.test.proto.UserWithAddress;
import org.apache.camel.component.protojson.test.proto.Address;
import com.google.protobuf.Timestamp;
import org.openjdk.jmh.annotations.*;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3)
@Measurement(iterations = 5)
@Fork(1)
public class JsonFormatComplexBenchmark {

    @State(Scope.Benchmark)
    public static class S {
        JsonFormat.Printer printer;
        JsonFormat.Parser parser;

        ComplexMessage message; // marshal için
        String json;            // unmarshal için

        @Setup(Level.Trial)
        public void setup() throws Exception {
            printer = JsonFormat.printer();
            parser = JsonFormat.parser();

            // Medium seviyede bir ComplexMessage üret
            message = createSampleMessage(5, 5); // 5 event, 5 userMap

            // Marshal edip JSON’u cache’le (unmarshal bench’inde kullanacağız)
            json = printer.print(message);
        }

        private ComplexMessage createSampleMessage(int eventCount, int userMapSize) {
            Instant now = Instant.now();

            ComplexMessage.Builder builder = ComplexMessage.newBuilder()
                    .setId("jsonformat-bench")
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
                                        .setZipCode(i)
                                        .build())
                                .build());
            }

            return builder.build();
        }
    }

    /**
     * Pure JsonFormat ile ComplexMessage -> JSON
     */
    @Benchmark
    public String benchJsonFormatMarshal(S s) throws Exception {
        // 1 op = 1 marshal
        return s.printer.print(s.message);
    }

    /**
     * Pure JsonFormat ile JSON -> ComplexMessage
     */
    @Benchmark
    public ComplexMessage benchJsonFormatUnmarshal(S s) throws Exception {
        // JsonFormat.parser().merge her seferinde yeni builder ister
        ComplexMessage.Builder builder = ComplexMessage.newBuilder();
        s.parser.merge(s.json, builder);
        return builder.build();
    }
}
