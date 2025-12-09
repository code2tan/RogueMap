# RogueMap 性能测试报告

本文档记录了 RogueMap 在不同模式下的性能表现，以及与 MapDB 的对比测试结果。

## 测试环境

- **硬件**: MacBook Pro (M2, 16GB)
- **Java 版本**: Java 8+
- **测试数据量**: 1,000,000 条记录
- **键值类型**: Long -> Long

---

## 1. RogueMap 多模式性能对比

该测试对比了 RogueMap 在不同存储模式下的性能表现，包括 HashMap（堆内存）、OffHeap（堆外内存）、Mmap 临时文件和 Mmap 持久化四种模式。

### 1.1 综合指标对比

| 模式 | 写入(ms) | 读取(ms) | 写吞吐(ops/s) | 读吞吐(ops/s) | 堆内存(MB) |
|------|----------|----------|---------------|---------------|------------|
| HashMap模式 | 611 | 463 | 1,636,661 | 2,159,827 | 304.04 |
| OffHeap模式 | 658 | 251 | 1,519,756 | 3,984,063 | 40.46 |
| Mmap临时文件模式 | 629 | 212 | 1,589,825 | 4,716,981 | 40.13 |
| Mmap持久化模式 | 547 | 195 | 1,828,153 | 5,128,205 | 40.01 |

### 1.2 性能分析

#### 写入性能

- **最快**: Mmap持久化模式 (547ms, 1,828,153 ops/s)
- **最慢**: OffHeap模式 (658ms, 1,519,756 ops/s)
- **性能差异**: 约 20% 提升

**结论**: Mmap 持久化模式在写入性能上表现最佳，这得益于操作系统的页缓存优化和顺序写入特性。

#### 读取性能

- **最快**: Mmap持久化模式 (195ms, 5,128,205 ops/s)
- **最慢**: HashMap模式 (463ms, 2,159,827 ops/s)
- **性能差异**: 超过 2.4 倍提升

**结论**: Mmap 模式在读取性能上有显著优势，读取速度是 HashMap 的 2.4 倍，是 OffHeap 的 1.3 倍。这得益于操作系统的内存映射机制和零拷贝优化。

#### 内存使用

- **HashMap模式**: 304.04 MB (堆内存)
- **OffHeap模式**: 40.46 MB (堆外内存)
- **Mmap临时文件**: 40.13 MB (堆外内存)
- **Mmap持久化**: 40.01 MB (堆外内存)

**结论**: 堆外内存模式（包括 OffHeap 和 Mmap）相比 HashMap 减少了约 **87%** 的堆内存使用，大幅降低了 GC 压力。

### 1.3 模式选择建议

| 场景 | 推荐模式 | 原因 |
|------|----------|------|
| 临时缓存，无持久化需求 | OffHeap | 内存占用小，性能均衡 |
| 需要持久化，追求极致性能 | Mmap持久化 | 读写性能最优，支持数据恢复 |
| 临时文件处理 | Mmap临时文件 | 性能接近持久化，自动清理 |
| 小数据量，简单场景 | HashMap | 最简单，无额外配置 |

---

## 2. RogueMap vs MapDB 性能对比

该测试对比了 RogueMap 和 MapDB 在相同存储模式下的性能差异。

### 2.1 综合指标对比

| 实现方式 | 写入(ms) | 读取(ms) | 写吞吐(ops/s) | 读吞吐(ops/s) | 堆内存(MB) |
|----------|----------|----------|---------------|---------------|------------|
| RogueMap OffHeap | 784 | 370 | 1,275,510 | 2,702,702 | 38.82 |
| RogueMap Mmap 临时文件 | 657 | 256 | 1,522,070 | 3,906,250 | 40.04 |
| RogueMap Mmap 持久化 | 632 | 202 | 1,582,278 | 4,950,495 | 40.00 |
| MapDB OffHeap | 2,593 | 3,308 | 385,653 | 302,297 | 4.77 |
| MapDB 临时文件 | 2,675 | 3,272 | 373,831 | 305,623 | 0.08 |
| MapDB 持久化 | 2,764 | 3,207 | 361,794 | 311,817 | 0.08 |

### 2.2 性能对比分析

#### 写入性能对比

**RogueMap vs MapDB (持久化模式)**:
- RogueMap: 632ms (1,582,278 ops/s)
- MapDB: 2,764ms (361,794 ops/s)
- **性能提升**: 约 **4.4 倍**

**RogueMap vs MapDB (OffHeap模式)**:
- RogueMap: 784ms (1,275,510 ops/s)
- MapDB: 2,593ms (385,653 ops/s)
- **性能提升**: 约 **3.3 倍**

#### 读取性能对比

**RogueMap vs MapDB (持久化模式)**:
- RogueMap: 202ms (4,950,495 ops/s)
- MapDB: 3,207ms (311,817 ops/s)
- **性能提升**: 约 **15.9 倍**

**RogueMap vs MapDB (OffHeap模式)**:
- RogueMap: 370ms (2,702,702 ops/s)
- MapDB: 3,308ms (302,297 ops/s)
- **性能提升**: 约 **8.9 倍**

#### 内存使用对比

