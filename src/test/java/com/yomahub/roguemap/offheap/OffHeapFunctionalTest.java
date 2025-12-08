package com.yomahub.roguemap.offheap;

import com.yomahub.roguemap.RogueMap;
import com.yomahub.roguemap.serialization.KryoObjectCodec;
import com.yomahub.roguemap.serialization.PrimitiveCodecs;
import com.yomahub.roguemap.serialization.StringCodec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 堆外内存存储功能测试
 *
 * 测试所有基本功能,包括:
 * - 基本的 put/get/remove/containsKey 操作
 * - 不同数据类型的支持
 * - 不同索引类型的支持
 * - 边界情况和异常处理
 * - 并发操作
 * - 内存管理
 */
public class OffHeapFunctionalTest {

    private RogueMap<String, String> map;

    @BeforeEach
    public void setUp() {
        // 每个测试前创建新的 map
        map = RogueMap.<String, String>builder()
                .keyCodec(new StringCodec())
                .valueCodec(new StringCodec())
                .maxMemory(100 * 1024 * 1024) // 100MB
                .offHeap()
                .build();
    }

    @AfterEach
    public void tearDown() {
        if (map != null) {
            map.close();
        }
    }

    // ========== 基本操作测试 ==========

    @Test
    public void testPutAndGet() {
        assertNull(map.put("key1", "value1"));
        assertEquals("value1", map.get("key1"));
    }

    @Test
    public void testPutUpdate() {
        map.put("key1", "value1");
        assertEquals("value1", map.put("key1", "value2"));
        assertEquals("value2", map.get("key1"));
    }

    @Test
    public void testGetNonExistent() {
        assertNull(map.get("nonexistent"));
    }

    @Test
    public void testRemove() {
        map.put("key1", "value1");
        assertEquals("value1", map.remove("key1"));
        assertNull(map.get("key1"));
    }

    @Test
    public void testRemoveNonExistent() {
        assertNull(map.remove("nonexistent"));
    }

    @Test
    public void testContainsKey() {
        map.put("key1", "value1");
        assertTrue(map.containsKey("key1"));
        assertFalse(map.containsKey("key2"));
    }

    @Test
    public void testSize() {
        assertEquals(0, map.size());
        map.put("key1", "value1");
        assertEquals(1, map.size());
        map.put("key2", "value2");
        assertEquals(2, map.size());
        map.remove("key1");
        assertEquals(1, map.size());
    }

    @Test
    public void testIsEmpty() {
        assertTrue(map.isEmpty());
        map.put("key1", "value1");
        assertFalse(map.isEmpty());
        map.remove("key1");
        assertTrue(map.isEmpty());
    }

    @Test
    public void testClear() {
        map.put("key1", "value1");
        map.put("key2", "value2");
        map.clear();
        assertEquals(0, map.size());
        assertTrue(map.isEmpty());
    }

    // ========== Null 值处理测试 ==========

    @Test
    public void testNullKey() {
        assertThrows(IllegalArgumentException.class, () -> map.put(null, "value"));
        assertNull(map.get(null));
        assertFalse(map.containsKey(null));
        assertNull(map.remove(null));
    }

    // ========== 多条目测试 ==========

    @Test
    public void testMultipleEntries() {
        int count = 1000;
        for (int i = 0; i < count; i++) {
            map.put("key" + i, "value" + i);
        }

        assertEquals(count, map.size());

        for (int i = 0; i < count; i++) {
            assertEquals("value" + i, map.get("key" + i));
        }
    }

    @Test
    public void testMultipleUpdates() {
        map.put("key1", "value1");
        map.put("key1", "value2");
        map.put("key1", "value3");
        assertEquals("value3", map.get("key1"));
        assertEquals(1, map.size());
    }

    @Test
    public void testMixedOperations() {
        map.put("key1", "value1");
        map.put("key2", "value2");
        assertEquals("value1", map.remove("key1"));
        map.put("key3", "value3");
        assertEquals("value2", map.put("key2", "value2_updated"));

        assertEquals(2, map.size());
        assertNull(map.get("key1"));
        assertEquals("value2_updated", map.get("key2"));
        assertEquals("value3", map.get("key3"));
    }

    // ========== 不同数据类型测试 ==========

    @Test
    public void testLongToLongMap() {
        RogueMap<Long, Long> longMap = RogueMap.<Long, Long>builder()
                .keyCodec(PrimitiveCodecs.LONG)
                .valueCodec(PrimitiveCodecs.LONG)
                .maxMemory(10 * 1024 * 1024)
                .offHeap()
                .build();

        try {
            longMap.put(1L, 100L);
            longMap.put(2L, 200L);
            assertEquals(100L, longMap.get(1L));
            assertEquals(200L, longMap.get(2L));
        } finally {
            longMap.close();
        }
    }

