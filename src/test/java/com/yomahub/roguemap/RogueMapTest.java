package com.yomahub.roguemap;

import com.yomahub.roguemap.serialization.PrimitiveCodecs;
import com.yomahub.roguemap.serialization.StringCodec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RogueMap 测试类
 *
 * 测试 RogueMap 的核心功能，包括：
 * - 基本的 put/get/remove 操作
 * - 不同数据类型的支持
 * - 基础索引和分段索引
 * - 边界情况和异常处理
 */
class RogueMapTest {

    private RogueMap<String, Long> map;

    @BeforeEach
    void setUp() {
        // 在每个测试之前创建一个新的 RogueMap 实例
        // 使用 String 作为键，Long 作为值
        // 最大内存设置为 10MB
        map = RogueMap.<String, Long>builder()
                .keyCodec(StringCodec.INSTANCE)
                .valueCodec(PrimitiveCodecs.LONG)
                .maxMemory(10 * 1024 * 1024) // 10MB
                .build();
    }

    @AfterEach
    void tearDown() {
        // 在每个测试之后关闭 map，释放堆外内存
        if (map != null) {
            map.close();
        }
    }

    /**
     * 测试基本的 put 和 get 操作
     *
     * 验证：
     * 1. 新插入的键返回 null
     * 2. 可以正确获取已插入的值
     * 3. 更新已存在的键会返回旧值
     * 4. 可以正确获取更新后的值
     */
    @Test
    void testPutAndGet() {
        // 插入一个新值，应该返回 null（之前不存在）
        Long oldValue = map.put("key1", 100L);
        assertNull(oldValue, "插入新键应该返回 null");

        // 获取刚插入的值
        Long value = map.get("key1");
        assertEquals(100L, value, "应该获取到刚插入的值");

        // 更新这个键的值，应该返回旧值
        oldValue = map.put("key1", 200L);
        assertEquals(100L, oldValue, "更新键应该返回旧值");

        // 获取更新后的值
        value = map.get("key1");
        assertEquals(200L, value, "应该获取到更新后的值");
    }

    /**
     * 测试获取不存在的键
     *
     * 验证：获取不存在的键应该返回 null
     */
    @Test
    void testGetNonExistent() {
        Long value = map.get("nonexistent");
        assertNull(value, "获取不存在的键应该返回 null");
    }

    /**
     * 测试 remove 操作
     *
     * 验证：
     * 1. 删除存在的键会返回对应的值
     * 2. 删除后该键不再存在
     * 3. 其他键不受影响
     */
    @Test
    void testRemove() {
        // 插入两个键值对
        map.put("key1", 100L);
        map.put("key2", 200L);

        // 删除第一个键
        Long removed = map.remove("key1");
        assertEquals(100L, removed, "删除应该返回对应的值");

        // 验证第一个键已被删除
        assertNull(map.get("key1"), "删除后的键应该不存在");
        // 验证第二个键仍然存在
        assertEquals(200L, map.get("key2"), "其他键不应受影响");
    }

    /**
     * 测试 containsKey 操作
     *
     * 验证：
     * 1. 存在的键返回 true
     * 2. 不存在的键返回 false
     */
    @Test
    void testContainsKey() {
        map.put("key1", 100L);

        assertTrue(map.containsKey("key1"), "存在的键应该返回 true");
        assertFalse(map.containsKey("key2"), "不存在的键应该返回 false");
    }

    /**
     * 测试 size 和 isEmpty 操作
     *
     * 验证：
     * 1. 初始状态 size 为 0，isEmpty 为 true
     * 2. 插入元素后 size 增加
     * 3. 删除元素后 size 减少
     */
    @Test
    void testSize() {
        // 初始状态
        assertEquals(0, map.size(), "初始 size 应该为 0");
        assertTrue(map.isEmpty(), "初始状态应该为空");

        // 插入一个元素
        map.put("key1", 100L);
        assertEquals(1, map.size(), "插入一个元素后 size 应该为 1");
        assertFalse(map.isEmpty(), "插入元素后不应该为空");

        // 再插入一个元素
        map.put("key2", 200L);
        assertEquals(2, map.size(), "插入两个元素后 size 应该为 2");

        // 删除一个元素
        map.remove("key1");
        assertEquals(1, map.size(), "删除一个元素后 size 应该为 1");
    }

    /**
     * 测试大量数据的插入和查询
     *
     * 验证：
     * 1. 可以正确处理 1000 个键值对
     * 2. 所有数据都能正确读取
     * 3. size 统计正确
     */
    @Test
    void testMultipleEntries() {
        // 插入 1000 个键值对
        for (int i = 0; i < 1000; i++) {
            map.put("key" + i, (long) i);
        }

        // 验证所有键值对都能正确读取
        for (int i = 0; i < 1000; i++) {
            assertEquals((long) i, map.get("key" + i),
                "键 'key" + i + "' 的值应该是 " + i);
        }

        // 验证 size 正确
        assertEquals(1000, map.size(), "应该有 1000 个元素");
    }

