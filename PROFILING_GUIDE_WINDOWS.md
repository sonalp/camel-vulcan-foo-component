# Windows Profiling Guide - Map Field Bottleneck Analysis

## 🎯 Hedef
parseMapFields() method'unun gerçek bottleneck'lerini bulmak için CPU ve memory profiling.

---

## 🔧 1. JMH Built-in Profilers (En Kolay)

### A) GC Profiler - Memory Allocation Analysis

```bash
mvn clean test-compile

mvn exec:java -Dexec.mainClass=org.openjdk.jmh.Main ^
  -Dexec.classpathScope=test ^
  -Dexec.args="-prof gc MapFieldBenchmark.parseMapFields"
```

**Ne Gösterir**:
- Allocation rate (MB/sec)
- GC time percentage
- Normalized allocation (bytes/op)

**Aradığımız**:
```
MapFieldBenchmark.parseMapFields (1000 entries)
  gc.alloc.rate:     1500 MB/sec   ← Yüksek allocation
  gc.time:           5%             ← GC overhead
  gc.alloc.rate.norm: 180 KB/op    ← Per-operation allocation
```

---

### B) Stack Profiler - Hotspot Detection

```bash
mvn exec:java -Dexec.mainClass=org.openjdk.jmh.Main ^
  -Dexec.classpathScope=test ^
  -Dexec.args="-prof stack:lines=5;top=10 MapFieldBenchmark.parseMapFields"
```

**Ne Gösterir**:
- CPU time per method
- Top 10 hottest stack traces
- Method call hierarchy

**Örnek Çıktı**:
```
....[Thread state distributions]....
 91.2%  RUNNABLE
  8.8%  WAITING

....[Thread state: RUNNABLE]....
 45.3%  35.2%  com.fasterxml.jackson.core.JsonParser.nextToken()
 28.1%  21.5%  org.apache.camel.component.protojson.internal.parser.ProtoJsonStreamer.parseSingleField()
 12.7%   9.8%  com.google.protobuf.DynamicMessage$Builder.setField()
  8.9%   6.8%  org.apache.camel.component.protojson.internal.parser.ProtoJsonStreamer.convertMapKey()
  5.0%   3.8%  java.util.HashMap.get()
```

Bu bize **gerçek bottleneck'i** gösterecek!

---

### C) Performance Counters (CPU Cache, Branch Mispredictions)

```bash
mvn exec:java -Dexec.mainClass=org.openjdk.jmh.Main ^
  -Dexec.classpathScope=test ^
  -Dexec.args="-prof perfnorm MapFieldBenchmark.parseMapFields"
```

**Ne Gösterir**:
- Instructions per operation
- CPU cycles
- Cache miss rates
- Branch mispredictions

---

## 🔥 2. Java Flight Recorder (JFR) - En Detaylı

### Setup (JDK 11+ built-in)

```bash
# JFR profiling ile benchmark çalıştır
mvn exec:java -Dexec.mainClass=org.openjdk.jmh.Main ^
  -Dexec.classpathScope=test ^
  -Dexec.args="-prof jfr MapFieldBenchmark.parseMapFields"
```

Bu `.jfr` dosyası oluşturur, ardından JDK Mission Control ile aç:

```bash
# JDK Mission Control'u başlat (JDK_HOME/bin/jmc.exe)
jmc.exe
```

**Veya** IntelliJ IDEA ile:
1. File → Open → benchmark-result.jfr
2. Profiler tab açılır

**Ne Gösterir**:
- CPU flamegraph
- Allocation hotspots
- Method profiling
- Lock contention
- GC events

---

## 📊 3. Comparison Benchmark - Before vs After

Optimizasyonun gerçek etkisini görmek için:

```bash
# BEFORE optimization sonuçlarını kaydet
mvn exec:java -Dexec.mainClass=org.openjdk.jmh.Main ^
  -Dexec.classpathScope=test ^
  -Dexec.args="-rf json -rff results-before.json MapFieldBenchmark"

# AFTER optimization (şu anki kod)
mvn exec:java -Dexec.mainClass=org.openjdk.jmh.Main ^
  -Dexec.classpathScope=test ^
  -Dexec.args="-rf json -rff results-after.json MapFieldBenchmark"
```

Sonra karşılaştır:
```bash
# JMH comparison tool kullan
# https://jmh.morethan.io/ - Upload both JSON files
```

---

## 🧪 4. Detailed Profiling Script

Tek seferde tüm profiling'leri çalıştır:

