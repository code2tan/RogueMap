package com.yomahub.roguemap.mmap;

import com.yomahub.roguemap.RogueMap;
import com.yomahub.roguemap.serialization.KryoObjectCodec;
import com.yomahub.roguemap.serialization.PrimitiveCodecs;
import com.yomahub.roguemap.serialization.StringCodec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MMAP 持久化功能测试
 *
 * 测试所有 MMAP 持久化相关功能,包括:
 * - 基本的持久化和恢复
 * - 多次会话的数据持久化
 * - 更新操作的持久化
 * - 删除操作的持久化
 * - 不同数据类型的持久化
 * - 不同索引类型的持久化
 * - 空 map 的持久化
 * - 大数据量持久化
 * - 文件管理和异常处理
 */
public class MmapFunctionalTest {

    private static final String TEST_FILE = "target/test-mmap-functional.db";

    @BeforeEach
    public void setUp() {
        deleteTestFile();
    }

    @AfterEach
    public void tearDown() {
        deleteTestFile();
    }

    private void deleteTestFile() {
        File file = new File(TEST_FILE);
        if (file.exists()) {
            file.delete();
        }
    }

    // ========== 基本持久化测试 ==========

    @Test
    public void testBasicPersistence() {
        // 第一阶段：写入数据
        RogueMap<String, String> map1 = RogueMap.<String, String>builder()
                .persistent(TEST_FILE)
                .allocateSize(10 * 1024 * 1024L)
                .keyCodec(new StringCodec())
                .valueCodec(new StringCodec())
                .build();

        map1.put("key1", "value1");
        map1.put("key2", "value2");
        map1.put("key3", "value3");
        map1.close();

        // 验证文件存在
        File file = new File(TEST_FILE);
        assertTrue(file.exists());
        assertTrue(file.length() > 0);

        // 第二阶段：重新打开并验证
        RogueMap<String, String> map2 = RogueMap.<String, String>builder()
                .persistent(TEST_FILE)
                .allocateSize(10 * 1024 * 1024L)
                .keyCodec(new StringCodec())
                .valueCodec(new StringCodec())
                .build();

        assertEquals("value1", map2.get("key1"));
        assertEquals("value2", map2.get("key2"));
        assertEquals("value3", map2.get("key3"));
        assertEquals(3, map2.size());

        map2.close();
    }

    @Test
    public void testSingleEntry() {
        // 第一阶段：写入数据
        RogueMap<String, String> map1 = RogueMap.<String, String>builder()
                .persistent(TEST_FILE)
                .allocateSize(10 * 1024 * 1024L)
                .keyCodec(new StringCodec())
                .valueCodec(new StringCodec())
                .build();

        map1.put("single", "value");
        map1.close();

        // 第二阶段：读取数据
        RogueMap<String, String> map2 = RogueMap.<String, String>builder()
                .persistent(TEST_FILE)
                .allocateSize(10 * 1024 * 1024L)
                .keyCodec(new StringCodec())
                .valueCodec(new StringCodec())
                .build();

        assertEquals("value", map2.get("single"));
        assertEquals(1, map2.size());
        map2.close();
    }

    @Test
    public void testEmptyMapPersistence() {
        // 测试空 map 的持久化
        RogueMap<String, String> map1 = RogueMap.<String, String>builder()
                .persistent(TEST_FILE)
                .allocateSize(10 * 1024 * 1024L)
                .keyCodec(new StringCodec())
                .valueCodec(new StringCodec())
                .build();

        assertEquals(0, map1.size());
        map1.close();

        // 重新打开空 map
        RogueMap<String, String> map2 = RogueMap.<String, String>builder()
                .persistent(TEST_FILE)
                .allocateSize(10 * 1024 * 1024L)
                .keyCodec(new StringCodec())
                .valueCodec(new StringCodec())
                .build();

        assertEquals(0, map2.size());
        assertNull(map2.get("anykey"));
        map2.close();
    }

    // ========== 更新操作持久化测试 ==========