    @Test
    public void testIntegerToIntegerMap() {
        RogueMap<Integer, Integer> intMap = RogueMap.<Integer, Integer>builder()
                .keyCodec(PrimitiveCodecs.INTEGER)
                .valueCodec(PrimitiveCodecs.INTEGER)
                .maxMemory(10 * 1024 * 1024)
                .offHeap()
                .build();

        try {
            intMap.put(1, 100);
            intMap.put(2, 200);
            assertEquals(100, intMap.get(1));
            assertEquals(200, intMap.get(2));
        } finally {
            intMap.close();
        }
    }

    @Test
    public void testDoubleMap() {
        RogueMap<Integer, Double> doubleMap = RogueMap.<Integer, Double>builder()
                .keyCodec(PrimitiveCodecs.INTEGER)
                .valueCodec(PrimitiveCodecs.DOUBLE)
                .maxMemory(10 * 1024 * 1024)
                .offHeap()
                .build();

        try {
            doubleMap.put(1, 1.5);
            doubleMap.put(2, 2.5);
            assertEquals(1.5, doubleMap.get(1));
            assertEquals(2.5, doubleMap.get(2));
        } finally {
            doubleMap.close();
        }
    }

    @Test
    public void testObjectMap() {
        RogueMap<String, TestUser> objectMap = RogueMap.<String, TestUser>builder()
                .keyCodec(new StringCodec())
                .valueCodec(KryoObjectCodec.create(TestUser.class))
                .maxMemory(10 * 1024 * 1024)
                .offHeap()
                .build();

        try {
            TestUser user1 = new TestUser(1L, "Alice", 25);
            TestUser user2 = new TestUser(2L, "Bob", 30);

            objectMap.put("user1", user1);
            objectMap.put("user2", user2);

            TestUser retrieved1 = objectMap.get("user1");
            assertEquals(1L, retrieved1.getId());
            assertEquals("Alice", retrieved1.getName());
            assertEquals(25, retrieved1.getAge());
        } finally {
            objectMap.close();
        }
    }

    // ========== 索引类型测试 ==========

    @Test
    public void testBasicIndex() {
        RogueMap<String, String> basicMap = RogueMap.<String, String>builder()
                .keyCodec(new StringCodec())
                .valueCodec(new StringCodec())
                .maxMemory(10 * 1024 * 1024)
                .offHeap()
                .basicIndex()
                .build();

        try {
            basicMap.put("key1", "value1");
            basicMap.put("key2", "value2");
            assertEquals("value1", basicMap.get("key1"));
            assertEquals("value2", basicMap.get("key2"));
        } finally {
            basicMap.close();
        }
    }

    @Test
    public void testSegmentedIndex() {
        RogueMap<String, String> segmentedMap = RogueMap.<String, String>builder()
                .keyCodec(new StringCodec())
                .valueCodec(new StringCodec())
                .maxMemory(10 * 1024 * 1024)
                .offHeap()
                .segmentedIndex(32)
                .build();

        try {
            segmentedMap.put("key1", "value1");
            segmentedMap.put("key2", "value2");
            assertEquals("value1", segmentedMap.get("key1"));
            assertEquals("value2", segmentedMap.get("key2"));
        } finally {
            segmentedMap.close();
        }
    }

    @Test
    public void testPrimitiveIndexLong() {
        RogueMap<Long, String> primitiveMap = RogueMap.<Long, String>builder()
                .keyCodec(PrimitiveCodecs.LONG)
                .valueCodec(new StringCodec())
                .maxMemory(10 * 1024 * 1024)
                .offHeap()
                .primitiveIndex()
                .build();

        try {
            primitiveMap.put(1L, "value1");
            primitiveMap.put(2L, "value2");
            assertEquals("value1", primitiveMap.get(1L));
            assertEquals("value2", primitiveMap.get(2L));
        } finally {
            primitiveMap.close();
        }
    }

    @Test
    public void testPrimitiveIndexInteger() {
        RogueMap<Integer, String> primitiveMap = RogueMap.<Integer, String>builder()
                .keyCodec(PrimitiveCodecs.INTEGER)
                .valueCodec(new StringCodec())
                .maxMemory(10 * 1024 * 1024)
                .offHeap()
                .primitiveIndex()
                .build();

        try {
            primitiveMap.put(1, "value1");
            primitiveMap.put(2, "value2");
            assertEquals("value1", primitiveMap.get(1));
            assertEquals("value2", primitiveMap.get(2));
        } finally {
            primitiveMap.close();
        }
    }

    // ========== 大数据测试 ==========

    @Test
    public void testLargeValues() {
        String largeValue = createLargeString(10000);
        map.put("key1", largeValue);
        assertEquals(largeValue, map.get("key1"));
    }

    @Test
    public void testManySmallValues() {
        int count = 10000;
        for (int i = 0; i < count; i++) {
            map.put("key" + i, "value" + i);
        }
        assertEquals(count, map.size());
    }

    // ========== 并发测试 ==========