```bash
@echo off
echo ========================================
echo Map Field Profiling Suite
echo ========================================
echo.

echo [1/4] GC Profiling...
call mvn exec:java -Dexec.mainClass=org.openjdk.jmh.Main ^
  -Dexec.classpathScope=test ^
  -Dexec.args="-prof gc -rf csv -rff gc-profile.csv MapFieldBenchmark.parseMapFields"

echo.
echo [2/4] Stack Profiling...
call mvn exec:java -Dexec.mainClass=org.openjdk.jmh.Main ^
  -Dexec.classpathScope=test ^
  -Dexec.args="-prof stack:lines=10;top=20 -rf csv -rff stack-profile.csv MapFieldBenchmark.parseMapFields"

echo.
echo [3/4] Performance Counters...
call mvn exec:java -Dexec.mainClass=org.openjdk.jmh.Main ^
  -Dexec.classpathScope=test ^
  -Dexec.args="-prof perfnorm -rf csv -rff perf-profile.csv MapFieldBenchmark.parseMapFields"

echo.
echo [4/4] JFR Recording...
call mvn exec:java -Dexec.mainClass=org.openjdk.jmh.Main ^
  -Dexec.classpathScope=test ^
  -Dexec.args="-prof jfr -rf json -rff jfr-results.json MapFieldBenchmark.parseMapFields"

echo.
echo ========================================
echo Profiling Complete!
echo ========================================
echo Results:
echo   - gc-profile.csv
echo   - stack-profile.csv
echo   - perf-profile.csv
echo   - jfr-results.json
echo   - *.jfr files (open with JMC)
echo ========================================
```

`profile-all.bat` olarak kaydet, çalıştır:
```bash
profile-all.bat
```

---

## 🔍 5. Specific Hypothesis Testing

### Test 1: Builder Allocation Overhead

```bash
# GC profiler ile builder allocation'ı ölç
mvn exec:java -Dexec.mainClass=org.openjdk.jmh.Main ^
  -Dexec.classpathScope=test ^
  -Dexec.args="-prof gc:churn=true MapFieldBenchmark.parseMapFields -p mapSize=1000"
```

**Bakılacak Metrik**:
```
gc.alloc.rate.norm:  ??? KB/op

Beklenen (eğer builder allocation major issue):
  BEFORE: ~200 KB/op  (1000 builders × 200 bytes)
  AFTER:  ~150 KB/op  (1 builder + 1000 messages)

Gerçek: Muhtemelen ~155 KB/op (minimal difference)
→ Builder allocation %5 overhead (not %30!)
```

---

### Test 2: Integer Parsing Overhead

```bash
# parseIntKeyMap vs parseMapFields karşılaştır
mvn exec:java -Dexec.mainClass=org.openjdk.jmh.Main ^
  -Dexec.classpathScope=test ^
  -Dexec.args="-prof stack MapFieldBenchmark.parseIntKeyMap -p mapSize=1000"
```

**Bakılacak**:
- `Integer.parseInt()` stack trace'de görünüyor mu?
- Ne kadar CPU time harcıyor?

**Beklenti**: <2% CPU time (negligible)

---

### Test 3: JSON Parsing Overhead

```bash
# Stack profiler ile Jackson overhead'i ölç
mvn exec:java -Dexec.mainClass=org.openjdk.jmh.Main ^
  -Dexec.classpathScope=test ^
  -Dexec.args="-prof stack:lines=10 MapFieldBenchmark -p mapSize=1000"
```

**Aranacak Stack Traces**:
```
com.fasterxml.jackson.core.JsonParser.nextToken()
com.fasterxml.jackson.core.json.UTF8StreamJsonParser.getText()
```

**Beklenti**: %30-40 CPU time

---

## 📈 6. Visualization Tools

### A) JMH Visualizer (Web-based)

1. Benchmark'ı JSON output ile çalıştır:
```bash
mvn exec:java -Dexec.mainClass=org.openjdk.jmh.Main ^
  -Dexec.classpathScope=test ^
  -Dexec.args="-rf json -rff benchmark-results.json MapFieldBenchmark"
```

2. https://jmh.morethan.io/ aç
3. `benchmark-results.json` upload et
4. Interactive charts görüntüle

---

### B) Excel/LibreOffice ile CSV Analysis

```bash
# CSV export
mvn exec:java -Dexec.mainClass=org.openjdk.jmh.Main ^
  -Dexec.classpathScope=test ^
  -Dexec.args="-rf csv -rff results.csv MapFieldBenchmark"
```

Excel'de aç, pivot table/chart oluştur.

---

### C) JFR Flamegraph (Command Line)

```bash
# JFR dosyasını flamegraph'e dönüştür
# https://github.com/chrishantha/jfr-flame-graph

java -jar jfr-flame-graph.jar benchmark-result.jfr flamegraph.html
```

Browser'da `flamegraph.html` aç → Interactive flamegraph!

---