    @Test
    public void testUpdatePersistence() {
        // 第一阶段：写入初始数据
        RogueMap<String, String> map1 = RogueMap.<String, String>builder()
                .persistent(TEST_FILE)
                .allocateSize(10 * 1024 * 1024L)
                .keyCodec(new StringCodec())
                .valueCodec(new StringCodec())
                .build();

        map1.put("key1", "value1");
        map1.put("key2", "value2");
        map1.close();

        // 第二阶段：重新打开,更新和添加数据
        RogueMap<String, String> map2 = RogueMap.<String, String>builder()
                .persistent(TEST_FILE)
                .allocateSize(10 * 1024 * 1024L)
                .keyCodec(new StringCodec())
                .valueCodec(new StringCodec())
                .build();

        assertEquals("value1", map2.get("key1"));
        map2.put("key1", "updated_value1");  // 更新
        map2.put("key3", "value3");          // 新增
        map2.close();

        // 第三阶段：再次打开,验证更新和新增的数据
        RogueMap<String, String> map3 = RogueMap.<String, String>builder()
                .persistent(TEST_FILE)
                .allocateSize(10 * 1024 * 1024L)
                .keyCodec(new StringCodec())
                .valueCodec(new StringCodec())
                .build();

        assertEquals("updated_value1", map3.get("key1"));
        assertEquals("value2", map3.get("key2"));
        assertEquals("value3", map3.get("key3"));
        assertEquals(3, map3.size());
        map3.close();
    }

    @Test
    public void testMultipleUpdates() {
        // 测试同一个键的多次更新
        RogueMap<String, String> map1 = RogueMap.<String, String>builder()
                .persistent(TEST_FILE)
                .allocateSize(10 * 1024 * 1024L)
                .keyCodec(new StringCodec())
                .valueCodec(new StringCodec())
                .build();

        map1.put("key", "value1");
        map1.close();

        // 第二次打开并更新
        RogueMap<String, String> map2 = RogueMap.<String, String>builder()
                .persistent(TEST_FILE)
                .allocateSize(10 * 1024 * 1024L)
                .keyCodec(new StringCodec())
                .valueCodec(new StringCodec())
                .build();
        map2.put("key", "value2");
        map2.close();

        // 第三次打开并更新
        RogueMap<String, String> map3 = RogueMap.<String, String>builder()
                .persistent(TEST_FILE)
                .allocateSize(10 * 1024 * 1024L)
                .keyCodec(new StringCodec())
                .valueCodec(new StringCodec())
                .build();
        map3.put("key", "value3");
        map3.close();

        // 验证最终值
        RogueMap<String, String> map4 = RogueMap.<String, String>builder()
                .persistent(TEST_FILE)
                .allocateSize(10 * 1024 * 1024L)
                .keyCodec(new StringCodec())
                .valueCodec(new StringCodec())
                .build();
        assertEquals("value3", map4.get("key"));
        map4.close();
    }

    // ========== 删除操作持久化测试 ==========

    @Test
    public void testDeletePersistence() {
        // 第一阶段：写入数据
        RogueMap<String, String> map1 = RogueMap.<String, String>builder()
                .persistent(TEST_FILE)
                .allocateSize(10 * 1024 * 1024L)
                .keyCodec(new StringCodec())
                .valueCodec(new StringCodec())
                .build();

        map1.put("key1", "value1");
        map1.put("key2", "value2");
        map1.put("key3", "value3");
        map1.close();

        // 第二阶段：重新打开,删除数据
        RogueMap<String, String> map2 = RogueMap.<String, String>builder()
                .persistent(TEST_FILE)
                .allocateSize(10 * 1024 * 1024L)
                .keyCodec(new StringCodec())
                .valueCodec(new StringCodec())
                .build();

        assertEquals("value2", map2.remove("key2"));
        assertEquals(2, map2.size());
        map2.close();

        // 第三阶段：再次打开,验证删除
        RogueMap<String, String> map3 = RogueMap.<String, String>builder()
                .persistent(TEST_FILE)
                .allocateSize(10 * 1024 * 1024L)
                .keyCodec(new StringCodec())
                .valueCodec(new StringCodec())
                .build();

        assertEquals("value1", map3.get("key1"));
        assertNull(map3.get("key2"));  // 已删除
        assertEquals("value3", map3.get("key3"));
        assertEquals(2, map3.size());
        map3.close();
    }

    @Test
    public void testDeleteAllEntries() {
        // 测试删除所有条目
        RogueMap<String, String> map1 = RogueMap.<String, String>builder()
                .persistent(TEST_FILE)
                .allocateSize(10 * 1024 * 1024L)
                .keyCodec(new StringCodec())
                .valueCodec(new StringCodec())
                .build();

        map1.put("key1", "value1");
        map1.put("key2", "value2");
        map1.close();

        // 删除所有条目
        RogueMap<String, String> map2 = RogueMap.<String, String>builder()
                .persistent(TEST_FILE)
                .allocateSize(10 * 1024 * 1024L)
                .keyCodec(new StringCodec())
                .valueCodec(new StringCodec())
                .build();
        map2.remove("key1");
        map2.remove("key2");
        map2.close();

        // 验证为空
        RogueMap<String, String> map3 = RogueMap.<String, String>builder()
                .persistent(TEST_FILE)
                .allocateSize(10 * 1024 * 1024L)
                .keyCodec(new StringCodec())
                .valueCodec(new StringCodec())
                .build();
        assertEquals(0, map3.size());
        map3.close();
    }

