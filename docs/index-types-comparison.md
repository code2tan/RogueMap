# RogueMap 索引类型对比分析

RogueMap 提供了三种不同的索引实现，每种索引都有其特定的使用场景和优缺点。本文档详细介绍了这三种索引类型的特点，并提供了详细的对比表格。

## 索引类型概述

### 1. HashIndex（哈希索引）
基于 `ConcurrentHashMap` 的基础索引实现，提供通用的键值存储功能。

### 2. SegmentedHashIndex（分段哈希索引）
使用分段设计和 `StampedLock` 的高并发索引，支持更好的并发性能。

### 3. LongPrimitiveIndex（原始类型索引）
使用原始类型数组的极致内存优化索引，仅支持 Long/Integer 键类型。

## 详细特性分析

### HashIndex

**核心特性：**
- 基于 `ConcurrentHashMap` 实现
- 支持泛型键类型
- 内置并发安全机制
- 完整的序列化支持

**技术实现：**
```java
private final ConcurrentHashMap<K, Entry> map;
private final AtomicInteger size;
private final Codec<K> keyCodec;
```

**内存结构：**
- 使用对象存储键值对
- 每个条目包含 `Entry(address, size)` 对象
- 键对象需要完整存储在内存中

**并发控制：**
- 依赖 `ConcurrentHashMap` 的内置分段锁机制
- 读写操作相对平衡，适合中等并发场景

### SegmentedHashIndex

**核心特性：**
- 分段设计减少锁竞争
- 使用 `StampedLock` 提供乐观读
- 支持高并发访问
- 可配置段数（默认64段）

**技术实现：**
```java
private final Segment<K>[] segments;
private final int segmentMask;
private final AtomicInteger size;
```

**内存结构：**
- 每个段维护独立的 `HashMap`
- 使用 `StampedLock` 进行细粒度控制
- 支持乐观读操作，显著提升读性能

**并发控制：**
- **乐观读：** `lock.tryOptimisticRead()` - 无锁快速读取
- **悲观读：** 当乐观读失败时降级到读锁
- **写操作：** 使用写锁保证数据一致性

### LongPrimitiveIndex

**核心特性：**
- 极致内存优化
- 仅支持原始类型键（Long/Integer）
- 高性能的单线程或低竞争场景
- **不支持序列化**

**技术实现：**
```java
private long[] keys;           // 原始类型键数组
private long[] addresses;      // 地址数组
private int[] sizes;           // 大小数组
```

**内存结构：**
- 使用三个并行数组存储数据
- 避免对象开销，节省约81%内存
- 开放地址法解决哈希冲突

**性能优化：**
- 线性探测，CPU缓存友好
- MurmurHash3 高质量哈希函数
- 无对象创建和GC压力

## 详细对比表格

| 特性维度 | HashIndex | SegmentedHashIndex | LongPrimitiveIndex |
|---------|-----------|-------------------|-------------------|
| **内存使用** | ❌ 较高<br>（对象+包装开销） | ❌ 较高<br>（分段+对象开销） | ✅ 极低<br>（原始类型数组） |
| **并发性能** | ✅ 中等<br>（ConcurrentHashMap） | ✅ 极佳<br>（乐观读+分段锁） | ⚠️ 中等<br>（单一StampedLock） |
| **内存占用(100万条)** | ~104MB | ~120MB | ~27MB |
| **键类型支持** | ✅ 任意泛型 | ✅ 任意泛型 | ⚠️ 仅Long/Integer |
| **序列化支持** | ✅ 完整支持 | ✅ 完整支持 | ❌ 不支持 |
| **读操作性能** | ✅ 良好 | ✅ 极佳<br>（乐观读无锁） | ✅ 良好<br>（缓存友好） |
| **写操作性能** | ✅ 良好 | ✅ 良好<br>（段间竞争小） | ✅ 极佳<br>（无对象创建） |
| **CPU缓存效率** | ⚠️ 中等<br>（对象分散） | ⚠️ 中等<br>（分段存储） | ✅ 极佳<br>（连续数组） |
| **GC压力** | ❌ 较高<br>（大量Entry对象） | ❌ 较高<br>（分段Entry对象） | ✅ 无<br>（原始类型） |
| **适用场景** | 通用场景 | 高并发读写 | 内存敏感/纯内存 |