## 🎯 7. Profiling Checklist

Run this sequence:

### Step 1: Quick GC Check
```bash
mvn exec:java -Dexec.mainClass=org.openjdk.jmh.Main ^
  -Dexec.classpathScope=test ^
  -Dexec.args="-prof gc MapFieldBenchmark.parseMapFields -p mapSize=1000"
```

**Action**: Note `gc.alloc.rate.norm` (bytes/op)

---

### Step 2: Hotspot Detection
```bash
mvn exec:java -Dexec.mainClass=org.openjdk.jmh.Main ^
  -Dexec.classpathScope=test ^
  -Dexec.args="-prof stack:lines=10;top=20 MapFieldBenchmark.parseMapFields -p mapSize=1000"
```

**Action**: Identify top 3 hottest methods

---

### Step 3: Detailed JFR
```bash
mvn exec:java -Dexec.mainClass=org.openjdk.jmh.Main ^
  -Dexec.classpathScope=test ^
  -Dexec.args="-prof jfr MapFieldBenchmark.parseMapFields -p mapSize=1000"
```

**Action**: Open .jfr with JMC, analyze:
- Hot Methods view
- Allocation view
- Call Tree

---

## 🔬 8. Expected Findings

Based on benchmark results, profiling should reveal:

### Hypothesis A: JSON Parsing Dominant
```
Stack Profile:
  40%  com.fasterxml.jackson.core.JsonParser.nextToken()
  20%  ProtoJsonStreamer.parseSingleField()
  15%  ProtoJsonStreamer.convertMapValue()
  10%  DynamicMessage.Builder.setField()
   5%  ProtoJsonStreamer.convertMapKey()
  10%  Other
```

→ **Optimization Target**: parseSingleField() fast path

---

### Hypothesis B: Type Conversion Overhead
```
Stack Profile:
  25%  JsonParser.nextToken()
  35%  ProtoJsonStreamer.parseSingleField()  ← Hottest!
  20%  MessageTypeConverter.mergeInto()      ← Nested messages
  10%  Builder operations
  10%  Other
```

→ **Optimization Target**: Type-specific fast paths

---

### Hypothesis C: Builder Allocation Negligible
```
GC Profile:
  gc.alloc.rate.norm: 155,000 bytes/op  (expected 150KB)

Breakdown:
  - 1000 Message objects: ~150 KB
  - 1 Builder reused: ~200 bytes
  - Other allocations: ~5 KB
```

→ **Conclusion**: Builder reuse saves <1% (confirmed!)

---

## 🚀 Next Steps After Profiling

1. **Run Step 1-3** (GC + Stack + JFR)
2. **Identify Top 3 Hotspots**
3. **Implement Targeted Optimization**:
   - If JSON parsing dominant → Can't optimize (external lib)
   - If parseSingleField dominant → Fast path for scalars
   - If nested message dominant → Can't optimize (complexity)

---

## 💾 Save Script for Later

```bash
# profile.bat
@echo off
set BENCHMARK=%1
if "%BENCHMARK%"=="" set BENCHMARK=MapFieldBenchmark.parseMapFields

echo Profiling: %BENCHMARK%
echo.

echo [GC Profiling]
mvn exec:java -Dexec.mainClass=org.openjdk.jmh.Main ^
  -Dexec.classpathScope=test ^
  -Dexec.args="-prof gc %BENCHMARK% -p mapSize=1000"

echo.
echo [Stack Profiling]
mvn exec:java -Dexec.mainClass=org.openjdk.jmh.Main ^
  -Dexec.classpathScope=test ^
  -Dexec.args="-prof stack:lines=10;top=20 %BENCHMARK% -p mapSize=1000"

pause
```

Usage:
```bash
profile.bat MapFieldBenchmark.parseMapFields
```

---

## 📚 Useful Links

- **JMH Samples**: https://github.com/openjdk/jmh/tree/master/jmh-samples
- **JMH Profilers**: https://github.com/openjdk/jmh/tree/master/jmh-core/src/main/java/org/openjdk/jmh/profile
- **JMH Visualizer**: https://jmh.morethan.io/
- **JDK Mission Control**: https://www.oracle.com/java/technologies/jdk-mission-control.html

---

## 🎯 TL;DR - Quick Start

```bash
# Fastest way to find bottleneck:
mvn exec:java -Dexec.mainClass=org.openjdk.jmh.Main ^
  -Dexec.classpathScope=test ^
  -Dexec.args="-prof stack:lines=10 MapFieldBenchmark.parseMapFields -p mapSize=1000"
```

Look for output like:
```
....[Thread state: RUNNABLE]....
 XX.X%  Method.name()  ← This is your bottleneck!
```

That's it! 🎉