    // ========== 多次会话测试 ==========

    @Test
    public void testMultipleSessions() {
        // 模拟多次启停场景
        for (int session = 0; session < 5; session++) {
            RogueMap<String, String> map = RogueMap.<String, String>builder()
                    .persistent(TEST_FILE)
                    .allocateSize(10 * 1024 * 1024L)
                    .keyCodec(new StringCodec())
                    .valueCodec(new StringCodec())
                    .build();

            // 添加本次会话的数据
            map.put("session" + session, "value" + session);

            // 验证之前会话的数据仍然存在
            for (int i = 0; i < session; i++) {
                assertEquals("value" + i, map.get("session" + i));
            }

            assertEquals(session + 1, map.size());
            map.close();
        }
    }

    @Test
    public void testSessionWithMixedOperations() {
        // 第一个会话：添加数据
        RogueMap<String, String> map1 = RogueMap.<String, String>builder()
                .persistent(TEST_FILE)
                .allocateSize(10 * 1024 * 1024L)
                .keyCodec(new StringCodec())
                .valueCodec(new StringCodec())
                .build();
        for (int i = 0; i < 10; i++) {
            map1.put("key" + i, "value" + i);
        }
        map1.close();

        // 第二个会话：删除一些,更新一些,添加一些
        RogueMap<String, String> map2 = RogueMap.<String, String>builder()
                .persistent(TEST_FILE)
                .allocateSize(10 * 1024 * 1024L)
                .keyCodec(new StringCodec())
                .valueCodec(new StringCodec())
                .build();
        map2.remove("key0");
        map2.remove("key1");
        map2.put("key2", "updated_value2");
        map2.put("key10", "value10");
        map2.close();

        // 第三个会话：验证
        RogueMap<String, String> map3 = RogueMap.<String, String>builder()
                .persistent(TEST_FILE)
                .allocateSize(10 * 1024 * 1024L)
                .keyCodec(new StringCodec())
                .valueCodec(new StringCodec())
                .build();
        assertNull(map3.get("key0"));
        assertNull(map3.get("key1"));
        assertEquals("updated_value2", map3.get("key2"));
        assertEquals("value3", map3.get("key3"));
        assertEquals("value10", map3.get("key10"));
        assertEquals(9, map3.size());
        map3.close();
    }

    // ========== 不同数据类型测试 ==========

    @Test
    public void testLongKeyValuePersistence() {
        String testFile = "target/test-mmap-long.db";

        try {
            // 写入
            RogueMap<Long, Long> map1 = RogueMap.<Long, Long>builder()
                    .persistent(testFile)
                    .allocateSize(10 * 1024 * 1024L)
                    .keyCodec(PrimitiveCodecs.LONG)
                    .valueCodec(PrimitiveCodecs.LONG)
                    .build();

            for (long i = 0; i < 100; i++) {
                map1.put(i, i * 1000);
            }
            map1.close();

            // 读取验证
            RogueMap<Long, Long> map2 = RogueMap.<Long, Long>builder()
                    .persistent(testFile)
                    .allocateSize(10 * 1024 * 1024L)
                    .keyCodec(PrimitiveCodecs.LONG)
                    .valueCodec(PrimitiveCodecs.LONG)
                    .build();

            assertEquals(100, map2.size());
            for (long i = 0; i < 100; i++) {
                assertEquals(i * 1000, map2.get(i));
            }
            map2.close();
        } finally {
            new File(testFile).delete();
        }
    }