## 性能基准测试结果

### 内存占用对比（100万条目，负载因子0.75）

| 索引类型 | 键存储 | 地址存储 | 大小存储 | 总内存 | 节省率 |
|---------|--------|----------|----------|--------|--------|
| HashMap(参考) | 24MB | 24MB | 16MB | 104MB | - |
| HashIndex | 24MB | 24MB | 16MB | ~104MB | ~0% |
| SegmentedHashIndex | 30MB | 30MB | 20MB | ~120MB | -15% |
| **LongPrimitiveIndex** | **10.7MB** | **10.7MB** | **5.3MB** | **~27MB** | **81%** |

### 吞吐量对比（单线程，100万操作）

| 操作类型 | HashIndex | SegmentedHashIndex | LongPrimitiveIndex |
|---------|-----------|-------------------|-------------------|
| 写入 | ~200K ops/s | ~180K ops/s | **~350K ops/s** |
| 顺序读取 | ~400K ops/s | **~600K ops/s** | ~450K ops/s |
| 随机读取 | ~300K ops/s | **~500K ops/s** | ~400K ops/s |

## 使用场景推荐

### HashIndex - 通用选择
```java
RogueMap<String, User> map = RogueMap.<String, User>builder()
    .keyCodec(new StringCodec())
    .valueCodec(KryoObjectCodec.create(User.class))
    .segmentedIndex(32)  // 或者不配置，使用默认
    .build();
```
**适用场景：**
- 中等数据量（< 100万条目）
- 通用键值存储
- 需要序列化支持
- 开发和测试环境

### SegmentedHashIndex - 高并发优化
```java
RogueMap<String, User> map = RogueMap.<String, User>builder()
    .keyCodec(new StringCodec())
    .valueCodec(KryoObjectCodec.create(User.class))
    .segmentedIndex(64)  // 64个段，支持高并发
    .build();
```
**适用场景：**
- 高并发读写（> 10线程）
- 读多写少的场景
- 需要持久化支持
- 生产环境推荐

### LongPrimitiveIndex - 极致性能
```java
RogueMap<Long, Long> map = RogueMap.<Long, Long>builder()
    .keyCodec(PrimitiveCodecs.LONG)
    .valueCodec(PrimitiveCodecs.LONG)
    .primitiveIndex()  // 使用原始类型索引
    .build();
```
**适用场景：**
- 大数据量（> 100万条目）
- 内存敏感环境
- 纯内存模式（不支持持久化）
- 数值型键（ID、时间戳等）

## 重要注意事项

### 1. 持久化场景限制
- ❌ `LongPrimitiveIndex` 不支持序列化，不能用于 MMAP 持久化
- ✅ `HashIndex` 和 `SegmentedHashIndex` 完全支持持久化

### 2. 键类型限制
- `LongPrimitiveIndex` 仅支持 `PrimitiveCodecs.LONG` 或 `PrimitiveCodecs.INTEGER`
- 如果使用不兼容的键类型，会抛出 `IllegalStateException`

### 3. 内存vs性能权衡
- **内存优先：** 选择 `LongPrimitiveIndex`
- **并发优先：** 选择 `SegmentedHashIndex`
- **通用需求：** 选择 `HashIndex`

### 4. 建议配置
```java
// 高并发生产环境推荐配置
.segmentedIndex(64)  // 64个段减少竞争
.initialCapacity(expectedSize / 64 * 2)  // 每段预留空间

// 内存敏感环境推荐配置
.primitiveIndex()
.initialCapacity(expectedSize * 2)  // 减少扩容开销
```

## 总结

选择合适的索引类型是 RogueMap 性能优化的关键：

- **HashIndex** 是安全的选择，适合大多数场景
- **SegmentedHashIndex** 是生产环境的最佳选择，特别适合高并发
- **LongPrimitiveIndex** 是极致性能的选择，但限制较多

根据具体的业务需求、数据规模、并发水平和内存约束来选择最适合的索引类型。