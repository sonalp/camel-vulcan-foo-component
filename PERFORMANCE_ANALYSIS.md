# Performans Analizi Raporu
**Camel ProtoJSON Component**
**Tarih**: 2026-01-10

---

## 1. Güçlü Yönler (İyi Tasarlanmış Optimizasyonlar)

### ✅ Akıllı Caching Stratejisi

**Konum**:
- `FieldConverterRegistry.java:40`
- `MetaRegistry.java:28`
- `BuilderFactory.java:16`

**Implementasyon Detayları**:
- **Converter Lookup Cache**: İlk aramadan sonra O(1) performans
- **MetaRegistry**: Field descriptor metadata'sını `ConcurrentHashMap` ile cache'liyor
- **BuilderFactory**: `MethodHandle`'ları cache'leyerek reflection overhead'ını minimize ediyor

**Örnek Kod** (`FieldConverterRegistry.java:48`):
```java
Optional<T> result = cache.computeIfAbsent(fieldKey, key -> {
    for (T converter : converters) {
        if (matcher.matches(converter, fd)) {
            return Optional.of(converter);
        }
    }
    return Optional.empty();  // Negative cache - önemli!
});
```

**Performans İyileştirmesi**: Negative caching, başarısız aramaları da cache'liyor. Bu sayede aynı başarısız lookup tekrar edildiğinde direkt cache'den dönüyor.

---

### ✅ MethodHandle Kullanımı

**Konum**: `BuilderFactory.java:16`

**Implementasyon**:
```java
MethodHandle mh = cache.computeIfAbsent(type, this::findNewBuilderHandle);
return (Message.Builder) mh.invoke();
```

**Etki**:
- Reflection API yerine `MethodHandle` kullanımı **~3-5x daha hızlı**
- JVM tarafından optimize edilebilir (inlining)
- Type-safe invocation

---

### ✅ Streaming Approach

**Konum**:
- `ProtoJsonStreamer.java:29`
- `ProtoJsonEngine.java:38`

**Özellikler**:
- Jackson'ın streaming API'sini kullanarak memory-efficient işlem
- DOM-based parsing yerine event-driven processing
- Büyük JSON payload'ları için düşük memory footprint

---

## 2. Performans Darboğazları

### ⚠️ KRITIK: Registry Auto-Discovery Overhead

**Konum**: `ProtoJsonDataFormat.java:316-384`

**Sorunlu Kod**:
```java
if (Boolean.TRUE.equals(autoDiscoverConverters) && camelContext != null) {
    discoverConvertersFromRegistry(allInFieldConverters, allInMapConverters, allOutFieldConverters);
}
```

**Sorun**:
- Her `doStart()` çağrısında registry scan yapılıyor
- Spring/CDI container'da yüzlerce bean varsa yavaş
- `findByType()` çağrıları maliyetli
- Tekrar eden route reload'larda performans kaybı

**Tahmini Etki**:
- 100+ bean registry: ~50-100ms overhead
- 1000+ bean registry: ~500ms+ overhead

**Önerilen Çözüm**:
```java
private volatile boolean convertersDiscovered = false;

@Override
protected void doStart() throws Exception {
    super.doStart();
    if (Boolean.TRUE.equals(autoDiscoverConverters)
        && camelContext != null
        && !convertersDiscovered) {
        synchronized (this) {
            if (!convertersDiscovered) {
                discoverConvertersFromRegistry(...);
                convertersDiscovered = true;
            }
        }
    }
}
```

---

### ⚠️ String Allocation Overhead

**Konum**:
- `DefaultMessageJsonConverter.java:133`
- `ProtoJsonStreamer.java:404`

**Sorunlu Kod**:
```java
// Map key conversion - her entry için yeni String
String key = String.valueOf(keyObj);  // Line 134
```

**Sorun**:
- Map field'lar için her key-value pair'inde String allocation
- Primitive key'ler (int, long) için boxing + String conversion