    @Test
    public void testConcurrentPut() throws InterruptedException {
        RogueMap<String, String> concurrentMap = RogueMap.<String, String>builder()
                .keyCodec(new StringCodec())
                .valueCodec(new StringCodec())
                .maxMemory(100 * 1024 * 1024)
                .offHeap()
                .segmentedIndex(64)
                .build();

        try {
            int threadCount = 10;
            int itemsPerThread = 100;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);

            for (int t = 0; t < threadCount; t++) {
                final int threadId = t;
                executor.submit(() -> {
                    try {
                        for (int i = 0; i < itemsPerThread; i++) {
                            concurrentMap.put("thread" + threadId + "_key" + i, "value" + i);
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await();
            executor.shutdown();

            assertEquals(threadCount * itemsPerThread, concurrentMap.size());
        } finally {
            concurrentMap.close();
        }
    }

    @Test
    public void testConcurrentReadWrite() throws InterruptedException {
        RogueMap<String, String> concurrentMap = RogueMap.<String, String>builder()
                .keyCodec(new StringCodec())
                .valueCodec(new StringCodec())
                .maxMemory(100 * 1024 * 1024)
                .offHeap()
                .segmentedIndex(64)
                .build();

        try {
            // 先写入一些数据
            for (int i = 0; i < 1000; i++) {
                concurrentMap.put("key" + i, "value" + i);
            }

            int threadCount = 10;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger readCount = new AtomicInteger(0);

            // 一半线程读,一半线程写
            for (int t = 0; t < threadCount; t++) {
                final int threadId = t;
                executor.submit(() -> {
                    try {
                        if (threadId % 2 == 0) {
                            // 读线程
                            for (int i = 0; i < 1000; i++) {
                                String value = concurrentMap.get("key" + (i % 1000));
                                if (value != null) {
                                    readCount.incrementAndGet();
                                }
                            }
                        } else {
                            // 写线程
                            for (int i = 0; i < 100; i++) {
                                concurrentMap.put("thread" + threadId + "_key" + i, "value" + i);
                            }
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await();
            executor.shutdown();

            assertTrue(readCount.get() > 0);
        } finally {
            concurrentMap.close();
        }
    }

    // ========== 边界条件测试 ==========

    @Test
    public void testEmptyKey() {
        map.put("", "empty_key_value");
        assertEquals("empty_key_value", map.get(""));
    }

    @Test
    public void testLongKey() {
        String longKey = createLargeString(1000);
        map.put(longKey, "value");
        assertEquals("value", map.get(longKey));
    }

    @Test
    public void testSpecialCharacters() {
        map.put("key\n\t\r", "value");
        map.put("key中文", "中文值");
        map.put("key🎉", "emoji_value");

        assertEquals("value", map.get("key\n\t\r"));
        assertEquals("中文值", map.get("key中文"));
        assertEquals("emoji_value", map.get("key🎉"));
    }

    @Test
    public void testDuplicateValues() {
        map.put("key1", "same_value");
        map.put("key2", "same_value");
        map.put("key3", "same_value");

        assertEquals("same_value", map.get("key1"));
        assertEquals("same_value", map.get("key2"));
        assertEquals("same_value", map.get("key3"));
    }

    // ========== 配置验证测试 ==========

    @Test
    public void testRequireKeyCodec() {
        assertThrows(IllegalStateException.class, () -> {
            RogueMap.<String, String>builder()
                    .valueCodec(new StringCodec())
                    .maxMemory(10 * 1024 * 1024)
                    .build();
        });
    }

    @Test
    public void testRequireValueCodec() {
        assertThrows(IllegalStateException.class, () -> {
            RogueMap.<String, String>builder()
                    .keyCodec(new StringCodec())
                    .maxMemory(10 * 1024 * 1024)
                    .build();
        });
    }

    @Test
    public void testInvalidMaxMemory() {
        assertThrows(IllegalArgumentException.class, () -> {
            RogueMap.<String, String>builder()
                    .keyCodec(new StringCodec())
                    .valueCodec(new StringCodec())
                    .maxMemory(0)
                    .build();
        });
    }

    @Test
    public void testInvalidInitialCapacity() {
        assertThrows(IllegalArgumentException.class, () -> {
            RogueMap.<String, String>builder()
                    .keyCodec(new StringCodec())
                    .valueCodec(new StringCodec())
                    .maxMemory(10 * 1024 * 1024)
                    .initialCapacity(0)
                    .build();
        });
    }

    // ========== 辅助方法 ==========

    private String createLargeString(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append('x');
        }
        return sb.toString();
    }

    /**
     * 测试用户对象
     */
    public static class TestUser {
        private long id;
        private String name;
        private int age;

        public TestUser() {}

        public TestUser(long id, String name, int age) {
            this.id = id;
            this.name = name;
            this.age = age;
        }

        public long getId() { return id; }
        public String getName() { return name; }
        public int getAge() { return age; }
    }
}
