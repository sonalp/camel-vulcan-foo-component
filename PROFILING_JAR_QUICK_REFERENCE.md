# JMH JAR Profiling - Quick Reference

## 🚀 Setup (One Time)

### Build Benchmark JAR
```bash
mvn clean package -DskipTests
```

This creates: `target\jmh-benchmarks.jar`

---

## ⚡ Quick Commands (Copy-Paste Ready)

### 1. List All Benchmarks
```bash
java -jar target\jmh-benchmarks.jar -l
```

### 2. Run Specific Benchmark (No Profiling)
```bash
java -jar target\jmh-benchmarks.jar MapFieldBenchmark.parseMapFields
```

### 3. Run with Parameter
```bash
java -jar target\jmh-benchmarks.jar MapFieldBenchmark.parseMapFields -p mapSize=1000
```

---

## 🔬 Profiling Commands

### GC Profiler (Memory Allocation)
```bash
java -jar target\jmh-benchmarks.jar MapFieldBenchmark.parseMapFields -p mapSize=1000 -prof gc
```

**What to look for**:
```
·gc.alloc.rate.norm        155,000 B/op    ← Total allocation per operation
```

---

### Stack Profiler (CPU Hotspots) ⭐ Most Important!
```bash
java -jar target\jmh-benchmarks.jar MapFieldBenchmark.parseMapFields -p mapSize=1000 -prof stack:lines=10,top=20
```

**What to look for**:
```
....[Thread state: RUNNABLE]....
 45.3%  35.2%  com.fasterxml.jackson.core.JsonParser.nextToken()
 28.1%  21.5%  ProtoJsonStreamer.parseSingleField()
  ↑      ↑
  |      └── CPU time relative to RUNNABLE state
  └── CPU time total
```

**First percentage = This is the bottleneck!**

---

### Performance Counters
```bash
java -jar target\jmh-benchmarks.jar MapFieldBenchmark.parseMapFields -p mapSize=1000 -prof perfnorm
```

**What to look for**:
```
·CPI                       0.5 cycles/insn   ← Cycles per instruction
·L1-dcache-load-misses     1,234,567         ← Cache misses
·branches                  12,345,678        ← Branch count
·branch-misses             123,456           ← Mispredictions
```

---

### JFR (Java Flight Recorder) - Detailed Analysis
```bash
java -jar target\jmh-benchmarks.jar MapFieldBenchmark.parseMapFields -p mapSize=1000 -prof jfr
```

**Output**: Creates `.jfr` files in current directory

**Open with**:
- JDK Mission Control: `jmc.exe`
- IntelliJ IDEA: File → Open → `*.jfr`

---

## 📊 Export Results

### JSON (for JMH Visualizer)
```bash
java -jar target\jmh-benchmarks.jar MapFieldBenchmark -rf json -rff results.json
```

Upload to: https://jmh.morethan.io/

### CSV (for Excel)
```bash
java -jar target\jmh-benchmarks.jar MapFieldBenchmark -rf csv -rff results.csv
```

---

## 🎯 Recommended Workflow

### Step 1: Quick Check
```bash
profile-quick-jar.bat
```

### Step 2: Identify Bottleneck
Look at stack profile output:
- If `JsonParser.nextToken()` > 35% → JSON parsing bottleneck
- If `parseSingleField()` > 30% → Type conversion bottleneck
- If `Builder` operations > 15% → Builder overhead

### Step 3: Detailed Analysis (if needed)
```bash
profile-detailed-jar.bat
```

---

## 🔍 Advanced Options

### Compare Multiple Parameters
```bash
java -jar target\jmh-benchmarks.jar MapFieldBenchmark.parseMapFields -p mapSize=10,100,1000 -prof gc
```

### Multiple Profilers at Once
```bash
java -jar target\jmh-benchmarks.jar MapFieldBenchmark.parseMapFields -prof gc -prof stack
```

### Change Warmup/Measurement
```bash
java -jar target\jmh-benchmarks.jar MapFieldBenchmark.parseMapFields -wi 5 -i 10 -f 3
```
- `-wi 5` = 5 warmup iterations
- `-i 10` = 10 measurement iterations
- `-f 3` = 3 forks

