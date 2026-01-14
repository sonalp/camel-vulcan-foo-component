package org.apache.camel.component.protojson.benchmark;

import org.apache.camel.component.protojson.engine.ProtoJsonEngine;
import org.apache.camel.component.protojson.engine.ProtoJsonEngineConfig;
import org.apache.camel.component.protojson.test.proto.*;
import org.openjdk.jmh.annotations.*;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * Benchmark for cache effectiveness.
 * Tests cold start vs warm cache vs mixed message types (cache thrashing).
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 3)
@Fork(1)
public class CacheEffectivenessBenchmark {

    /**
     * Cold start - new engine for each invocation.
     * Tests performance without cache warming.
     */
    @State(Scope.Thread)
    public static class ColdStartState {
        private byte[] json;

        @Setup(Level.Trial)
        public void trialSetup() {
            json = """
                {
                  "name": "John Doe",
                  "age": 30,
                  "email": "john@example.com",
                  "active": true
                }
                """.getBytes(StandardCharsets.UTF_8);
        }

        @Setup(Level.Invocation)
        public void invocationSetup() {
            // New engine for each invocation - cold cache
        }

        public ProtoJsonEngine createEngine() {
            return new ProtoJsonEngine(ProtoJsonEngineConfig.newBuilder().build());
        }

        public byte[] getJson() {
            return json;
        }
    }

    /**
     * Warm cache - same engine for all invocations.
     * Tests performance with fully warmed caches.
     */
    @State(Scope.Benchmark)
    public static class WarmCacheState {
        private ProtoJsonEngine engine;
        private byte[] json;

        @Setup
        public void setup() {
            ProtoJsonEngineConfig config = ProtoJsonEngineConfig.newBuilder().build();
            engine = new ProtoJsonEngine(config);

            json = """
                {
                  "name": "John Doe",
                  "age": 30,
                  "email": "john@example.com",
                  "active": true
                }
                """.getBytes(StandardCharsets.UTF_8);

            // Warm up the cache
            try {
                for (int i = 0; i < 100; i++) {
                    engine.parse(new ByteArrayInputStream(json), SimpleUser.class);
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * Mixed message types - rotates between different message types.
     * Tests cache thrashing scenario.
     */
    @State(Scope.Benchmark)
    public static class MixedTypesState {
        private ProtoJsonEngine engine;
        private byte[][] jsons;
        private Class<?>[] types;
        private int counter;

        @Setup
        public void setup() {
            ProtoJsonEngineConfig config = ProtoJsonEngineConfig.newBuilder().build();
            engine = new ProtoJsonEngine(config);

            jsons = new byte[][] {
                """
                {
                  "name": "User1",
                  "age": 25,
                  "email": "user1@example.com",
                  "active": true
                }
                """.getBytes(StandardCharsets.UTF_8),

                """
                {
                  "name": "User2",
                  "status": "ACTIVE"
                }
                """.getBytes(StandardCharsets.UTF_8),

                """
                {
                  "name": "User3",
                  "address": {
                    "street": "123 Main St",
                    "city": "SF",
                    "country": "USA",
                    "zipCode": 94102
                  }
                }
                """.getBytes(StandardCharsets.UTF_8),

                """
                {
                  "name": "User4",
                  "tags": ["tag1", "tag2", "tag3"],
                  "scores": [10, 20, 30]
                }
                """.getBytes(StandardCharsets.UTF_8),

                """
                {
                  "name": "User5",
                  "stringMeta": {"key1": "value1", "key2": "value2"}
                }
                """.getBytes(StandardCharsets.UTF_8)
            };

            types = new Class<?>[] {
                SimpleUser.class,
                UserWithStatus.class,
                UserWithAddress.class,
                UserWithTags.class,
                UserWithMetadata.class
            };

            counter = 0;
        }

        public ProtoJsonEngine getEngine() {
            return engine;
        }

        public byte[] getNextJson() {
            int idx = counter++ % jsons.length;
            return jsons[idx];
        }

        public Class<?> getNextType() {
            return types[(counter - 1) % types.length];
        }
    }

    @Benchmark
    public SimpleUser coldStart(ColdStartState state) throws Exception {
        ProtoJsonEngine engine = state.createEngine();
        return engine.parse(new ByteArrayInputStream(state.getJson()), SimpleUser.class);
    }

    @Benchmark
    public SimpleUser warmCache(WarmCacheState state) throws Exception {
        return state.engine.parse(new ByteArrayInputStream(state.json), SimpleUser.class);
    }

    @Benchmark
    @SuppressWarnings("unchecked")
    public Object mixedTypes(MixedTypesState state) throws Exception {
        return state.getEngine().parse(
                new ByteArrayInputStream(state.getNextJson()),
                (Class) state.getNextType()
        );
    }

    /**
     * Tests field name lookup performance with different casing.
     * Case-sensitive hit vs case-insensitive fallback.
     */
    @State(Scope.Benchmark)
    public static class FieldNameLookupState {
        private ProtoJsonEngine engine;
        private byte[] correctCaseJson;
        private byte[] wrongCaseJson;

        @Setup
        public void setup() {
            ProtoJsonEngineConfig config = ProtoJsonEngineConfig.newBuilder().build();
            engine = new ProtoJsonEngine(config);

            // Correct JSON field names (should hit first lookup)
            correctCaseJson = """
                {
                  "name": "Test",
                  "age": 30,
                  "email": "test@example.com",
                  "active": true
                }
                """.getBytes(StandardCharsets.UTF_8);

            // Wrong case field names (should hit second lookup after lowercase)
            wrongCaseJson = """
                {
                  "NAME": "Test",
                  "AGE": 30,
                  "EMAIL": "test@example.com",
                  "ACTIVE": true
                }
                """.getBytes(StandardCharsets.UTF_8);
        }
    }

    @Benchmark
    public SimpleUser fieldNameCorrectCase(FieldNameLookupState state) throws Exception {
        return state.engine.parse(
                new ByteArrayInputStream(state.correctCaseJson),
                SimpleUser.class
        );
    }

    @Benchmark
    public SimpleUser fieldNameWrongCase(FieldNameLookupState state) throws Exception {
        return state.engine.parse(
                new ByteArrayInputStream(state.wrongCaseJson),
                SimpleUser.class
        );
    }
}
