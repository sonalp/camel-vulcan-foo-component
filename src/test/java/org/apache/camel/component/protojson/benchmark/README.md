# ProtoJSON Microbenchmarks

JMH-based performance benchmarks for the ProtoJSON component.

## Benchmark Suites

### 1. SimpleMessageBenchmark
Tests baseline performance with minimal complexity.
- **Scenarios**: Parse, print, parse+print
- **Message Type**: SimpleUser (4 fields)
- **Measures**: Raw throughput without complex features

### 2. NestedMessageBenchmark
Tests performance with nested message structures.
- **Scenarios**: Parse, print, parse+print
- **Message Type**: UserWithAddress (nested Address message)
- **Measures**: Builder allocation overhead for nested messages

### 3. RepeatedFieldBenchmark
Tests performance with repeated/array fields.
- **Scenarios**: Parse, print
- **Message Type**: UserWithTags (arrays of strings, ints, messages)
- **Parameters**: Array sizes: 10, 100, 1000
- **Measures**: ArrayList resizing overhead

### 4. MapFieldBenchmark
Tests performance with map fields.
- **Scenarios**: Parse, print, integer key conversion
- **Message Type**: UserWithMetadata (various map types)
- **Parameters**: Map sizes: 10, 100, 1000
- **Measures**: String allocation overhead (especially for int keys)

### 5. CacheEffectivenessBenchmark
Tests cache warming and effectiveness.
- **Scenarios**:
  - **Cold Start**: New engine per invocation
  - **Warm Cache**: Same engine, same message type
  - **Mixed Types**: Cache thrashing with 5 different message types
  - **Field Name Lookup**: Correct case vs wrong case (lowercase fallback)
- **Measures**: Cache hit/miss impact on performance

## Running Benchmarks

### Run All Benchmarks
```bash
mvn clean test-compile
mvn exec:java -Dexec.mainClass=org.openjdk.jmh.Main \
  -Dexec.classpathScope=test \
  -Dexec.args="org.apache.camel.component.protojson.benchmark"
```

### Run Specific Benchmark
```bash
mvn exec:java -Dexec.mainClass=org.openjdk.jmh.Main \
  -Dexec.classpathScope=test \
  -Dexec.args="SimpleMessageBenchmark"
```

### Run with Custom Parameters
```bash
# Run only specific methods
mvn exec:java -Dexec.mainClass=org.openjdk.jmh.Main \
  -Dexec.classpathScope=test \
  -Dexec.args="SimpleMessageBenchmark.parseSimpleMessage"

# Custom warmup/measurement
mvn exec:java -Dexec.mainClass=org.openjdk.jmh.Main \
  -Dexec.classpathScope=test \
  -Dexec.args="-wi 5 -i 10 -f 2 SimpleMessageBenchmark"

# Run with specific array/map size
mvn exec:java -Dexec.mainClass=org.openjdk.jmh.Main \
  -Dexec.classpathScope=test \
  -Dexec.args="-p arraySize=1000 RepeatedFieldBenchmark"
```

### Generate Report
```bash
# JSON output
mvn exec:java -Dexec.mainClass=org.openjdk.jmh.Main \
  -Dexec.classpathScope=test \
  -Dexec.args="-rf json -rff benchmark-results.json ."

# CSV output
mvn exec:java -Dexec.mainClass=org.openjdk.jmh.Main \
  -Dexec.classpathScope=test \
  -Dexec.args="-rf csv -rff benchmark-results.csv ."
```

## Understanding Results

### Throughput Mode
Results are in **operations per second** (higher is better).

Example output:
```
Benchmark                                    Mode  Cnt      Score      Error  Units
SimpleMessageBenchmark.parseSimpleMessage   thrpt    5  50000.123 ± 1234.567  ops/s
```

This means: ~50,000 messages parsed per second.

### Comparing Results

**Expected Performance Hierarchy** (fastest to slowest):
1. Warm Cache > Cold Start (~30-40% faster)
2. Simple Message > Nested > Repeated > Map
3. Small Arrays/Maps > Large Arrays/Maps
4. Correct Field Case > Wrong Field Case (~10-15% faster)

### Key Metrics to Watch

1. **Cache Impact**: Compare `CacheEffectivenessBenchmark.warmCache` vs `coldStart`
2. **Scalability**: How does throughput change with `arraySize`/`mapSize` parameters?
3. **Field Lookup**: Compare `fieldNameCorrectCase` vs `fieldNameWrongCase`

## Profiling

### CPU Profiling with async-profiler
```bash
mvn exec:java -Dexec.mainClass=org.openjdk.jmh.Main \
  -Dexec.classpathScope=test \
  -Dexec.args="-prof async:libPath=/path/to/libasyncProfiler.so SimpleMessageBenchmark"
```

### GC Profiling
```bash
mvn exec:java -Dexec.mainClass=org.openjdk.jmh.Main \
  -Dexec.classpathScope=test \
  -Dexec.args="-prof gc SimpleMessageBenchmark"
```

### Allocation Profiling
```bash
mvn exec:java -Dexec.mainClass=org.openjdk.jmh.Main \
  -Dexec.classpathScope=test \
  -Dexec.args="-prof gc:churn=true MapFieldBenchmark"
```

## Interpreting GC Output

Look for:
- **Allocation rate** (MB/sec): Lower is better
- **GC time** (%): Should be < 5%
- **Normalized allocation** (bytes/op): Shows per-operation memory cost

High allocation rates indicate:
- Excessive object creation
- String allocations (map keys, field names)
- Builder allocations (nested messages)

## Performance Baselines

Expected approximate throughput on modern hardware (8-core CPU, JDK 17):

| Benchmark | Throughput | Notes |
|-----------|-----------|-------|
| Simple Message Parse | ~50K ops/s | Baseline |
| Nested Message Parse | ~30K ops/s | Builder overhead |
| Repeated (100 elements) | ~8K ops/s | ArrayList resizing |
| Map (100 entries) | ~10K ops/s | String allocation |
| Cold Start | ~35K ops/s | No cache |
| Warm Cache | ~52K ops/s | Full cache |

## Tips for Accurate Benchmarks

1. **Close other applications** to reduce noise
2. **Disable CPU frequency scaling** for consistent results
3. **Run multiple forks** (-f 3) to detect JIT variations
4. **Longer measurement times** (-i 10) for stable results
5. **Check GC impact** - high GC time invalidates results

## Contributing

When adding new benchmarks:
1. Use meaningful `@BenchmarkMode` (typically Throughput)
2. Add proper warmup (3+ iterations)
3. Document what the benchmark measures
4. Use realistic data sizes
5. Add to this README

## References

- [JMH Documentation](https://github.com/openjdk/jmh)
- [JMH Samples](https://github.com/openjdk/jmh/tree/master/jmh-samples/src/main/java/org/openjdk/jmh/samples)
- [Performance Analysis Report](../../../../../../../../../PERFORMANCE_ANALYSIS.md)