**Etki**:
- GC pressure, özellikle büyük map'lerde (1000+ entries)
- Memory churn: 1K entry map = 1K String object

**Önerilen Çözüm**:
```java
// String pool for common numeric keys
private static final String[] COMMON_NUMBERS = new String[1000];
static {
    for (int i = 0; i < 1000; i++) {
        COMMON_NUMBERS[i] = String.valueOf(i);
    }
}

String key = (keyObj instanceof Integer && (int)keyObj < 1000)
    ? COMMON_NUMBERS[(int)keyObj]
    : String.valueOf(keyObj);
```

---

### ⚠️ Field Name Lookup Double Hash

**Konum**: `MetaRegistry.java:72-74`

**Sorunlu Kod**:
```java
public FieldMeta find(String name) {
    FieldMeta fm = byName.get(name);
    return fm != null ? fm : byName.get(name.toLowerCase(Locale.ROOT));
}
```

**Sorun**:
- İki HashMap lookup (case-sensitive fail ederse)
- Her lookup için lowercase conversion overhead
- `Locale.ROOT` allocation

**Etki**:
- Her field için potansiyel 2x lookup overhead
- CPU + GC pressure (String.toLowerCase allocations)

**Önerilen Çözüm**:
```java
// Fast path cache
private final ConcurrentHashMap<String, FieldMeta> fastPath = new ConcurrentHashMap<>();

public FieldMeta find(String name) {
    return fastPath.computeIfAbsent(name, k -> {
        FieldMeta fm = byName.get(k);
        return fm != null ? fm : byName.get(k.toLowerCase(Locale.ROOT));
    });
}
```

**Tahmini İyileştirme**: %10-15 daha hızlı field lookup

---

## 3. Memory Allocation Hotspots

### 🔥 Builder Allocation

**Konum**:
- `ProtoJsonStreamer.java:196`
- `ProtoJsonStreamer.java:322`

**Kod**:
```java
Message.Builder nestedBuilder = builder.newBuilderForField(fd);  // Her message için
```

**Sorun**:
- Nested message'lar için her seferinde yeni builder allocation
- Deep nesting'de exponential allocation pattern

**Etki Örneği**:
```
Depth 1: 1 builder
Depth 2: 1 + 5 = 6 builders (5 nested)
Depth 3: 1 + 5 + 25 = 31 builders
Depth 5: ~156 builders
```

**Not**: Protobuf API tasarımı nedeniyle builder pooling implementasyonu zor/imkansız.

---

### 🔥 Repeated Field Operations

**Konum**: `ProtoJsonStreamer.java:254-260`

**Kod**:
```java
while ((t = p.nextToken()) != JsonToken.END_ARRAY) {
    addRepeatedElement(p, t, fd, builder, ctx);  // Her element için
}
```

**Sorun**:
- `addRepeatedField()` internal ArrayList resize'ları
- ArrayList default capacity: 10, grows by 1.5x
- Büyük array'lerde multiple resize + copy

**Etki**:
- 10K element array: ~14 resize operation
- Memory churn ve CPU overhead

**Not**: JSON streaming nedeniyle array size öngörülemez, pre-allocation mümkün değil.

---

## 4. Thread Safety Analizi

### ✅ İyi: ConcurrentHashMap Kullanımı

**Thread-safe Cache'ler**:
- `FieldConverterRegistry.java:20` - Thread-safe converter cache
- `MetaRegistry.java:22` - ConcurrentHashMap ile metadata cache
- `BuilderFactory.java:12` - ConcurrentHashMap ile MethodHandle cache

**Sonuç**: Multi-threaded Camel route'larda güvenli kullanım.

---

### ⚠️ Dikkat: Shared ObjectMapper

**Konum**: `ProtoJsonDataFormat.java:337`

**Kod**:
```java
.objectMapper(objectMapper != null ? objectMapper : new ObjectMapper())
```

