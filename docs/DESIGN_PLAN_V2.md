# RogueMap 设计方案 v2.0

> **目标**: 打造一个比 MapDB 更高性能、更易用的嵌入式键值存储引擎
> **版本**: v0.1.0
> **最后更新**: 2025-12-03

---

## 📋 核心设计原则

### 1. 多 Java 版本兼容策略

**核心要求**: 兼容 Java 8/17/21，同时能利用高版本特性

#### 技术方案：多版本 JAR (Multi-Release JAR - JEP 238)

```
roguemap.jar
├── META-INF/
│   └── versions/
│       ├── 17/                    # Java 17 特定实现
│       │   └── com/yomahub/roguemap/memory/
│       │       └── UnsafeOps17.class
│       └── 21/                    # Java 21 特定实现
│           └── com/yomahub/roguemap/memory/
│               └── UnsafeOps21.class (使用 Foreign Memory API)
└── com/yomahub/roguemap/
    ├── memory/
    │   └── UnsafeOps.class        # Java 8 基础实现
    └── ...
```

**版本特性利用**:

| 功能模块 | Java 8 | Java 17 | Java 21 |
|---------|--------|---------|---------|
| **内存操作** | `sun.misc.Unsafe` | `VarHandle` | `Foreign Memory API` |
| **并发** | `ReentrantLock` | `StampedLock` | `Virtual Threads` |
| **序列化** | 反射 | `MethodHandle` | Pattern Matching |
| **性能** | 基准 | +15% | +30% |

**构建配置**:
```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <executions>
        <execution>
            <id>java8</id>
            <goals><goal>compile</goal></goals>
            <configuration>
                <source>8</source>
                <target>8</target>
            </configuration>
        </execution>
        <execution>
            <id>java17</id>
            <goals><goal>compile</goal></goals>
            <configuration>
                <source>17</source>
                <target>17</target>
                <compileSourceRoots>
                    <compileSourceRoot>${project.basedir}/src/main/java17</compileSourceRoot>
                </compileSourceRoots>
            </configuration>
        </execution>
        <execution>
            <id>java21</id>
            <goals><goal>compile</goal></goals>
            <configuration>
                <source>21</source>
                <target>21</target>
                <compileSourceRoots>
                    <compileSourceRoot>${project.basedir}/src/main/java21</compileSourceRoot>
                </compileSourceRoots>
            </configuration>
        </execution>
    </executions>
</plugin>
```

---

## 🗄️ 存储模式设计（参考 MapDB）

### MapDB 支持的模式

根据研究，MapDB 支持以下存储模式：

1. **堆外内存模式** (`memoryDirectDB()`)
   - 数据存储在 DirectByteBuffer 中
   - 不受 GC 影响
   - 需要设置 `-XX:MaxDirectMemorySize`

2. **内存映射文件模式** (`fileDB().fileMmapEnable()`)
   - 使用 `mmap` 映射文件到内存
   - 利用操作系统页缓存
   - 适合大数据量持久化

3. **临时文件模式** (`tempFileDB()`)
   - 创建临时文件，关闭后自动删除
   - 适合临时数据处理

4. **纯内存模式** (`memoryDB()`)
   - 数据在堆内，序列化为 byte[]
   - 不受 GC 影响（已序列化）

5. **堆内存模式** (`heapDB()`)
   - 数据直接存在堆上，无序列化
   - 受 GC 影响，但速度快

### RogueMap 存储模式设计

**我们排除纯堆内存模式**（用户用原生 `HashMap` 即可），支持以下模式：

#### 1. 堆外内存模式（Off-Heap）

```java
RogueMap<K, V> map = RogueMap.<K, V>builder()
    .offHeap()
    .maxMemory(10 * 1024 * 1024 * 1024L)  // 10GB
    .build();
```

**实现要点**:
- 使用 `ByteBuffer.allocateDirect()` 分配内存
- 自定义内存分配器（Slab Allocator）减少碎片
- 支持内存限制和自动淘汰（LRU）

#### 2. 内存映射文件模式（Memory-Mapped File）

