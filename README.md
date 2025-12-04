# RogueMap

[![License](https://img.shields.io/badge/license-Apache%202-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-8%2B-orange.svg)](https://www.oracle.com/java/)

**RogueMap** 是一个高性能的嵌入式键值存储引擎，支持堆外内存和持久化存储。目标是打造一个比 MapDB 更快、更易用的 Java 存储解决方案。

## ✨ 特性

### Phase 1 (已完成)

- ✅ **堆外内存存储** - 数据存储在 JVM 堆外，不受 GC 影响
- ✅ **零拷贝序列化** - 原始类型直接内存布局，无序列化开销
- ✅ **高并发支持** - 分段锁设计，支持高并发读写
- ✅ **智能内存分配** - Slab Allocator 减少内存碎片
- ✅ **多种索引结构** - 支持基础 HashIndex 和高并发 SegmentedHashIndex
- ✅ **类型安全** - 泛型支持，编译时类型检查
- ✅ **零依赖** - 核心库无第三方依赖
- ✅ **Java 8 兼容** - 兼容 Java 8+

## 🚀 快速开始

### Maven 依赖

```xml
<dependency>
    <groupId>com.yomahub</groupId>
    <artifactId>roguemap</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

### 基本使用

```java
import com.yomahub.roguemap.RogueMap;
import com.yomahub.roguemap.serialization.PrimitiveCodecs;
import com.yomahub.roguemap.serialization.StringCodec;

// 创建一个 String -> Long 的堆外内存 Map
try (RogueMap<String, Long> map = RogueMap.<String, Long>builder()
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

### 支持的数据类型

RogueMap 提供了零拷贝的原始类型编解码器：

```java
// Long 类型
RogueMap<String, Long> longMap = RogueMap.<String, Long>builder()
    .keyCodec(StringCodec.INSTANCE)
    .valueCodec(PrimitiveCodecs.LONG)
    .build();

// Integer 类型
RogueMap<Integer, Integer> intMap = RogueMap.<Integer, Integer>builder()
    .keyCodec(PrimitiveCodecs.INTEGER)
    .valueCodec(PrimitiveCodecs.INTEGER)
    .build();

// Double 类型
RogueMap<String, Double> doubleMap = RogueMap.<String, Double>builder()
    .keyCodec(StringCodec.INSTANCE)
    .valueCodec(PrimitiveCodecs.DOUBLE)
    .build();

// String 类型
RogueMap<String, String> stringMap = RogueMap.<String, String>builder()
    .keyCodec(StringCodec.INSTANCE)
    .valueCodec(StringCodec.INSTANCE)
    .build();
```

支持的原始类型：`Long`, `Integer`, `Double`, `Float`, `Short`, `Byte`, `Boolean`

### 配置选项

```java
RogueMap<K, V> map = RogueMap.<K, V>builder()
    // 必需配置
    .keyCodec(keyCodec)          // 键的编解码器
    .valueCodec(valueCodec)       // 值的编解码器

    // 可选配置
    .maxMemory(100 * 1024 * 1024) // 最大内存 (默认 1GB)
    .offHeap()                    // 堆外内存模式 (默认)
    .basicIndex()                 // 使用基础索引
    .segmentedIndex(64)           // 使用分段索引 (默认 64 段)
    .build();
```

## 📊 性能测试

在 MacBook Pro (M2, 16GB) 上的测试结果：

```
Inserting 100,000 entries...
Insert time: 42ms
Insert throughput: 2,380,952 ops/sec

Read time: 11ms
Read throughput: 9,090,909 ops/sec
```

## 🏗️ 架构设计

```
RogueMap API
   ↓
Index Layer (HashIndex/SegmentedHashIndex)
   ↓
Storage Engine (OffHeapStorage)
   ↓
Memory Allocator (SlabAllocator)
   ↓
UnsafeOps (Java 8 Unsafe)
   ↓
Off-Heap Memory
```

### 核心模块

- **memory** - 内存管理（UnsafeOps, SlabAllocator）
- **storage** - 存储引擎（OffHeapStorage）
- **index** - 索引层（HashIndex, SegmentedHashIndex）
- **serialization** - 序列化（PrimitiveCodecs, StringCodec）

## 🛣️ 开发路线图

- [x] **Phase 1: 核心基础** - 堆外内存存储和基础索引 (已完成)
- [ ] **Phase 2: 持久化存储** - 内存映射文件和 WAL
- [ ] **Phase 3: 多版本支持** - Java 17/21 优化
- [ ] **Phase 4: 性能优化** - SIMD、零拷贝、异步刷盘
- [ ] **Phase 5: 高级特性** - TTL、压缩、MVCC、混合模式

详见 [设计文档](docs/DESIGN_PLAN_V2.md)

## 📖 示例代码

更多示例请查看 [Example.java](src/test/java/com/yomahub/roguemap/Example.java)

## 🔧 构建项目

```bash
# 编译
mvn clean compile

# 运行测试
mvn test

# 运行示例
mvn test-compile exec:java -Dexec.mainClass="com.yomahub.roguemap.Example" -Dexec.classpathScope=test
```

## 📝 系统要求

- Java 8 或更高版本
- Maven 3.6+

## ⚠️ 注意事项

1. **Unsafe API 警告** - 本项目使用 `sun.misc.Unsafe` API，这是内部 API，可能在未来版本中被移除。Phase 3 将添加 Java 17/21 的替代实现。

2. **内存管理** - 请确保正确关闭 RogueMap 实例以释放堆外内存：
   ```java
   try (RogueMap<K, V> map = ...) {
       // 使用 map
   } // 自动关闭
   ```

3. **内存限制** - 堆外内存受 `-XX:MaxDirectMemorySize` JVM 参数限制

## 🤝 贡献

欢迎提交 Issue 和 Pull Request！

## 📄 许可证

本项目采用 [Apache License 2.0](LICENSE) 许可证。

## 🙏 致谢

本项目的设计灵感来自于：
- [MapDB](https://github.com/jankotek/mapdb) - 优秀的嵌入式数据库
- [Chronicle Map](https://github.com/OpenHFT/Chronicle-Map) - 高性能堆外 Map

---

**作者**: bryan31 (bryan31@yomahub.com)
**组织**: YomaHub