**Analiz**:
- Jackson `ObjectMapper` thread-safe
- `JsonFactory` instance'lar thread-safe
- `JsonParser`/`JsonGenerator` instance'lar **thread-safe DEĞİL**

**Mevcut Durum**: ✅ Her parse/print işleminde yeni parser/generator oluşturuluyor - güvenli.

---

## 5. Benchmark Önerileri

### Test Senaryoları

```java
// 1. Cold Start (No Cache)
@Benchmark
public void coldStartSimpleMessage() {
    // Clear all caches
    // Parse simple message
}

// 2. Warm Cache (Repeated Same Message Type)
@Benchmark
public void warmCacheRepeated() {
    // Parse same message type 1000 times
}

// 3. Mixed Message Types (Cache Thrashing)
@Benchmark
public void mixedMessageTypes() {
    // Parse 10 different message types in rotation
}

// 4. Large Arrays
@Benchmark
public void largeArrays() {
    // 1K, 10K, 100K element repeated fields
}

// 5. Deep Nesting
@Benchmark
public void deepNesting() {
    // 5-10 seviye nested messages
}

// 6. Map-Heavy Payloads
@Benchmark
public void mapHeavy() {
    // 1K-10K entry map fields
}

// 7. Well-Known Types
@Benchmark
public void wellKnownTypes() {
    // Timestamp, Duration, Any, Struct
}
```

### JMH Configuration Önerisi

```xml
<dependency>
    <groupId>org.openjdk.jmh</groupId>
    <artifactId>jmh-core</artifactId>
    <version>1.37</version>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.openjdk.jmh</groupId>
    <artifactId>jmh-generator-annprocess</artifactId>
    <version>1.37</version>
    <scope>test</scope>
</dependency>
```

---

## 6. Hızlı Kazançlar İçin Öneriler

### 1️⃣ Converter Discovery Caching (Yüksek Öncelik)

**Implementasyon**:
```java
// ProtoJsonDataFormat.java
private static final Map<String, List<JsonInFieldConverter>> GLOBAL_CONVERTER_CACHE =
    new ConcurrentHashMap<>();

private void discoverConvertersFromRegistry(...) {
    String cacheKey = camelContext.getName();
    List<JsonInFieldConverter> cached = GLOBAL_CONVERTER_CACHE.get(cacheKey);
    if (cached != null) {
        allInFieldConverters.addAll(cached);
        return;
    }
    // ... discovery logic ...
    GLOBAL_CONVERTER_CACHE.put(cacheKey, discovered);
}
```

**Tahmini Kazanç**: 50-500ms per route restart

---

### 2️⃣ Field Lookup Optimization (Orta Öncelik)

**Implementasyon**: MetaRegistry'de iki-seviye lookup yerine cached lookup (yukarıda detaylandırıldı)

**Tahmini Kazanç**: %10-15 daha hızlı parsing

---

### 3️⃣ String Interning for Field Names (Düşük Öncelik)

**Implementasyon**:
```java
// Repeated field names için
private static final Map<String, String> FIELD_NAME_POOL = new ConcurrentHashMap<>();

private String internFieldName(String name) {
    return FIELD_NAME_POOL.computeIfAbsent(name, Function.identity());
}
```

**Tahmini Kazanç**: %5-8 daha az memory usage (tekrar eden field names)

---

### 4️⃣ Warm-up Phase (Opsiyonel)

**Implementasyon**:
```java
/**
 * Pre-warm caches with expected message types.
 * Call during application startup.
 */
public void warmupCaches(List<Class<? extends Message>> messageTypes) {
    for (Class<? extends Message> type : messageTypes) {
        try {
            Message.Builder builder = builderFactory.newBuilder(type);
            metaRegistry.metaFor(builder.getDescriptorForType());
        } catch (Exception e) {
            // log warning
        }
    }
}
```

**Tahmini Kazanç**: İlk request'lerde %30-40 daha hızlı response

---