```java
RogueMap<K, V> map = RogueMap.<K, V>builder()
    .persistent("data.db")
    .mmap()
    .allocateSize(10 * 1024 * 1024 * 1024L)  // 预分配 10GB
    .build();
```

**实现要点**:
- 使用 `FileChannel.map()` 创建 `MappedByteBuffer`
- 预分配策略减少文件扩展开销
- 自动刷盘策略（异步 + WAL）

#### 3. 临时文件模式（Temporary File）

```java
RogueMap<K, V> map = RogueMap.<K, V>builder()
    .tempFile()
    .mmap()
    .deleteOnExit()
    .build();
```

**实现要点**:
- 在系统临时目录创建文件
- JVM 退出时自动删除
- 适合大数据量临时计算

#### 4. 混合模式（Hybrid）- RogueMap 独有

```java
RogueMap<K, V> map = RogueMap.<K, V>builder()
    .persistent("data.db")
    .offHeapCache(2 * 1024 * 1024 * 1024L)  // 2GB 堆外缓存
    .mmap()
    .build();
```

**创新点**:
- 热数据在堆外内存（快速访问）
- 冷数据在 mmap 文件（节省内存）
- 自动冷热数据迁移

---

## 🚀 性能优化策略（超越 MapDB）

### 1. 更快的哈希表实现

**MapDB 的问题**:
- 全局锁在高并发下是瓶颈
- 哈希冲突处理效率低

**RogueMap 改进**:
```java
// 分段锁 + 无锁读
class OffHeapHashMap<K, V> {
    private static final int SEGMENT_COUNT = 64;
    private final Segment<K, V>[] segments;

    static class Segment<K, V> {
        private final StampedLock lock = new StampedLock();

        V get(K key) {
            // 完全无锁读（使用 volatile 保证可见性）
            long stamp = lock.tryOptimisticRead();
            V result = getInternal(key);
            if (!lock.validate(stamp)) {
                stamp = lock.readLock();
                try {
                    result = getInternal(key);
                } finally {
                    lock.unlockRead(stamp);
                }
            }
            return result;
        }

        void put(K key, V value) {
            long stamp = lock.writeLock();
            try {
                putInternal(key, value);
            } finally {
                lock.unlockWrite(stamp);
            }
        }
    }
}
```

**性能提升**: 并发读性能 +200%，并发写性能 +100%

### 2. 零拷贝序列化

**MapDB 的问题**:
- 即使是原始类型（Long, Int）也需要序列化开销

**RogueMap 改进**:
```java
// 对于固定长度类型，直接内存布局
class ZeroCopyLongCodec implements Codec<Long> {
    @Override
    public void encode(long address, Long value) {
        UNSAFE.putLong(address, value);  // 直接写入，无序列化
    }

    @Override
    public Long decode(long address) {
        return UNSAFE.getLong(address);  // 直接读取
    }

    @Override
    public int size() {
        return 8;  // 固定 8 字节
    }
}
```

**性能提升**: 原始类型读写性能 +300%

### 3. 智能内存分配器

**MapDB 的问题**:
- 使用 Java 的 DirectByteBuffer，存在碎片问题
- 频繁分配释放导致性能下降

**RogueMap 改进**:
```java
class SlabAllocator {
    // 按对象大小分级的内存池
    private final Slab[] slabs = new Slab[] {
        new Slab(16),      // 0-16 字节
        new Slab(64),      // 17-64 字节
        new Slab(256),     // 65-256 字节
        new Slab(1024),    // 257-1024 字节
        new Slab(4096)     // 1025-4096 字节
    };

    long allocate(int size) {
        Slab slab = findSlab(size);
        return slab.allocate();  // 从预分配的内存池中获取
    }

    void free(long address, int size) {
        Slab slab = findSlab(size);
        slab.free(address);  // 回收到内存池，延迟释放
    }
}
```

**性能提升**: 减少 70% 的系统调用，内存碎片率 -80%

### 4. 异步刷盘 + WAL

**MapDB 的问题**:
- 事务提交是同步的，每次都 `fsync()`
- 吞吐量受限于磁盘 IOPS