### Single Iteration (Fast Test)
```bash
java -jar target\jmh-benchmarks.jar MapFieldBenchmark.parseMapFields -wi 1 -i 1 -f 1
```

---

## 📈 Interpreting Stack Profile Output

### Example Output:
```
....[Thread state distributions]....
 91.2%  RUNNABLE
  8.8%  WAITING

....[Thread state: RUNNABLE]....
 45.3%  35.2%  com.fasterxml.jackson.core.JsonParser.nextToken
 28.1%  21.5%  ProtoJsonStreamer.parseSingleField
 12.7%   9.8%  com.google.protobuf.DynamicMessage$Builder.setField
  8.9%   6.8%  ProtoJsonStreamer.convertMapKey
  5.0%   3.8%  java.util.HashMap.get
```

### Reading the Numbers:
- **First column** (45.3%): Percentage of total CPU time
- **Second column** (35.2%): Percentage of RUNNABLE state time
- **Method name**: Where time is spent

### Bottleneck Identification:
- **> 40%**: Major bottleneck!
- **20-40%**: Significant overhead
- **10-20%**: Moderate impact
- **< 10%**: Minor contributor

---

## 🎯 Common Profiling Scenarios

### Scenario 1: Find Memory Leak
```bash
java -jar target\jmh-benchmarks.jar MapFieldBenchmark -prof gc:churn=true
```

Look for: High `gc.alloc.rate` (MB/sec)

### Scenario 2: Find CPU Bottleneck
```bash
java -jar target\jmh-benchmarks.jar MapFieldBenchmark -prof stack:lines=10,top=20
```

Look for: Methods with >30% CPU time

### Scenario 3: Compare Before/After Optimization
```bash
# Before
java -jar target\jmh-benchmarks.jar MapFieldBenchmark -rf json -rff before.json

# Make changes...

# After
java -jar target\jmh-benchmarks.jar MapFieldBenchmark -rf json -rff after.json

# Compare at https://jmh.morethan.io/
```

### Scenario 4: Flamegraph Generation (with JFR)
```bash
java -jar target\jmh-benchmarks.jar MapFieldBenchmark -prof jfr

# Open .jfr with JMC
jmc.exe
```

---

## 🐛 Troubleshooting

### JAR Not Found
```
mvn clean package -DskipTests
```

### Profiler Not Available
```bash
# Check available profilers
java -jar target\jmh-benchmarks.jar -lprof
```

Common profilers:
- ✅ `gc` (always available)
- ✅ `stack` (always available)
- ✅ `jfr` (JDK 11+)
- ⚠️ `perfnorm` (Windows may not support)
- ❌ `async` (Linux/macOS only)

### Profiler Output Too Verbose
```bash
# Reduce iterations
java -jar target\jmh-benchmarks.jar MapFieldBenchmark -wi 2 -i 3 -prof stack
```

---

## 💡 Tips

### 1. Focus on Stack Profiler First
Most actionable information comes from stack profiling.

### 2. Use mapSize=1000 for Profiling
Larger datasets show bottlenecks more clearly.

### 3. Run Multiple Times
JVM warmup can affect results. Run 2-3 times and compare.

### 4. Close Other Applications
Background processes can skew results.

### 5. Check Error Margins
High `±` values = unreliable results, run more iterations.

---

## 📚 Help Commands

### List all options
```bash
java -jar target\jmh-benchmarks.jar -h
```

### List all profilers
```bash
java -jar target\jmh-benchmarks.jar -lprof
```

### Profiler-specific help
```bash
java -jar target\jmh-benchmarks.jar -prof stack:help
java -jar target\jmh-benchmarks.jar -prof gc:help
java -jar target\jmh-benchmarks.jar -prof jfr:help
```

---

## 🎯 TL;DR - One Command to Rule Them All

```bash
java -jar target\jmh-benchmarks.jar MapFieldBenchmark.parseMapFields -p mapSize=1000 -prof stack:lines=10,top=20
```

Look for methods with >30% CPU time → That's your bottleneck! 🔥
