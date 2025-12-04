# RogueMap 设计方案与开发计划

> **项目定位**: 高性能的 Java 堆外内存与持久化键值存储引擎
> **版本**: v0.1.0
> **最后更新**: 2025-12-03

---

## 📋 目录

1. [项目概述](#项目概述)
2. [核心定位与目标](#核心定位与目标)
3. [与 MapDB 的差异化竞争](#与-mapdb-的差异化竞争)
4. [技术架构设计](#技术架构设计)
5. [性能优化策略](#性能优化策略)
6. [API 设计理念](#api-设计理念)
7. [创新功能清单](#创新功能清单)
8. [开发路线图](#开发路线图)
9. [技术选型说明](#技术选型说明)

---

## 项目概述

**RogueMap** 是一个专注于堆外内存（Off-Heap）和磁盘持久化（Persistent）的高性能键值存储引擎。

### 设计原则

- **❌ 不支持堆内存模式**：原生 Java 集合（如 `HashMap`、`ConcurrentHashMap`）已经能很好地处理堆内存场景
- **✅ 专注堆外与持久化**：解决大数据量存储时的 GC 压力和磁盘持久化需求
- **✅ 极致性能**：通过零拷贝、无锁并发、智能内存管理等技术超越 MapDB
- **✅ 简洁 API**：提供比 MapDB 更简洁、更现代的 Java 21+ API

---

## 核心定位与目标

### 目标用户场景

1. **海量数据缓存**（几十 GB 到 TB 级别）
   - 需要避免 JVM GC 压力
   - 数据量超过物理内存限制

2. **高性能本地持久化**
   - 不需要复杂的 SQL 查询
   - 需要 ACID 事务保证
   - 应用重启后数据不丢失

3. **时间序列数据存储**
   - 日志、监控指标、事件流
   - 需要按时间范围查询
   - 自动过期（TTL）

4. **嵌入式数据库**
   - 单一 JAR 包，无外部依赖
   - 随应用一起打包发布
   - 零配置启动

### 非目标

- ❌ 不做分布式存储（单机场景）
- ❌ 不支持 SQL 查询（纯 KV 存储）
- ❌ 不做跨语言支持（Java Only）

---

## 与 MapDB 的差异化竞争

| 特性 | MapDB | RogueMap | 优势说明 |
|-----|-------|----------|---------|
| **API 简洁度** | ⭐⭐⭐ | ⭐⭐⭐⭐⭐ | Builder 模式 + 智能默认值 |
| **写入性能** | ⭐⭐⭐ | ⭐⭐⭐⭐⭐ | 无锁并发 + WAL + 异步刷盘 |
| **范围查询** | ⭐⭐ | ⭐⭐⭐⭐ | B+Tree 索引优化 |
| **TTL 支持** | ❌ | ✅ | 自动过期清理 |
| **压缩** | ❌ | ✅ | 透明压缩（LZ4/Zstd） |
| **时间序列优化** | ❌ | ✅ | 时间分片 + 范围查询 |
| **Snapshot/MVCC** | ❌ | ✅ | 多版本并发控制 |
| **可观测性** | ❌ | ✅ | 内置 Metrics |
| **Java 版本** | Java 8 | Java 21+ | Virtual Threads, Foreign Memory API |
| **维护状态** | 🟡 不活跃 | 🟢 新项目 | 活跃开发中 |

---

## 技术架构设计

### 包结构划分

```
com.yomahub.roguemap/
├── memory/                     # 堆外内存管理
│   ├── Allocator.java          # 内存分配器（Slab-based）
│   ├── DirectBuffer.java       # 堆外缓冲区封装
│   └── UnsafeOps.java          # Unsafe 工具类
├── storage/                    # 存储层
│   ├── MmapFile.java           # 内存映射文件
│   ├── WAL.java                # Write-Ahead Log
│   ├── PageCache.java          # 页缓存管理
│   └── Compactor.java          # 数据压缩整理
├── index/                      # 索引结构
│   ├── HashIndex.java          # 哈希索引（点查询）
│   ├── BTreeIndex.java         # B+树索引（范围查询）
│   └── LSMTree.java            # LSM-Tree（写优化）
├── concurrent/                 # 并发控制
│   ├── LockFreeHashMap.java    # 无锁哈希表
│   ├── StripedLock.java        # 分段锁
│   └── MVCC.java               # 多版本控制
├── serialization/              # 序列化
│   ├── ZeroCopyCodec.java      # 零拷贝序列化
│   └── Codecs.java             # 内置编解码器
└── api/                        # 用户 API
    ├── RogueMap.java           # 主入口 API
    ├── RogueMapBuilder.java    # Builder 构造器
    ├── MapConfig.java          # 配置类
    └── types/                  # 高级数据结构
        ├── RoguePriorityQueue.java
        ├── RogueBloomFilter.java
        └── RogueCounter.java
```

> **注意**: 项目采用单模块结构，所有代码在一个 Maven 项目中，最终构建为单一 JAR 包（`com.yomahub:roguemap`）

### 核心数据流

```
用户代码
   ↓
RogueMap API (com.yomahub.roguemap.api)
   ↓
索引层 (HashIndex / BTreeIndex)
   ↓
内存管理 (Allocator + DirectBuffer)
   ↓
存储层 (MmapFile + WAL)
   ↓
磁盘文件 (.rmap)
```

---

## 性能优化策略

### 1. 零拷贝序列化（Zero-Copy Serialization）

**问题**: MapDB 在某些场景仍需要序列化/反序列化对象

**解决方案**:
- 对于固定长度类型（`long`, `int`, `double`），直接内存布局
- 使用 `sun.misc.Unsafe` 或 Java 21 的 `VarHandle` 直接读写内存
- 无需经过 Java 对象分配，直接从堆外内存读取

**示例**:
```java
// 传统方式（有序列化开销）
byte[] bytes = serialize(value);
buffer.put(bytes);

// 零拷贝方式（直接写入）
long address = allocator.allocate(8);
UNSAFE.putLong(address, value);
```

### 2. 无锁并发控制（Lock-Free Concurrency）

**问题**: MapDB 的全局写锁在高并发下是瓶颈

**解决方案**:
- **读操作**: 完全无锁（基于 volatile + 内存屏障）
- **写操作**: CAS（Compare-And-Swap）+ 分段锁
- **参考**: `ConcurrentHashMap` 的 Segment 设计，但针对堆外内存优化

**核心技术**:
```java
// 分段锁（降低锁竞争）
int segment = hash(key) % NUM_SEGMENTS;
locks[segment].lock();
try {
    // 写操作
} finally {
    locks[segment].unlock();
}
```

### 3. 智能内存分配器（Slab Allocator）

**问题**: 频繁的内存分配/释放导致碎片化

**解决方案**:
- 按对象大小分配不同的内存池（类似 jemalloc）
- 预分配大块内存，避免频繁系统调用
- 延迟释放（Batch Free）

**内存池设计**:
```
Slab 1: 0-64 字节对象
Slab 2: 64-256 字节对象
Slab 3: 256-1024 字节对象
Slab 4: 大对象（直接分配）
```

### 4. 异步持久化（WAL + Background Flush）

**问题**: MapDB 的事务提交是同步的，性能受限于磁盘 IO

**解决方案**:
- **Write-Ahead Log (WAL)**: 写入先记录到 WAL（顺序写）
- **异步刷盘**: 后台线程批量刷盘
- **Group Commit**: 多个事务合并提交（减少 fsync 调用）

**写入流程**:
```
1. 写入 WAL（内存缓冲 + 顺序写磁盘，快）
2. 立即返回（用户感知延迟低）
3. 后台线程异步将 WAL 应用到数据文件
4. Checkpoint 后删除旧 WAL
```

### 5. 内存映射文件优化（Mmap Tuning）

**技术点**:
- 使用 `MappedByteBuffer` 进行文件读写
- 利用操作系统的 Page Cache（比 Java 自己管理缓存更高效）
- 预读（Readahead）优化顺序扫描
- `madvise` 提示内核内存访问模式

---

## API 设计理念

### 设计目标

1. **简洁性**: 最常见的用法只需一行代码
2. **类型安全**: 利用 Java 泛型，编译期检查
3. **智能默认值**: 开箱即用，无需复杂配置
4. **流式 API**: Builder 模式，链式调用

### API 对比示例

#### MapDB 的写法（繁琐）

```java
DB db = DBMaker.fileDB("data.db")
    .fileMmapEnable()
    .transactionEnable()
    .closeOnJvmShutdown()
    .make();

ConcurrentMap<String, String> map = db.hashMap("myMap")
    .keySerializer(Serializer.STRING)
    .valueSerializer(Serializer.STRING)
    .createOrOpen();

map.put("key", "value");
db.commit();
db.close();
```

#### RogueMap 的写法（简洁）

```java
// 方式 1: 极简模式（自动推断序列化器）
RogueMap<String, String> map = RogueMap.create("data.db");
map.put("key", "value");
// 自动刷盘，无需手动 commit

// 方式 2: Builder 模式（高级配置）
RogueMap<String, User> users = RogueMap.<String, User>builder()
    .persistent("users.db")
    .ttl(Duration.ofDays(7))
    .compression(CompressionType.LZ4)
    .build();
```

### 核心 API 类设计

#### `RogueMap<K, V>` 接口

```java
public interface RogueMap<K, V> extends ConcurrentMap<K, V>, AutoCloseable {

    // === 基础操作（继承自 ConcurrentMap）===
    V get(K key);
    V put(K key, V value);
    V remove(K key);

    // === 范围查询（RogueMap 特有）===
    Stream<Entry<K, V>> range(K from, K to);
    Stream<Entry<K, V>> prefix(K prefix);

    // === 批量操作（性能优化）===
    void putAll(Map<K, V> entries);  // 批量写入（一次性刷盘）
    Map<K, V> getAll(Collection<K> keys);  // 批量读取

    // === 事务支持 ===
    Snapshot snapshot();
    void rollback(Snapshot snapshot);

    // === 生命周期管理 ===
    void flush();  // 强制刷盘
    void compact();  // 压缩整理
    void close();  // 关闭并释放资源

    // === 统计信息 ===
    MapMetrics metrics();
}
```

#### `RogueMapBuilder<K, V>` 构造器

```java
public class RogueMapBuilder<K, V> {

    // 持久化配置
    public RogueMapBuilder<K, V> persistent(String path);
    public RogueMapBuilder<K, V> offHeap();  // 纯堆外内存（不持久化）

    // 性能配置
    public RogueMapBuilder<K, V> cacheSize(long bytes);
    public RogueMapBuilder<K, V> asyncFlush(boolean enable);

    // 高级特性
    public RogueMapBuilder<K, V> ttl(Duration duration);
    public RogueMapBuilder<K, V> compression(CompressionType type);
    public RogueMapBuilder<K, V> timeSeriesMode();

    // 序列化器（可选，默认自动推断）
    public RogueMapBuilder<K, V> keySerializer(Codec<K> codec);
    public RogueMapBuilder<K, V> valueSerializer(Codec<V> codec);

    // 构建
    public RogueMap<K, V> build();
}
```

---

## 创新功能清单

### 1. 时间序列优化（Time-Series Optimization）

**使用场景**: 日志、监控指标、事件流

**核心功能**:
- 自动 TTL（Time-To-Live）过期清理
- 按时间戳范围查询
- 时间分片存储（Partitioning by Time）

**API 示例**:
```java
RogueMap<Instant, Event> events = RogueMap.<Instant, Event>builder()
    .persistent("events.db")
    .ttl(Duration.ofHours(24))  // 24 小时后自动删除
    .timeSeriesMode()
    .build();

// 查询最近 1 小时
Instant now = Instant.now();
events.range(now.minus(Duration.ofHours(1)), now)
      .forEach(entry -> System.out.println(entry.getValue()));
```

### 2. 列式存储（Columnar Storage）

**使用场景**: 结构化数据，只查询部分字段

**核心优势**:
- 每个字段独立存储
- 只读取需要的列，节省内存
- 列级压缩（相同列数据相似度高，压缩比更好）

**API 示例**:
```java
record User(long id, String name, int age, String email) {}

RogueMap<Long, User> users = RogueMap.<Long, User>builder()
    .persistent("users.db")
    .columnarStorage()  // 启用列式存储
    .build();

// 只查询 age 字段（不反序列化 name、email）
users.selectColumn("age")
     .where(age -> age > 18)
     .forEach(System.out::println);
```

### 3. 透明压缩（Transparent Compression）

**支持的压缩算法**:
- **LZ4**: 高速压缩（适合低延迟场景）
- **Zstd**: 高压缩比（适合存储空间受限）
- **AUTO**: 自动检测数据特征选择算法

**API 示例**:
```java
RogueMap<String, byte[]> blobs = RogueMap.<String, byte[]>builder()
    .persistent("blobs.db")
    .compression(CompressionType.AUTO)  // 自动选择
    .build();

// 写入自动压缩，读取自动解压
blobs.put("image1", imageBytes);
```

### 4. Snapshot 与 MVCC

**使用场景**: 数据备份、一致性读、回滚操作

**核心技术**: Multi-Version Concurrency Control (MVCC)

**API 示例**:
```java
map.put("key", "v1");
Snapshot snap = map.snapshot();  // 创建快照

map.put("key", "v2");
map.put("key", "v3");

// 回滚到快照版本
map.rollback(snap);
assert "v1".equals(map.get("key"));
```

### 5. 多数据结构支持

#### 优先队列（Priority Queue）
```java
RoguePriorityQueue<Task> queue = RogueQueue.<Task>priority()
    .persistent("tasks.db")
    .comparator(Comparator.comparing(Task::priority))
    .build();

queue.offer(new Task("high", 1));
queue.offer(new Task("low", 10));
assert "high".equals(queue.poll().name());
```

#### 布隆过滤器（Bloom Filter）
```java
RogueBloomFilter filter = RogueBloomFilter.create("seen.db", 1_000_000_000L);
filter.add("url1");
assert filter.mightContain("url1");
assert !filter.mightContain("url2");
```

#### 滑动窗口计数器（Sliding Window Counter）
```java
RogueCounter counter = RogueCounter.slidingWindow("metrics.db", Duration.ofMinutes(5));
counter.increment("requests");
long count = counter.get("requests");  // 最近 5 分钟的计数
```

### 6. 可观测性（Observability）

**内置指标**:
- 读写 QPS
- 缓存命中率
- 内存使用量
- 磁盘 IO 统计

**API 示例**:
```java
MapMetrics metrics = map.metrics();
System.out.println("Read QPS: " + metrics.readOpsPerSec());
System.out.println("Write QPS: " + metrics.writeOpsPerSec());
System.out.println("Cache Hit Rate: " + metrics.cacheHitRate());
System.out.println("Memory Usage: " + metrics.memoryUsageMB() + " MB");
```

### 7. 智能预热（Warm-up）

**使用场景**: 应用重启后快速恢复性能

**策略**:
- `RECENT_KEYS`: 预加载最近访问的键
- `FREQUENT_KEYS`: 预加载访问频率最高的键
- `ALL`: 预加载所有数据到 Page Cache

**API 示例**:
```java
RogueMap<String, String> map = RogueMap.<String, String>builder()
    .persistent("cache.db")
    .warmUp(WarmUpStrategy.FREQUENT_KEYS)
    .build();
```

### 8. 事件订阅（Change Data Capture）

**使用场景**: 监听数据变化，触发后续操作

**API 示例**:
```java
map.subscribe(event -> {
    switch (event.type()) {
        case PUT -> System.out.println("Key added: " + event.key());
        case REMOVE -> System.out.println("Key removed: " + event.key());
        case UPDATE -> System.out.println("Key updated: " + event.key());
    }
});
```

---

## 开发路线图

### Phase 1: MVP（最小可行产品）[预计 2-3 个月]

**目标**: 实现基础功能，验证核心技术方案

- ✅ 项目结构搭建（Maven 多模块）
- [ ] 堆外内存管理
  - [ ] `Allocator` 内存分配器
  - [ ] `DirectBuffer` 封装
  - [ ] `UnsafeOps` 工具类
- [ ] 哈希索引实现（`HashIndex`）
  - [ ] 无锁读
  - [ ] 分段锁写
- [ ] 持久化层（Mmap）
  - [ ] `MmapFile` 文件映射
  - [ ] 基础的序列化/反序列化
- [ ] 用户 API
  - [ ] `RogueMap` 接口
  - [ ] `RogueMapBuilder`
- [ ] 单元测试
- [ ] 基准测试（vs MapDB, ConcurrentHashMap）

**里程碑**: 能够运行基础的 put/get 操作，性能不低于 MapDB

### Phase 2: 性能优化 [预计 2-3 个月]

**目标**: 实现关键性能优化，超越 MapDB

- [ ] WAL（Write-Ahead Log）
  - [ ] 异步刷盘
  - [ ] Group Commit
- [ ] 智能内存分配器（Slab Allocator）
- [ ] 零拷贝序列化优化
  - [ ] 原始类型（Long, Int, Double）
  - [ ] 定长字符串
- [ ] B+Tree 索引（范围查询）
- [ ] 压缩整理（Compaction）
- [ ] 性能基准测试
  - [ ] 吞吐量测试（QPS）
  - [ ] 延迟测试（P50, P99, P999）
  - [ ] 内存使用测试

**里程碑**: 核心操作性能超越 MapDB 20%-50%

### Phase 3: 高级特性 [预计 3-4 个月]

**目标**: 实现差异化功能

- [ ] TTL（Time-To-Live）
  - [ ] 自动过期清理
  - [ ] 惰性删除 + 定期清理
- [ ] 时间序列优化
  - [ ] 时间分片
  - [ ] 范围查询优化
- [ ] Snapshot + MVCC
  - [ ] 快照创建
  - [ ] 回滚操作
- [ ] 透明压缩
  - [ ] LZ4 集成
  - [ ] Zstd 集成
  - [ ] 自动选择算法
- [ ] Metrics 与可观测性
  - [ ] 内置统计
  - [ ] JMX 支持

**里程碑**: 功能完整度达到 1.0 正式版水平

### Phase 4: 生态扩展 [持续进行]

**目标**: 完善生态，提升易用性

- [ ] 更多数据结构
  - [ ] `RoguePriorityQueue`
  - [ ] `RogueBloomFilter`
  - [ ] `RogueCounter`
- [ ] CLI 工具（roguemap-tools）
  - [ ] 查看数据库文件
  - [ ] 修复损坏文件
  - [ ] 导出/导入
- [ ] Spring Boot Starter
- [ ] 监控面板（Web UI）
- [ ] 文档与示例
  - [ ] 用户手册
  - [ ] API 文档（Javadoc）
  - [ ] 性能调优指南

**里程碑**: 成为 Java 生态中知名的嵌入式存储方案

---

## 技术选型说明

### 核心技术栈

| 技术 | 选型 | 理由 |
|-----|------|------|
| **JDK 版本** | Java 21+ | Virtual Threads、Foreign Memory API、Pattern Matching |
| **构建工具** | Maven | 生态成熟，企业友好 |
| **测试框架** | JUnit 5 | 现代化测试框架 |
| **基准测试** | JMH | 官方推荐的微基准测试工具 |
| **日志** | SLF4J | 轻量级日志门面 |
| **压缩库** | LZ4-Java, Zstd-JNI | 高性能压缩算法 |

### 零依赖原则

**roguemap-core** 模块保持零依赖（除了 JDK）:
- ✅ 部署简单（单一 JAR）
- ✅ 避免依赖冲突
- ✅ 启动速度快
- ✅ GraalVM Native Image 友好

**可选依赖**（仅在特定模块使用）:
- `roguemap-benchmark`: JMH
- `roguemap-tools`: Picocli（命令行解析）

### 内存安全

虽然使用 `Unsafe` 进行内存操作，但通过以下手段保证安全性:
1. **封装 Unsafe 操作**：不暴露给用户代码
2. **边界检查**：每次内存访问前检查越界
3. **生命周期管理**：明确的内存分配/释放
4. **完善的单元测试**：覆盖边界条件

---

## 性能目标

### 与 MapDB 对比目标

| 指标 | MapDB | RogueMap 目标 | 提升 |
|-----|-------|---------------|------|
| **点查询（QPS）** | 100 万/秒 | 150 万/秒 | +50% |
| **写入（QPS）** | 30 万/秒 | 50 万/秒 | +66% |
| **范围查询（10K 条）** | 50 ms | 20 ms | -60% |
| **启动时间（1GB 数据）** | 100 ms | 50 ms | -50% |
| **内存占用** | 基准 | -20% | 更紧凑 |

### 硬件基准

测试环境:
- CPU: 8 核 3.0 GHz
- 内存: 32 GB
- 磁盘: NVMe SSD
- OS: Linux / macOS

---

## 开源协议与社区

- **开源协议**: Apache License 2.0（与 MapDB 相同）
- **代码托管**: GitHub
- **文档**: Markdown + GitHub Pages
- **问题跟踪**: GitHub Issues
- **持续集成**: GitHub Actions

---

## 总结

RogueMap 的核心竞争力:

1. **更高性能**: 无锁并发 + WAL + 零拷贝
2. **更简洁 API**: Builder 模式 + 智能默认值
3. **创新功能**: TTL、时间序列、MVCC、压缩、多数据结构
4. **现代 Java**: Java 21+ 特性（Virtual Threads, Foreign Memory API）
5. **零依赖**: 单一 JAR，易于部署

**下一步行动**: 开始 Phase 1 开发，实现 MVP 版本。