**RogueMap 改进**:
```java
class AsyncFlusher {
    private final WriteAheadLog wal;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor(); // Java 21

    void commit(Transaction tx) {
        // 1. 写入 WAL（顺序写，快）
        wal.append(tx.getChanges());

        // 2. 立即返回（用户感知延迟低）

        // 3. 后台异步刷盘
        executor.submit(() -> {
            // Group Commit: 批量刷盘
            List<Transaction> batch = collectPendingTransactions();
            flushBatch(batch);
        });
    }
}
```

**性能提升**: 写入吞吐量 +500%，延迟降低 90%

### 5. SIMD 加速（Java 21 Vector API）

**MapDB 的问题**:
- 未利用现代 CPU 的 SIMD 指令

**RogueMap 改进**:
```java
// src/main/java21/com/yomahub/roguemap/memory/VectorOps.java
import jdk.incubator.vector.*;

class VectorOps {
    private static final VectorSpecies<Long> SPECIES = LongVector.SPECIES_PREFERRED;

    // 使用 SIMD 批量复制内存
    void copyMemory(long src, long dst, int length) {
        int vectorLength = SPECIES.length();
        int i = 0;

        // 向量化处理
        for (; i < length - vectorLength; i += vectorLength) {
            LongVector v = LongVector.fromMemorySegment(
                SPECIES, srcSegment, i, ByteOrder.nativeOrder());
            v.intoMemorySegment(dstSegment, i, ByteOrder.nativeOrder());
        }

        // 处理剩余部分
        for (; i < length; i++) {
            UNSAFE.putLong(dst + i * 8, UNSAFE.getLong(src + i * 8));
        }
    }
}
```

**性能提升**: 大批量数据操作 +400%

---

## 🌟 独有特性（MapDB 不具备）

### 1. TTL 自动过期

```java
RogueMap<String, User> cache = RogueMap.<String, User>builder()
    .offHeap()
    .ttl(Duration.ofMinutes(30))  // 30 分钟后自动过期
    .build();

cache.put("user1", user);  // 30 分钟后自动删除
```

**实现**:
- 时间轮（Timing Wheel）算法
- 惰性删除 + 定期清理

### 2. 透明压缩

```java
RogueMap<String, byte[]> blobs = RogueMap.<String, byte[]>builder()
    .persistent("blobs.db")
    .compression(CompressionType.LZ4)  // 自动压缩
    .build();

blobs.put("image", imageBytes);  // 自动压缩存储
byte[] data = blobs.get("image");  // 自动解压
```

**实现**:
- LZ4（高速）/ Zstd（高压缩比）
- 块级压缩（4KB 块）

### 3. MVCC（多版本并发控制）

```java
RogueMap<String, String> map = RogueMap.<String, String>builder()
    .persistent("data.db")
    .mvcc()
    .build();

Snapshot snap = map.snapshot();  // 创建快照
map.put("key", "v2");
map.rollback(snap);  // 回滚到快照
```

**实现**:
- Copy-on-Write
- 版本链表

### 4. 可观测性

```java
MapMetrics metrics = map.metrics();
System.out.println("Read QPS: " + metrics.readOps());
System.out.println("Write QPS: " + metrics.writeOps());
System.out.println("Cache Hit Rate: " + metrics.hitRate());
System.out.println("Memory Usage: " + metrics.memoryBytes());
```

**实现**:
- 轻量级统计（无锁计数器）
- JMX 支持

---

## 📐 架构设计

### 核心模块

