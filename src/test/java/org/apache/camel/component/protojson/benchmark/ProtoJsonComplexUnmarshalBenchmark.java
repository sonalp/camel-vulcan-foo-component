package org.apache.camel.component.protojson.benchmark;

import com.google.protobuf.Timestamp;
import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.protojson.ProtoJsonDataFormat;
import org.apache.camel.component.protojson.test.proto.*;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.Language;
import org.openjdk.jmh.annotations.*;

import java.time.Instant;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 2)
@Measurement(iterations = 5)
@Fork(1)
public class ProtoJsonComplexUnmarshalBenchmark {

    @State(Scope.Benchmark)
    public static class S {
        CamelContext context;
        ProducerTemplate producer;
        Random rnd;

        @Setup(Level.Trial)
        public void setup() throws Exception {
            context = new DefaultCamelContext();
            rnd = new Random();

            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    // ComplexMessage için data format
                    ProtoJsonDataFormat complexFormat =
                            new ProtoJsonDataFormat(ComplexMessage.class);



                    // Marshal & unmarshal route'ları,
                    // ComplexIntegrationTest ile aynı pattern
                    from("direct:marshal")
                            .marshal(complexFormat)
                            .convertBodyTo(String.class);

                    from("direct:unmarshal")
                            .unmarshal(complexFormat);
                }
            });

            context.start();
            producer = context.createProducerTemplate();
        }

        @TearDown(Level.Trial)
        public void tearDown() throws Exception {
            context.stop();
        }

        /**
         * Her çağrıda random ComplexMessage üret.
         */
        ComplexMessage randomComplexMessage() {
            Instant now = Instant.now();
            int eventCount = 1 + rnd.nextInt(5);
            int userCount = 1 + rnd.nextInt(3);

            ComplexMessage.Builder builder = ComplexMessage.newBuilder()
                    .setId("complex-" + rnd.nextInt(1_000_000))
                    .setUser(SimpleUser.newBuilder()
                            .setName("User-" + rnd.nextInt(1000))
                            .setAge(18 + rnd.nextInt(50))
                            .setActive(rnd.nextBoolean())
                            .build())
                    .setUpdatedAt(Timestamp.newBuilder()
                            .setSeconds(now.getEpochSecond())
                            .build());

            // random event list
            for (int i = 0; i < eventCount; i++) {
                builder.addEvents(EventMessage.newBuilder()
                        .setEventId("evt-" + rnd.nextInt(1_000_000))
                        .setCreatedAt(Timestamp.newBuilder()
                                .setSeconds(now.minusSeconds(rnd.nextInt(3600)).getEpochSecond())
                                .build())
                        .build());
            }

            // random userMap
            for (int i = 0; i < userCount; i++) {
                builder.putUserMap("user-" + i,
                        UserWithAddress.newBuilder()
                                .setName("User " + i)
                                .setAddress(Address.newBuilder()
                                        .setCity("City-" + rnd.nextInt(100))
                                        .setStreet("Street-" + rnd.nextInt(1000))
                                        .setZipCode(rnd.nextInt(99999))
                                        .build())
                                .build());
            }

            return builder.build();
        }

        /**
         * Random ComplexMessage'i marshal edip JSON string döndür.
         */
        String nextRandomJson() {
            ComplexMessage msg = randomComplexMessage();
            return producer.requestBody("direct:marshal", msg, String.class);
        }
    }

    /**
     * Her iterasyonda farklı JSON ile ComplexMessage unmarshal benchmark'ı.
     */
    @Benchmark
    public Object benchComplexUnmarshal(S s) {
        String json = s.nextRandomJson();

        // Route zaten ComplexMessage ile konfigüre olduğu için header'a gerek yok.
        // İstersen header'lı versiyonu da kullanabilirsin.
        return s.producer.requestBody("direct:unmarshal", json);
    }

    /**
     * Header'lı versiyon istersen:
     */
    @Benchmark
    public Object benchComplexUnmarshalWithHeader(S s) {
        String json = s.nextRandomJson();

        return s.producer.requestBodyAndHeader(
                "direct:unmarshal",
                json,
                "CamelProtoJsonClass",
                ComplexMessage.class.getName()
        );
    }
}
