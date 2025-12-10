# RogueMap

[![License](https://img.shields.io/badge/license-Apache%202-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-8%2B-orange.svg)](https://www.oracle.com/java/)
[![Version](https://img.shields.io/badge/version-0.1.0--SNAPSHOT-green.svg)](https://github.com/bryan31/RogueMap)

**RogueMap** 是一个高性能的嵌入式键值存储引擎，支持堆外内存和持久化存储。目标是打造一个比 MapDB 更快、更易用的 Java 存储解决方案。

## 🎯 项目介绍

RogueMap 是一个专为 Java 应用设计的高性能键值存储引擎，提供了灵活的存储模式和极致的性能表现。与传统的 HashMap 相比，RogueMap 将数据存储在 JVM 堆外内存或内存映射文件中，大幅降低 GC 压力的同时提供更高的吞吐量。

### 核心优势

- **极致性能**: 写入速度比 MapDB 快 4.4 倍，读取速度快 15.9 倍
- **低内存占用**: 堆外内存模式减少 87% 堆内存使用
- **灵活存储**: 支持堆外内存（OffHeap）和 映射映射文件（Mmap Persist）以及 临时文件（Mmap Temp File）三种模式
- **易于使用**: 简洁的 Builder API，类型安全，自动资源管理

### 适用场景

- 高性能键值缓存
- 大数据量临时处理
- 实时数据分析
- 嵌入式键值存储
- 持久化配置管理

## ✨ 特性

- ✅ **多种存储模式** - 支持 堆外内存、内存映射文件、内存映射临时文件 三种模式
- ✅ **持久化支持** - Mmap 模式支持数据持久化到磁盘，支持自动恢复
- ✅ **临时文件模式** - 支持自动清理的临时文件存储
- ✅ **零拷贝序列化** - 原始类型直接内存布局，无序列化开销
- ✅ **高并发支持** - 分段锁设计（64 个段），StampedLock 乐观锁优化
- ✅ **智能内存分配** - Slab Allocator 减少内存碎片
- ✅ **多种索引结构** - 支持 HashIndex、SegmentedHashIndex、LongPrimitiveIndex、IntPrimitiveIndex
- ✅ **类型安全** - 泛型支持，编译时类型检查
- ✅ **零依赖** - 核心库无第三方依赖

## 🚀 快速开始

### Maven 依赖

```xml
<dependency>
    <groupId>com.yomahub</groupId>
    <artifactId>roguemap</artifactId>
    <version>1.0.0-Beta</version>
</dependency>
```

### 基本使用

#### OffHeap 模式（堆外内存）

```java
import com.yomahub.roguemap.RogueMap;
import com.yomahub.roguemap.serialization.PrimitiveCodecs;
import com.yomahub.roguemap.serialization.StringCodec;

// 创建一个 String -> Long 的堆外内存 Map
try (RogueMap<String, Long> map = RogueMap.<String, Long>offHeap()
        .keyCodec(StringCodec.INSTANCE)
        .valueCodec(PrimitiveCodecs.LONG)
        .maxMemory(100 * 1024 * 1024) // 100MB
        .build()) {

    // 存储数据
    map.put("user1", 1000L);
    map.put("user2", 2000L);

    // 读取数据
    Long score = map.get("user1");
    System.out.println("Score: " + score);

    // 更新数据
    map.put("user1", 1500L);

    // 删除数据
    map.remove("user2");

    // 检查存在
    boolean exists = map.containsKey("user1");

    // 获取大小
    int size = map.size();
}
```

#### Mmap 临时文件模式

```java
// 自动创建临时文件，JVM 关闭后自动删除
RogueMap<Long, Long> tempMap = RogueMap.<Long, Long>mmap()
    .temporary()
    .allocateSize(500 * 1024 * 1024L)
    .keyCodec(PrimitiveCodecs.LONG)
    .valueCodec(PrimitiveCodecs.LONG)
    .build();
```

#### Mmap 模式（持久化存储）

```java
// 第一次：创建并写入数据
RogueMap<String, Long> map1 = RogueMap.<String, Long>mmap()
    .persistent("data/scores.db")
    .allocateSize(1024 * 1024 * 1024L)  // 1GB
    .keyCodec(StringCodec.INSTANCE)
    .valueCodec(PrimitiveCodecs.LONG)
    .build();

map1.put("alice", 100L);
map1.put("bob", 200L);
map1.flush();  // 刷新到磁盘
map1.close();

// 第二次：重新打开并恢复数据
RogueMap<String, Long> map2 = RogueMap.<String, Long>mmap()
    .persistent("data/scores.db")
    .keyCodec(StringCodec.INSTANCE)
    .valueCodec(PrimitiveCodecs.LONG)
    .build();

long score = map2.get("alice");  // 100L（从磁盘恢复）
map2.close();
```

### 支持的数据类型

RogueMap 提供了零拷贝的原始类型编解码器：

```java
// Long 类型（高性能）
RogueMap<Long, Long> longMap = RogueMap.<Long, Long>offHeap()
    .keyCodec(PrimitiveCodecs.LONG)
    .valueCodec(PrimitiveCodecs.LONG)
    .build();

// Integer 类型
RogueMap<Integer, Integer> intMap = RogueMap.<Integer, Integer>offHeap()
    .keyCodec(PrimitiveCodecs.INTEGER)
    .valueCodec(PrimitiveCodecs.INTEGER)
    .build();

// String 类型
RogueMap<String, String> stringMap = RogueMap.<String, String>offHeap()
    .keyCodec(StringCodec.INSTANCE)
    .valueCodec(StringCodec.INSTANCE)
    .build();

// 混合类型
RogueMap<String, Double> mixedMap = RogueMap.<String, Double>offHeap()
    .keyCodec(StringCodec.INSTANCE)
    .valueCodec(PrimitiveCodecs.DOUBLE)
    .build();
```

**支持的原始类型**：`Long`, `Integer`, `Double`, `Float`, `Short`, `Byte`, `Boolean`

如果是对象类型，RogueMap也提供了对象的编码解析器：

```java
// 对象类型
RogueMap<Long, Long> longMap = RogueMap.<String, YourObject>offHeap()
                .keyCodec(StringCodec.INSTANCE)
                .valueCodec(KryoObjectCodec.create(YourObject.class))
                .build();
```

### 索引选择

RogueMap 提供了多种索引策略，适用于不同场景：

```java
// 场景1: 高并发读写，推荐分段索引（默认）
RogueMap<String, String> concurrentMap = RogueMap.<String, String>offHeap()
    .keyCodec(StringCodec.INSTANCE)
    .valueCodec(StringCodec.INSTANCE)
    .segmentedIndex(64)  // 64个段，减少锁竞争
    .build();

// 场景2: 内存敏感，Long键，推荐原始索引
RogueMap<Long, Long> memoryOptimized = RogueMap.<Long, Long>offHeap()
    .keyCodec(PrimitiveCodecs.LONG)
    .valueCodec(PrimitiveCodecs.LONG)
    .primitiveIndex()  // 节省81%内存
    .build();

// 场景3: 简单场景，推荐基础索引
RogueMap<String, Integer> simpleMap = RogueMap.<String, Integer>offHeap()
    .keyCodec(StringCodec.INSTANCE)
    .valueCodec(PrimitiveCodecs.INTEGER)
    .basicIndex()
    .build();
```

### 配置选项

#### OffHeap 模式配置

```java
RogueMap<K, V> map = RogueMap.<K, V>offHeap()
    // 必需配置
    .keyCodec(keyCodec)           // 键的编解码器
    .valueCodec(valueCodec)       // 值的编解码器

    // 可选配置
    .maxMemory(1024 * 1024 * 1024) // 最大内存 (默认 1GB)
        
    // 以下三种配置一种即可，或者不配置
    .basicIndex()                 // 使用基础索引
    .segmentedIndex(64)           // 使用分段索引 (默认)
    .primitiveIndex()             // 使用原始索引（仅Long/Integer键）
        
    .build();
```

#### Mmap 临时文件模式配置

```java
RogueMap<K, V> map = RogueMap.<K, V>mmap()
    // 必须配置
    .temporary()                  // 临时文件模式
    .keyCodec(keyCodec)           // 键的编解码器
    .valueCodec(valueCodec)       // 值的编解码器

    // 可选配置
    .allocateSize(10L * 1024 * 1024 * 1024) // 预分配大小 (默认 10GB)
        
    // 以下三种配置一种即可，或者不配置
    .basicIndex()                 // 使用基础索引
    .segmentedIndex(64)           // 使用分段索引 (默认)
    .primitiveIndex()             // 使用原始索引（仅Long/Integer键）
        
    .build();
```

#### Mmap 文件持久化模式配置

```java
RogueMap<K, V> map = RogueMap.<K, V>mmap()
    // 必需配置
    .persistent("data.db")        // 持久化文件路径
    .keyCodec(keyCodec)           // 键的编解码器
    .valueCodec(valueCodec)       // 值的编解码器

    // 可选配置
    .allocateSize(10L * 1024 * 1024 * 1024) // 预分配大小 (默认 10GB)

    // 以下三种配置一种即可，或者不配置
    .basicIndex()                 // 使用基础索引
    .segmentedIndex(64)           // 使用分段索引 (默认)
    .primitiveIndex()             // 使用原始索引（仅Long/Integer键）
        
    .build();
```

## 📊 性能测试

在 MacBook Pro (M3, 18GB) 上测试 100 万条 Long->Object 数据的结果：

### RogueMap 多模式性能对比

| 模式 | 写入 | 读取 | 写吞吐(ops/s) | 读吞吐(ops/s) | 堆内存(MB) |
|------|------|------|---------------|---------------|------------|
| HashMap模式 | 611ms | 463ms | 1,636,661 | 2,159,827 | 304.04 |
| OffHeap模式 | 658ms | 251ms | 1,519,756 | 3,984,063 | 40.46 |
| Mmap临时文件 | 629ms | 212ms | 1,589,825 | 4,716,981 | 40.13 |
| **Mmap持久化** | **547ms** | **195ms** | **1,828,153** | **5,128,205** | **40.01** |

**关键发现**:
- **Mmap持久化模式**综合性能最优
- **堆外内存模式**减少 87% 堆内存占用
- **读性能**比 HashMap 提升 2.4 倍

### RogueMap vs MapDB 性能对比

| 实现方式 | 写入 | 读取 | 写吞吐(ops/s) | 读吞吐(ops/s) |
|----------|------|------|---------------|---------------|
| RogueMap Mmap持久化 | **632ms** | **202ms** | **1,582,278** | **4,950,495** |
| MapDB 持久化 | 2,764ms | 3,207ms | 361,794 | 311,817 |
| **性能提升** | **4.4x** | **15.9x** | **4.4x** | **15.9x** |

**核心优势**:
- 写入速度比 MapDB 快 **4.4 倍**
- 读取速度比 MapDB 快 **15.9 倍**
- 极致性能 + 持久化兼得

详细测试报告请查看 [性能测试文档](docs/PERFORMANCE_BENCHMARK.md)

### 运行性能测试

```bash
# 运行 RogueMap 多模式对比
mvn test -Dtest=MemoryUsageComparisonTest

# 运行 RogueMap vs MapDB 对比
mvn test -Dtest=RogueMapVsMapDBComparisonTest

# 运行所有性能测试
mvn test -Dtest=*ComparisonTest
```

## 🏗️ 架构设计

```
RogueMap API
   ↓
Index Layer (HashIndex/SegmentedHashIndex/PrimitiveIndex)
   ↓
Storage Engine (OffHeapStorage/MmapStorage)
   ↓
Memory Allocator (SlabAllocator/MmapAllocator)
   ↓
UnsafeOps (Java 8 Unsafe)
   ↓
Off-Heap Memory / Memory-Mapped Files
```

### 核心模块

- **RogueMap** - 主类，提供 OffHeapBuilder 和 MmapBuilder 两个构建器
- **index** - 索引层
  - `HashIndex` - 基础哈希索引，基于 ConcurrentHashMap
  - `SegmentedHashIndex` - 分段哈希索引，64 个段 + StampedLock 乐观锁
  - `LongPrimitiveIndex` - Long 键原始数组索引，节省 81% 内存
  - `IntPrimitiveIndex` - Integer 键原始数组索引
- **storage** - 存储引擎
  - `OffHeapStorage` - 堆外内存存储
  - `MmapStorage` - 内存映射文件存储
- **memory** - 内存管理
  - `SlabAllocator` - Slab 分配器，7 个大小类别（16B 到 16KB）
  - `MmapAllocator` - 内存映射文件分配器，支持超过 2GB 的大文件
  - `UnsafeOps` - 底层 Unsafe API 操作
- **serialization** - 序列化层
  - `PrimitiveCodecs` - 原始类型零拷贝编解码器
  - `StringCodec` - String 编解码器
  - `KryoObjectCodec` - Kryo 对象序列化编解码器（可选）

### 内存管理机制

#### SlabAllocator（堆外内存）

- **分配策略**: 7 个 size class (16B, 64B, 256B, 1KB, 4KB, 16KB)
- **块大小**: 1MB
- **优化**: 空闲列表重用，负载因子自适应扩容
- **内存节省**: 相比 HashMap 节省 87% 堆内存

#### MmapAllocator（文件映射）

- **特点**: 使用 MappedByteBuffer 将文件映射到内存
- **大文件支持**: 单个分段最大 2GB，自动分多段处理
- **并发安全**: CAS 操作分配偏移量
- **双模式**: 支持持久化和临时文件

### 高并发支持

#### SegmentedHashIndex 并发机制

- **分段数量**: 64 个独立段
- **锁策略**: 每个段独立的 StampedLock
- **乐观读**: 读操作优先使用乐观读，验证失败时降级为读锁
- **性能**: 高并发场景下读性能提升 15 倍

#### LongPrimitiveIndex 并发机制

- **实现**: 原始数组 (long[] keys, long[] addresses, int[] sizes)
- **锁策略**: StampedLock 乐观读
- **内存优化**: 节省 81% 内存

## 📖 文档

- [性能测试报告](docs/PERFORMANCE_BENCHMARK.md)

## 🔧 构建项目

```bash
# 编译
mvn clean compile

# 运行测试
mvn test

# 运行特定测试
mvn test -Dtest=OffHeapFunctionalTest
mvn test -Dtest=MmapFunctionalTest
```

## 📝 系统要求

- Java 8
- Maven 3.6+

## ⚠️ 注意事项

1. **Unsafe API 警告** - 本项目使用 `sun.misc.Unsafe` API，这是内部 API，可能在未来版本中被移除。以后将添加 Java 17/21 的替代实现。

2. **内存管理** - 请确保正确关闭 RogueMap 实例以释放堆外内存：
   ```java
   try (RogueMap<K, V> map = ...) {
       // 使用 map
   } // 自动关闭，释放资源
   ```

3. **内存限制** - 堆外内存受 `-XX:MaxDirectMemorySize` JVM 参数限制，建议根据实际需求设置：
   ```bash
   java -XX:MaxDirectMemorySize=2g -jar your-app.jar
   ```

4. **文件大小** - Mmap 模式的 `allocateSize()` 会立即占用磁盘空间，请根据实际需求设置

5. **并发安全** - RogueMap 是线程安全的，支持高并发读写

## 🤝 贡献

欢迎提交 Issue 和 Pull Request！

## 📄 许可证

本项目采用 [Apache License 2.0](LICENSE) 许可证。

## 🙏 致谢

本项目的设计灵感来自于：
- [MapDB](https://github.com/jankotek/mapdb) - 优秀的嵌入式数据库
- [Chronicle Map](https://github.com/OpenHFT/Chronicle-Map) - 高性能堆外 Map