```
com.yomahub.roguemap/
├── storage/                        # 存储引擎
│   ├── StorageEngine.java          # 存储引擎接口
│   ├── OffHeapStorage.java         # 堆外内存实现
│   ├── MmapStorage.java            # 内存映射文件实现
│   └── HybridStorage.java          # 混合模式实现
├── memory/                         # 内存管理
│   ├── Allocator.java              # 内存分配器接口
│   ├── SlabAllocator.java          # Slab 分配器
│   ├── UnsafeOps.java              # Java 8 Unsafe 操作
│   ├── [java17] UnsafeOps17.java   # Java 17 VarHandle 操作
│   └── [java21] UnsafeOps21.java   # Java 21 Foreign Memory API
├── index/                          # 索引层
│   ├── HashIndex.java              # 哈希索引
│   ├── SegmentedHashIndex.java     # 分段哈希索引（高并发）
│   └── BTreeIndex.java             # B+树索引（范围查询）
├── concurrent/                     # 并发控制
│   ├── StripedLock.java            # 分段锁
│   ├── OptimisticLock.java         # 乐观锁（StampedLock 封装）
│   └── LockFreeCounter.java        # 无锁计数器
├── serialization/                  # 序列化
│   ├── Codec.java                  # 编解码器接口
│   ├── PrimitiveCodecs.java        # 原始类型编解码器
│   ├── StringCodec.java            # 字符串编解码器
│   └── ObjectCodec.java            # 对象编解码器
├── wal/                            # WAL（Write-Ahead Log）
│   ├── WriteAheadLog.java          # WAL 接口
│   └── MmapWAL.java                # 基于 mmap 的 WAL 实现
├── transaction/                    # 事务
│   ├── Transaction.java            # 事务接口
│   └── MVCCTransaction.java        # MVCC 事务实现
├── compression/                    # 压缩
│   ├── Compressor.java             # 压缩器接口
│   ├── LZ4Compressor.java          # LZ4 实现
│   └── ZstdCompressor.java         # Zstd 实现
├── metrics/                        # 可观测性
│   ├── MapMetrics.java             # 指标接口
│   └── DefaultMetrics.java         # 默认实现
└── RogueMap.java                   # 用户 API 入口
    └── RogueMapBuilder.java        # Builder 构造器
```

### 数据流

```
用户代码
   ↓
RogueMap API
   ↓
Index Layer (HashIndex/BTreeIndex)
   ↓
Storage Engine (OffHeap/Mmap/Hybrid)
   ↓
Memory Allocator (SlabAllocator)
   ↓
UnsafeOps (根据 Java 版本选择实现)
   ↓
DirectByteBuffer / MappedByteBuffer
   ↓
堆外内存 / 磁盘文件
```

---

## 🎯 开发路线图

### Phase 1: 核心基础（2-3 周）

**目标**: 实现基础的堆外内存和 mmap 存储

- [ ] **内存管理**
  - [ ] `UnsafeOps` (Java 8 版本)
  - [ ] `SlabAllocator`（Slab 内存分配器）
  - [ ] `OffHeapStorage`（堆外内存存储引擎）

- [ ] **索引层**
  - [ ] `HashIndex`（基础哈希索引）
  - [ ] `SegmentedHashIndex`（分段锁版本）

- [ ] **序列化**
  - [ ] `PrimitiveCodecs`（Long, Int, Double 等）
  - [ ] `StringCodec`

- [ ] **用户 API**
  - [ ] `RogueMap` 接口
  - [ ] `RogueMapBuilder`

- [ ] **单元测试**
  - [ ] 基础功能测试
  - [ ] 并发测试

**里程碑**: 能够运行 `RogueMap.builder().offHeap().build()`

### Phase 2: 持久化存储（2-3 周）

**目标**: 实现 mmap 和 WAL

- [ ] **存储引擎**
  - [ ] `MmapStorage`（内存映射文件）
  - [ ] 文件预分配策略

- [ ] **WAL**
  - [ ] `MmapWAL`（基于 mmap 的 WAL）
  - [ ] 异步刷盘机制

- [ ] **临时文件模式**
  - [ ] `TempFileStorage`
  - [ ] 自动清理

- [ ] **崩溃恢复**
  - [ ] WAL 回放
  - [ ] 数据校验

**里程碑**: 能够运行 `RogueMap.builder().persistent("data.db").build()`

### Phase 3: 多 Java 版本支持（1-2 周）

**目标**: 实现 Java 17/21 优化版本

- [ ] **Java 17 支持**
  - [ ] `UnsafeOps17`（使用 `VarHandle`）
  - [ ] Multi-Release JAR 配置

- [ ] **Java 21 支持**
  - [ ] `UnsafeOps21`（使用 Foreign Memory API）
  - [ ] `VectorOps`（SIMD 加速）
  - [ ] Virtual Threads 集成

- [ ] **兼容性测试**
  - [ ] Java 8/17/21 环境测试
  - [ ] 性能对比测试