    /**
     * 测试 null 键的处理
     *
     * 验证：
     * 1. put null 键会抛出 IllegalArgumentException
     * 2. get null 键返回 null
     * 3. containsKey null 返回 false
     */
    @Test
    void testNullKey() {
        // 尝试插入 null 键应该抛出异常
        assertThrows(IllegalArgumentException.class, () -> {
            map.put(null, 100L);
        }, "插入 null 键应该抛出 IllegalArgumentException");

        // get null 键应该返回 null
        assertNull(map.get(null), "get null 键应该返回 null");
        // containsKey null 应该返回 false
        assertFalse(map.containsKey(null), "containsKey null 应该返回 false");
    }

    /**
     * 测试 String 到 String 的 Map
     *
     * 验证：
     * 1. 可以使用 String 作为键和值
     * 2. 字符串的存储和读取正确
     */
    @Test
    void testStringToStringMap() {
        // 创建 String -> String 的 Map
        RogueMap<String, String> stringMap = RogueMap.<String, String>builder()
                .keyCodec(StringCodec.INSTANCE)
                .valueCodec(StringCodec.INSTANCE)
                .maxMemory(10 * 1024 * 1024)
                .build();

        try {
            // 插入字符串键值对
            stringMap.put("hello", "world");
            stringMap.put("foo", "bar");

            // 验证读取正确
            assertEquals("world", stringMap.get("hello"),
                "应该获取到 'world'");
            assertEquals("bar", stringMap.get("foo"),
                "应该获取到 'bar'");
            assertEquals(2, stringMap.size(),
                "应该有 2 个元素");
        } finally {
            stringMap.close();
        }
    }

    /**
     * 测试 Integer 到 Double 的 Map
     *
     * 验证：
     * 1. 可以使用 Integer 作为键，Double 作为值
     * 2. 数字类型的存储和读取正确
     */
    @Test
    void testIntegerToDoubleMap() {
        // 创建 Integer -> Double 的 Map
        RogueMap<Integer, Double> numMap = RogueMap.<Integer, Double>builder()
                .keyCodec(PrimitiveCodecs.INTEGER)
                .valueCodec(PrimitiveCodecs.DOUBLE)
                .maxMemory(10 * 1024 * 1024)
                .build();

        try {
            // 插入数字键值对
            numMap.put(1, 1.5);
            numMap.put(2, 2.5);
            numMap.put(3, 3.5);

            // 验证读取正确
            assertEquals(1.5, numMap.get(1), "键 1 的值应该是 1.5");
            assertEquals(2.5, numMap.get(2), "键 2 的值应该是 2.5");
            assertEquals(3.5, numMap.get(3), "键 3 的值应该是 3.5");
        } finally {
            numMap.close();
        }
    }

    /**
     * 测试基础索引（HashIndex）
     *
     * 验证：
     * 1. 基础索引可以正常工作
     * 2. 数据的存储和读取正确
     */
    @Test
    void testBasicIndex() {
        // 创建使用基础索引的 Map
        RogueMap<String, Long> basicMap = RogueMap.<String, Long>builder()
                .keyCodec(StringCodec.INSTANCE)
                .valueCodec(PrimitiveCodecs.LONG)
                .maxMemory(10 * 1024 * 1024)
                .basicIndex() // 使用基础索引而不是分段索引
                .build();

        try {
            basicMap.put("key1", 100L);
            basicMap.put("key2", 200L);

            assertEquals(100L, basicMap.get("key1"),
                "基础索引应该正确存取数据");
            assertEquals(200L, basicMap.get("key2"),
                "基础索引应该正确存取数据");
        } finally {
            basicMap.close();
        }
    }

    /**
     * 测试分段索引（SegmentedHashIndex）
     *
     * 验证：
     * 1. 分段索引可以正常工作
     * 2. 自定义段数（32）能正确创建
     * 3. 数据的存储和读取正确
     */
    @Test
    void testSegmentedIndex() {
        // 创建使用 32 个段的分段索引 Map
        RogueMap<String, Long> segmentedMap = RogueMap.<String, Long>builder()
                .keyCodec(StringCodec.INSTANCE)
                .valueCodec(PrimitiveCodecs.LONG)
                .maxMemory(10 * 1024 * 1024)
                .segmentedIndex(32) // 使用 32 个段的分段索引
                .build();

        try {
            segmentedMap.put("key1", 100L);
            segmentedMap.put("key2", 200L);

            assertEquals(100L, segmentedMap.get("key1"),
                "分段索引应该正确存取数据");
            assertEquals(200L, segmentedMap.get("key2"),
                "分段索引应该正确存取数据");
        } finally {
            segmentedMap.close();
        }
    }
}