## 7. Performans Metrikleri Tahminleri

### Throughput Projeksiyonları

| Senaryo | Tahmini Throughput | Dominant Darboğaz | CPU/Memory |
|---------|-------------------|-------------------|------------|
| Simple message (10 fields) | ~50K msg/s | JSON parsing | CPU-bound |
| Complex nested (5 levels) | ~10K msg/s | Builder allocation | Memory-bound |
| Large array (10K elements) | ~5K msg/s | Repeated field adds | Memory-bound |
| Map-heavy (1K entries) | ~8K msg/s | String valueOf | CPU+Memory |
| Well-known types | ~40K msg/s | Converter dispatch | CPU-bound |

**Not**: JVM: OpenJDK 17, CPU: 8-core modern processor varsayımıyla.

---

### Latency Projeksiyonları (p99)

| Senaryo | Tahmini Latency | GC Impact |
|---------|----------------|-----------|
| Simple message | <0.5ms | Düşük |
| Complex nested | 2-5ms | Orta |
| Large array | 10-20ms | Yüksek |
| Map-heavy | 5-10ms | Orta-Yüksek |

---

## 8. Monitoring & Observability Önerileri

### Eklenebilecek Metrikler

```java
// Micrometer integration örneği
@Component
public class ProtoJsonMetrics {

    private final MeterRegistry registry;

    // Cache effectiveness
    public void recordCacheHit(String cacheType) {
        registry.counter("protojson.cache.hit", "type", cacheType).increment();
    }

    public void recordCacheMiss(String cacheType) {
        registry.counter("protojson.cache.miss", "type", cacheType).increment();
    }

    // Processing time
    public void recordParseTime(long nanos, String messageType) {
        registry.timer("protojson.parse.duration", "type", messageType)
                .record(nanos, TimeUnit.NANOSECONDS);
    }

    // Message complexity
    public void recordNestingDepth(int depth) {
        registry.summary("protojson.message.depth").record(depth);
    }

    public void recordFieldCount(int count) {
        registry.summary("protojson.message.fields").record(count);
    }
}
```

### Önerilen Dashboard Metrikleri

1. **Cache Hit Ratios**:
   - FieldConverterRegistry hit/miss ratio
   - MetaRegistry hit/miss ratio
   - BuilderFactory hit/miss ratio

2. **Performance**:
   - Average parse time (by message type)
   - Average print time (by message type)
   - p50, p95, p99 latencies

3. **Resource Usage**:
   - Average nesting depth
   - Average field count per message
   - Converter lookup times

4. **Auto-Discovery**:
   - Registry scan duration
   - Discovered converter count
   - Scan frequency

---

## 9. GC Tuning Önerileri

### Heap Allocation Patterns

**Young Generation Pressure** (yüksek):
- Builder allocations
- String conversions (map keys)
- ArrayList resizing (repeated fields)

**Old Generation Pressure** (düşük):
- Cache'ler long-lived
- Metadata immutable

### Önerilen GC Settings

```bash
# G1GC (önerilen)
-XX:+UseG1GC
-XX:MaxGCPauseMillis=200
-XX:G1HeapRegionSize=16M
-XX:InitiatingHeapOccupancyPercent=45

# Heap sizing (örnek: 4GB total)
-Xms2G -Xmx4G

# Young generation
-XX:NewRatio=2  # Old/Young = 2:1
-XX:SurvivorRatio=8

# GC logging
-Xlog:gc*:file=gc.log:time,uptime,level,tags
```

### ZGC Alternatifi (Low-Latency Gerekirse)

```bash
-XX:+UseZGC
-XX:+ZGenerational
-Xms4G -Xmx4G  # ZGC için sabit heap size önerili
```

---

## 10. Profiling Checklist

### CPU Profiling