    @Test
    public void testIntegerKeyValuePersistence() {
        String testFile = "target/test-mmap-int.db";

        try {
            RogueMap<Integer, Integer> map1 = RogueMap.<Integer, Integer>builder()
                    .persistent(testFile)
                    .allocateSize(10 * 1024 * 1024L)
                    .keyCodec(PrimitiveCodecs.INTEGER)
                    .valueCodec(PrimitiveCodecs.INTEGER)
                    .build();

            for (int i = 0; i < 50; i++) {
                map1.put(i, i * 100);
            }
            map1.close();

            RogueMap<Integer, Integer> map2 = RogueMap.<Integer, Integer>builder()
                    .persistent(testFile)
                    .allocateSize(10 * 1024 * 1024L)
                    .keyCodec(PrimitiveCodecs.INTEGER)
                    .valueCodec(PrimitiveCodecs.INTEGER)
                    .build();

            assertEquals(50, map2.size());
            for (int i = 0; i < 50; i++) {
                assertEquals(i * 100, map2.get(i));
            }
            map2.close();
        } finally {
            new File(testFile).delete();
        }
    }

    @Test
    public void testObjectPersistence() {
        String testFile = "target/test-mmap-object.db";

        try {
            RogueMap<String, TestUser> map1 = RogueMap.<String, TestUser>builder()
                    .persistent(testFile)
                    .allocateSize(10 * 1024 * 1024L)
                    .keyCodec(new StringCodec())
                    .valueCodec(KryoObjectCodec.create(TestUser.class))
                    .build();

            TestUser user1 = new TestUser(1L, "Alice", 25);
            TestUser user2 = new TestUser(2L, "Bob", 30);
            map1.put("user1", user1);
            map1.put("user2", user2);
            map1.close();

            RogueMap<String, TestUser> map2 = RogueMap.<String, TestUser>builder()
                    .persistent(testFile)
                    .allocateSize(10 * 1024 * 1024L)
                    .keyCodec(new StringCodec())
                    .valueCodec(KryoObjectCodec.create(TestUser.class))
                    .build();

            TestUser retrieved1 = map2.get("user1");
            assertEquals(1L, retrieved1.getId());
            assertEquals("Alice", retrieved1.getName());
            assertEquals(25, retrieved1.getAge());

            TestUser retrieved2 = map2.get("user2");
            assertEquals(2L, retrieved2.getId());
            assertEquals("Bob", retrieved2.getName());
            assertEquals(30, retrieved2.getAge());

            map2.close();
        } finally {
            new File(testFile).delete();
        }
    }

    // ========== 不同索引类型测试 ==========

    @Test
    public void testBasicIndexPersistence() {
        String testFile = "target/test-mmap-basic-index.db";

        try {
            RogueMap<String, String> map1 = RogueMap.<String, String>builder()
                    .persistent(testFile)
                    .allocateSize(10 * 1024 * 1024L)
                    .basicIndex()
                    .keyCodec(new StringCodec())
                    .valueCodec(new StringCodec())
                    .build();

            for (int i = 0; i < 100; i++) {
                map1.put("key" + i, "value" + i);
            }
            map1.close();

            RogueMap<String, String> map2 = RogueMap.<String, String>builder()
                    .persistent(testFile)
                    .allocateSize(10 * 1024 * 1024L)
                    .basicIndex()
                    .keyCodec(new StringCodec())
                    .valueCodec(new StringCodec())
                    .build();

            assertEquals(100, map2.size());
            for (int i = 0; i < 100; i++) {
                assertEquals("value" + i, map2.get("key" + i));
            }
            map2.close();
        } finally {
            new File(testFile).delete();
        }
    }

    @Test
    public void testSegmentedIndexPersistence() {
        String testFile = "target/test-mmap-segmented-index.db";

        try {
            RogueMap<String, String> map1 = RogueMap.<String, String>builder()
                    .persistent(testFile)
                    .allocateSize(10 * 1024 * 1024L)
                    .segmentedIndex(32)
                    .keyCodec(new StringCodec())
                    .valueCodec(new StringCodec())
                    .build();

            for (int i = 0; i < 500; i++) {
                map1.put("seg_key" + i, "seg_value" + i);
            }
            map1.close();

            RogueMap<String, String> map2 = RogueMap.<String, String>builder()
                    .persistent(testFile)
                    .allocateSize(10 * 1024 * 1024L)
                    .segmentedIndex(32)
                    .keyCodec(new StringCodec())
                    .valueCodec(new StringCodec())
                    .build();

            assertEquals(500, map2.size());
            for (int i = 0; i < 500; i++) {
                assertEquals("seg_value" + i, map2.get("seg_key" + i));
            }
            map2.close();
        } finally {
            new File(testFile).delete();
        }
    }

    // ========== 大数据量测试 ==========