| 模式 | RogueMap | MapDB | 差异 |
|------|----------|-------|------|
| OffHeap | 38.82 MB | 4.77 MB | RogueMap 多 34 MB |
| 临时文件 | 40.04 MB | 0.08 MB | RogueMap 多 40 MB |
| 持久化 | 40.00 MB | 0.08 MB | RogueMap 多 40 MB |

**分析**: MapDB 的堆内存占用更小，但这是以牺牲性能为代价的。RogueMap 使用更多的堆内存来缓存元数据和索引，从而实现更高的吞吐量。

### 2.3 核心优势总结

| 维度 | RogueMap | MapDB | 优势倍数 |
|------|----------|-------|----------|
| 写入速度 | 1,582,278 ops/s | 361,794 ops/s | **4.4x** |
| 读取速度 | 4,950,495 ops/s | 311,817 ops/s | **15.9x** |
| 堆内存使用 | 40 MB | 0.08 MB | 劣势 (500x) |
| 整体性能 | 极致 | 中等 | **显著优势** |

### 2.4 适用场景对比

| 场景 | 推荐选择 | 原因 |
|------|----------|------|
| 高吞吐量写入 | RogueMap | 写入速度快 4.4 倍 |
| 高吞吐量读取 | RogueMap | 读取速度快 15.9 倍 |
| 极致性能要求 | RogueMap | 综合性能远超 MapDB |
| 超低内存环境 | MapDB | 堆内存占用极小 |
| 持久化存储 | RogueMap | 性能与持久化兼得 |
| 嵌入式设备 | MapDB | 资源占用更小 |

---

## 3. 测试代码参考

### 3.1 RogueMap 多模式测试

测试文件: [MemoryUsageComparisonTest.java](../src/test/java/com/yomahub/roguemap/compare/MemoryUsageComparisonTest.java)

```java
// HashMap 模式
Map<Long, Long> hashMap = new HashMap<>();

// OffHeap 模式
RogueMap<Long, Long> offHeapMap = RogueMap.<Long, Long>builder()
    .offHeap()
    .maxMemory(500 * 1024 * 1024L)
    .keyCodec(PrimitiveCodecs.LONG)
    .valueCodec(PrimitiveCodecs.LONG)
    .build();

// Mmap 临时文件模式
RogueMap<Long, Long> mmapTempMap = RogueMap.<Long, Long>builder()
    .temporary()
    .mmap()
    .allocateSize(500 * 1024 * 1024L)
    .keyCodec(PrimitiveCodecs.LONG)
    .valueCodec(PrimitiveCodecs.LONG)
    .build();

// Mmap 持久化模式
RogueMap<Long, Long> mmapPersistentMap = RogueMap.<Long, Long>builder()
    .persistent("test_data/persistent-map.db")
    .mmap()
    .allocateSize(500 * 1024 * 1024L)
    .keyCodec(PrimitiveCodecs.LONG)
    .valueCodec(PrimitiveCodecs.LONG)
    .build();
```

### 3.2 RogueMap vs MapDB 对比测试

测试文件: [RogueMapVsMapDBComparisonTest.java](../src/test/java/com/yomahub/roguemap/compare/RogueMapVsMapDBComparisonTest.java)

```java
// RogueMap OffHeap
RogueMap<Long, Long> rogueMapOffHeap = RogueMap.<Long, Long>builder()
    .offHeap()
    .maxMemory(500 * 1024 * 1024L)
    .keyCodec(PrimitiveCodecs.LONG)
    .valueCodec(PrimitiveCodecs.LONG)
    .build();

// MapDB OffHeap
DB mapDbOffHeap = DBMaker.memoryDB()
    .make();
HTreeMap<Long, Long> mapDbOffHeapMap = mapDbOffHeap
    .hashMap("offheap")
    .create();
```

---

## 4. 运行性能测试

### 4.1 运行 RogueMap 多模式对比

```bash
mvn test -Dtest=MemoryUsageComparisonTest
```

### 4.2 运行 RogueMap vs MapDB 对比

```bash
mvn test -Dtest=RogueMapVsMapDBComparisonTest
```

### 4.3 运行所有性能测试

```bash
mvn test -Dtest=*ComparisonTest
```

---

## 5. 结论

### RogueMap 的核心优势

1. **极致性能**:
   - 写入速度比 MapDB 快 **4.4 倍**
   - 读取速度比 MapDB 快 **15.9 倍**

2. **多模式支持**:
   - HashMap: 适合小数据量
   - OffHeap: 适合临时缓存
   - Mmap临时文件: 适合临时处理
   - Mmap持久化: 适合持久化存储

3. **低 GC 压力**:
   - 堆外内存模式减少 87% 堆内存占用
   - 适合大数据量场景

4. **易用性**:
   - 简洁的 Builder API
   - 自动资源管理
   - 类型安全

### 性能优化建议

1. **选择合适的模式**: 根据使用场景选择最优模式
2. **使用原始类型**: Long/Integer 比 String 性能更好
3. **预分配内存**: 设置合理的 `maxMemory` 或 `allocateSize`
4. **批量刷盘**: 批量操作后统一调用 `flush()`

---

**测试日期**: 2025-12-09
**版本**: 0.1.0-SNAPSHOT
**测试人员**: bryan31
