package org.apache.camel.component.protojson.benchmark;

import com.google.protobuf.Timestamp;
import com.google.protobuf.util.JsonFormat;
import org.apache.camel.component.protojson.config.ParserConfig;
import org.apache.camel.component.protojson.config.PrinterConfig;
import org.apache.camel.component.protojson.converter.wellknown.WellKnownConverters;
import org.apache.camel.component.protojson.engine.ProtoJsonEngine;
import org.apache.camel.component.protojson.engine.ProtoJsonEngineConfig;
import org.apache.camel.component.protojson.test.proto.Address;
import org.apache.camel.component.protojson.test.proto.ComplexMessage;
import org.apache.camel.component.protojson.test.proto.EventMessage;
import org.apache.camel.component.protojson.test.proto.SimpleUser;
import org.apache.camel.component.protojson.test.proto.UserWithAddress;
import org.openjdk.jmh.annotations.*;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3)
@Measurement(iterations = 5)
@Fork(1)
public class JsonFormatVsEngineUnmarshalBenchmark {

    @State(Scope.Benchmark)
    public static class S {
        // JsonFormat tarafı
        JsonFormat.Printer jsonFormatPrinter;
        JsonFormat.Parser jsonFormatParser;

        // Engine tarafı
        ProtoJsonEngine engine;

        // Test verisi
        ComplexMessage message;
        String jsonFromJsonFormat;
        String jsonFromEngine;

        @Setup(Level.Trial)
        public void setup() throws Exception {
            // JsonFormat printer/parser
            jsonFormatPrinter = JsonFormat.printer();
            jsonFormatParser = JsonFormat.parser();

            // Engine config + registry

            ParserConfig parserConfig = ParserConfig.newBuilder().addAllInConverters(
                    WellKnownConverters.allInConverters()).build();
            PrinterConfig printerConfig = PrinterConfig.newBuilder().addAllOutConverters(
                    WellKnownConverters.allOutConverters()).build();


            ProtoJsonEngineConfig.Builder cfgBuilder = ProtoJsonEngineConfig.newBuilder()
                    .parserConfig(parserConfig)
                    .printerConfig(printerConfig);

            ProtoJsonEngineConfig cfg = cfgBuilder.build();

            cfg.getMetaRegistry().register(ComplexMessage.class);

            engine = new ProtoJsonEngine(cfg);

            // Ortak ComplexMessage (medium complexity: 5 event, 5 userMap)
            message = createSampleMessage(5, 5);

            // İki farklı JSON üret:
            // 1) JsonFormat'ın ürettiği JSON
            jsonFromJsonFormat = jsonFormatPrinter.print(message);

            // 2) Engine'in ürettiği JSON
            jsonFromEngine = engine.printToString(message);
        }

        private ComplexMessage createSampleMessage(int eventCount, int userMapSize) {
            Instant now = Instant.now();

            ComplexMessage.Builder builder = ComplexMessage.newBuilder()
                    .setId("bench-complex")
                    .setUser(SimpleUser.newBuilder()
                            .setName("Bench User")
                            .setAge(30)
                            .setActive(true)
                            .build())
                    .setUpdatedAt(Timestamp.newBuilder()
                            .setSeconds(now.getEpochSecond())
                            .build());

            // events
            for (int i = 0; i < eventCount; i++) {
                builder.addEvents(EventMessage.newBuilder()
                        .setEventId("evt-" + i)
                        .setCreatedAt(Timestamp.newBuilder()
                                .setSeconds(now.minusSeconds(i * 10L).getEpochSecond())
                                .build())
                        .build());
            }

            // userMap
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

    // 1) JsonFormat JSON → JsonFormat parse
    @Benchmark
    public ComplexMessage jsonFormatJson_to_jsonFormatParse(S s) throws Exception {
        ComplexMessage.Builder builder = ComplexMessage.newBuilder();
        s.jsonFormatParser.merge(s.jsonFromJsonFormat, builder);
        return builder.build();
    }

    // 2) Engine JSON → Engine parse
    @Benchmark
    public ComplexMessage engineJson_to_engineParse(S s) throws Exception {
        return s.engine.parse(s.jsonFromEngine.getBytes(), ComplexMessage.class);
    }

    // 3) Engine JSON → JsonFormat parse (cross)
    @Benchmark
    public ComplexMessage engineJson_to_jsonFormatParse(S s) throws Exception {
        ComplexMessage.Builder builder = ComplexMessage.newBuilder();
        s.jsonFormatParser.merge(s.jsonFromEngine, builder);
        return builder.build();
    }

    // 4) JsonFormat JSON → Engine parse (cross)
    @Benchmark
    public ComplexMessage jsonFormatJson_to_engineParse(S s) throws Exception {
        return s.engine.parse(s.jsonFromJsonFormat.getBytes(), ComplexMessage.class);
    }
}