**Hedef Hotspot'lar**:
- [ ] `MetaRegistry.find()` - Field lookup
- [ ] `FieldConverterRegistry.find()` - Converter dispatch
- [ ] `ProtoJsonStreamer.merge()` - Main parse loop
- [ ] `String.valueOf()` - Map key conversion
- [ ] `Locale.ROOT` operations

**Araçlar**:
- async-profiler (önerilen)
- JFR (Java Flight Recorder)

### Memory Profiling

**Hedef Allocation Hotspot'ları**:
- [ ] `Message.Builder` allocations
- [ ] `String` allocations (field names, map keys)
- [ ] `ArrayList` internal array growth
- [ ] `HashMap` entries

**Araçlar**:
- JFR Memory Profiler
- YourKit Allocation Recorder

### Lock Contention

**Kontrol Edilecekler**:
- [ ] `ConcurrentHashMap.computeIfAbsent()` - Cache writes
- [ ] `FieldConverterRegistry` - Multi-thread access

**Araçlar**:
- JFR Lock Profiling
- async-profiler (lock mode)

---

## 11. Karşılaştırmalı Analiz

### Alternatif Kütüphaneler

| Kütüphane | Yaklaşım | Tahmini Performans | Trade-offs |
|-----------|----------|-------------------|------------|
| **Bu Proje** | Streaming + Cache | ⭐⭐⭐⭐ | Flexibility ↑, Raw speed ↓ |
| protobuf-java-util | Reflection-heavy | ⭐⭐⭐ | Simple, slower |
| gson + protobuf | Two-stage | ⭐⭐ | Memory overhead ↑ |
| Custom codegen | Generated code | ⭐⭐⭐⭐⭐ | Flexibility ↓, Speed ↑↑ |

**Sonuç**: Mevcut implementasyon **streaming + caching** dengesinde çok iyi. Codegen alternatifi daha hızlı ama esneklik kaybı var.

---

## Özet ve Öncelikler

### 🟢 Güçlü Yönler
1. ✅ Excellent caching strategy (converter, metadata, MethodHandle)
2. ✅ Streaming-based processing (memory efficient)
3. ✅ Thread-safe design (ConcurrentHashMap)
4. ✅ MethodHandle optimization (vs reflection)

### 🟡 İyileştirilebilir (Orta Öncelik)
1. ⚠️ Registry auto-discovery overhead → **Lazy + one-time discovery**
2. ⚠️ Field name lookup double-hash → **Fast path cache**
3. ⚠️ String allocations (map keys) → **String pooling**

### 🔴 İzlenmeli (Düşük Öncelik / Kabul Edilebilir)
1. 📊 Builder allocation (deep nesting) → **Protobuf API limitation**
2. 📊 Repeated field resizing → **Streaming limitation**

### Aksiyon Planı

**Faz 1 - Hızlı Kazançlar** (1-2 gün):
- [ ] Converter discovery caching
- [ ] Field lookup optimization
- [ ] JMH benchmark suite kurulumu

**Faz 2 - Measurement** (3-5 gün):
- [ ] Micrometer metrics ekleme
- [ ] Profiling (CPU + Memory)
- [ ] Gerçek-dünya load testi

**Faz 3 - Fine-tuning** (1 hafta):
- [ ] Profiling sonuçlarına göre optimization
- [ ] GC tuning
- [ ] Documentation güncellemesi

---

## Sonuç

Kod genel olarak **iyi optimize edilmiş** durumda. Temel caching ve streaming stratejileri doğru implementasyonu gösteriyor.

**Ana Öneriler**:
1. Registry auto-discovery'yi optimize et (en büyük kazanç burada)
2. JMH benchmarks ekle (ölçüm olmadan optimization körlemedir)
3. Production monitoring ekle (gerçek-dünya performance görmek için)

Mikro-optimizasyonlar mevcut ama **büyük kazançlar için profiling data'ya ihtiyaç var**. Önce ölç, sonra optimize et!

---

**Hazırlayan**: Claude (AI Assistant)
**Tarih**: 2026-01-10
**Versiyon**: 1.0
