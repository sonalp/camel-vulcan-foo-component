package org.apache.camel.component.protojson.benchmark;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.openjdk.jmh.annotations.*;

import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
public class CamelContextOnlyBenchmark {

    @State(Scope.Benchmark)
    public static class S {
        CamelContext context;

        @Setup(Level.Trial)
        public void setup() throws Exception {
            context = new DefaultCamelContext();
            // HİÇ route eklemiyoruz
            context.start();
        }

        @TearDown(Level.Trial)
        public void tearDown() throws Exception {
            context.stop();
        }
    }

    @Benchmark
    public int dummy(S s) {
        // Sadece context’in yaşadığını teyit eden anlamsız bir op
        return 1;
    }
}