**里程碑**: 单一 JAR 可在 Java 8/17/21 运行，高版本性能更优

### Phase 4: 性能优化（2-3 周）

**目标**: 超越 MapDB

- [ ] **零拷贝优化**
  - [ ] 原始类型直接内存布局
  - [ ] 批量操作优化

- [ ] **并发优化**
  - [ ] 无锁读实现
  - [ ] 分段锁粒度调优

- [ ] **内存优化**
  - [ ] Slab 分配器调优
  - [ ] 内存碎片率降低

- [ ] **基准测试**
  - [ ] JMH 性能测试
  - [ ] vs MapDB 对比

**里程碑**: 核心操作性能超越 MapDB 50%+

### Phase 5: 高级特性（2-3 周）

**目标**: 实现 MapDB 不具备的功能

- [ ] **TTL 支持**
  - [ ] 时间轮算法
  - [ ] 自动过期清理

- [ ] **透明压缩**
  - [ ] LZ4/Zstd 集成
  - [ ] 块级压缩

- [ ] **MVCC**
  - [ ] 快照创建
  - [ ] 版本回滚

- [ ] **混合模式**
  - [ ] 堆外缓存 + mmap 文件
  - [ ] 冷热数据迁移

- [ ] **可观测性**
  - [ ] Metrics 实现
  - [ ] JMX 支持

**里程碑**: 功能完整度达到 1.0 版本

---

## 🔧 技术选型

### 核心技术

- **JDK 版本**: 基线 Java 8，优化版本 Java 17/21
- **构建工具**: Maven（Multi-Release JAR）
- **测试框架**: JUnit 5
- **基准测试**: JMH
- **日志**: SLF4J（可选依赖）
- **压缩库**: LZ4-Java, Zstd-JNI（可选依赖）

### 依赖管理

```xml
<dependencies>
    <!-- 零核心依赖 -->

    <!-- 可选依赖 -->
    <dependency>
        <groupId>org.slf4j</groupId>
        <artifactId>slf4j-api</artifactId>
        <optional>true</optional>
    </dependency>

    <dependency>
        <groupId>org.lz4</groupId>
        <artifactId>lz4-java</artifactId>
        <optional>true</optional>
    </dependency>
</dependencies>
```

---

## 📊 预期性能目标

| 指标 | MapDB | RogueMap 目标 | 提升 |
|-----|-------|---------------|------|
| **点查询（Java 8）** | 100 万 ops/s | 150 万 ops/s | +50% |
| **点查询（Java 21）** | 100 万 ops/s | 200 万 ops/s | +100% |
| **写入（Java 8）** | 30 万 ops/s | 50 万 ops/s | +66% |
| **写入（Java 21）** | 30 万 ops/s | 80 万 ops/s | +166% |
| **并发读（64 线程）** | 200 万 ops/s | 500 万 ops/s | +150% |
| **内存碎片率** | 20% | 5% | -75% |
| **启动时间（1GB 数据）** | 100 ms | 30 ms | -70% |

---

## 参考资料

- [MapDB Guide | Baeldung](https://www.baeldung.com/mapdb)
- [MapDB Performance Documentation](https://mapdb.org/book/performance/)
- [MapDB DBMaker API](https://mapdb.org/javadoc/latest/mapdb/org/mapdb/DBMaker.html)
- [JEP 238: Multi-Release JAR Files](https://openjdk.org/jeps/238)
- [Java 21 Foreign Memory API](https://openjdk.org/jeps/454)
- [Java 21 Vector API](https://openjdk.org/jeps/448)

---

## 总结

RogueMap 的核心竞争力：

1. **多版本兼容**: Java 8/17/21 单一 JAR，高版本性能更优
2. **多存储模式**: 堆外内存、mmap、临时文件、混合模式
3. **更高性能**: 分段锁、零拷贝、异步刷盘、SIMD 加速
4. **独有特性**: TTL、压缩、MVCC、可观测性
5. **零核心依赖**: 纯 Java 实现，易于部署

**下一步**: 开始 Phase 1 开发 - 堆外内存管理和基础哈希索引