    @Test
    public void testLargeDataPersistence() {
        int count = 10000;

        RogueMap<String, String> map1 = RogueMap.<String, String>builder()
                .persistent(TEST_FILE)
                .allocateSize(100 * 1024 * 1024L)
                .keyCodec(new StringCodec())
                .valueCodec(new StringCodec())
                .build();

        for (int i = 0; i < count; i++) {
            map1.put("key" + i, "value" + i + "_with_some_padding_to_make_it_larger");
        }
        map1.close();

        RogueMap<String, String> map2 = RogueMap.<String, String>builder()
                .persistent(TEST_FILE)
                .allocateSize(100 * 1024 * 1024L)
                .keyCodec(new StringCodec())
                .valueCodec(new StringCodec())
                .build();

        assertEquals(count, map2.size());
        for (int i = 0; i < count; i++) {
            assertEquals("value" + i + "_with_some_padding_to_make_it_larger", map2.get("key" + i));
        }
        map2.close();
    }

    @Test
    public void testLargeValuePersistence() {
        String largeValue = createLargeString(10000);

        RogueMap<String, String> map1 = RogueMap.<String, String>builder()
                .persistent(TEST_FILE)
                .allocateSize(50 * 1024 * 1024L)
                .keyCodec(new StringCodec())
                .valueCodec(new StringCodec())
                .build();

        map1.put("large_key", largeValue);
        map1.close();

        RogueMap<String, String> map2 = RogueMap.<String, String>builder()
                .persistent(TEST_FILE)
                .allocateSize(50 * 1024 * 1024L)
                .keyCodec(new StringCodec())
                .valueCodec(new StringCodec())
                .build();

        assertEquals(largeValue, map2.get("large_key"));
        map2.close();
    }

    // ========== 其他功能测试 ==========

    @Test
    public void testContainsKeyAfterPersistence() {
        RogueMap<String, String> map1 = RogueMap.<String, String>builder()
                .persistent(TEST_FILE)
                .allocateSize(10 * 1024 * 1024L)
                .keyCodec(new StringCodec())
                .valueCodec(new StringCodec())
                .build();

        map1.put("exists", "yes");
        map1.close();

        RogueMap<String, String> map2 = RogueMap.<String, String>builder()
                .persistent(TEST_FILE)
                .allocateSize(10 * 1024 * 1024L)
                .keyCodec(new StringCodec())
                .valueCodec(new StringCodec())
                .build();

        assertTrue(map2.containsKey("exists"));
        assertFalse(map2.containsKey("notexists"));
        map2.close();
    }

    @Test
    public void testSpecialCharactersPersistence() {
        RogueMap<String, String> map1 = RogueMap.<String, String>builder()
                .persistent(TEST_FILE)
                .allocateSize(10 * 1024 * 1024L)
                .keyCodec(new StringCodec())
                .valueCodec(new StringCodec())
                .build();

        map1.put("key中文", "中文值");
        map1.put("key🎉", "emoji_value");
        map1.put("key\n\t", "special_chars");
        map1.close();

        RogueMap<String, String> map2 = RogueMap.<String, String>builder()
                .persistent(TEST_FILE)
                .allocateSize(10 * 1024 * 1024L)
                .keyCodec(new StringCodec())
                .valueCodec(new StringCodec())
                .build();

        assertEquals("中文值", map2.get("key中文"));
        assertEquals("emoji_value", map2.get("key🎉"));
        assertEquals("special_chars", map2.get("key\n\t"));
        map2.close();
    }

    // ========== 异常处理测试 ==========

    @Test
    public void testRequireFilePath() {
        assertThrows(IllegalStateException.class, () -> {
            RogueMap.<String, String>builder()
                    .mmap()
                    .keyCodec(new StringCodec())
                    .valueCodec(new StringCodec())
                    .build();
        });
    }

    @Test
    public void testInvalidAllocateSize() {
        assertThrows(IllegalArgumentException.class, () -> {
            RogueMap.<String, String>builder()
                    .persistent(TEST_FILE)
                    .allocateSize(0)
                    .keyCodec(new StringCodec())
                    .valueCodec(new StringCodec())
                    .build();
        });
    }

    @Test
    public void testFileCreation() {
        RogueMap<String, String> map = RogueMap.<String, String>builder()
                .persistent(TEST_FILE)
                .allocateSize(10 * 1024 * 1024L)
                .keyCodec(new StringCodec())
                .valueCodec(new StringCodec())
                .build();

        File file = new File(TEST_FILE);
        assertTrue(file.exists());
        assertEquals(10 * 1024 * 1024L, file.length());

        map.close();
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
