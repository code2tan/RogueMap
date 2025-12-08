package com.yomahub.roguemap.mmap;

import com.yomahub.roguemap.RogueMap;
import com.yomahub.roguemap.serialization.KryoObjectCodec;
import com.yomahub.roguemap.serialization.PrimitiveCodecs;
import com.yomahub.roguemap.serialization.StringCodec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.File;

/**
 * MMAP 持久化性能测试
 *
 * 测试 MMAP 模式下的各种性能指标,包括:
 * - 持久化写入性能
 * - 持久化读取性能
 * - 恢复性能
 * - 多次会话性能
 * - 大数据量性能
 * - 文件 I/O 性能
 */
public class MmapPerformanceTest {

    private static final String TEST_FILE = "target/test-mmap-performance.db";

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

    /**
     * 测试首次写入性能
     */
    @Test
    public void testInitialWritePerformance() {
        deleteTestFile();
        int count = 500_000;
        System.out.println("\n========== MMAP 首次写入性能测试 ==========");
        System.out.println("数据量: " + count);

        RogueMap<Long, Long> map = RogueMap.<Long, Long>builder()
                .persistent(TEST_FILE)
                .allocateSize(1024L * 1024 * 1024)
                .keyCodec(PrimitiveCodecs.LONG)
                .valueCodec(PrimitiveCodecs.LONG)
                .initialCapacity(count)
                .build();

        try {
            long startTime = System.currentTimeMillis();

            for (long i = 1; i <= count; i++) {
                map.put(i, i * 100);
            }

            long insertDuration = System.currentTimeMillis() - startTime;
            System.out.println("写入耗时: " + insertDuration + " ms");
            System.out.println("写入吞吐量: " + (count * 1000L / insertDuration) + " ops/sec");

            // 关闭并持久化
            startTime = System.currentTimeMillis();
            map.close();
            long closeDuration = System.currentTimeMillis() - startTime;

            System.out.println("持久化耗时(close): " + closeDuration + " ms");
            System.out.println("总耗时: " + (insertDuration + closeDuration) + " ms");
        } catch (Exception e) {
            if (map != null) {
                map.close();
            }
            throw e;
        }
    }

    /**
     * 测试恢复性能
     */
    @Test
    public void testRecoveryPerformance() {
        deleteTestFile();
        int count = 500_000;
        System.out.println("\n========== MMAP 恢复性能测试 ==========");
        System.out.println("数据量: " + count);

        // 第一阶段：写入数据
        RogueMap<Long, Long> map1 = RogueMap.<Long, Long>builder()
                .persistent(TEST_FILE)
                .allocateSize(1024L * 1024 * 1024)
                .keyCodec(PrimitiveCodecs.LONG)
                .valueCodec(PrimitiveCodecs.LONG)
                .initialCapacity(count)
                .build();

        for (long i = 1; i <= count; i++) {
            map1.put(i, i * 100);
        }
        map1.close();

        System.out.println("数据已写入,开始测试恢复...");

        // 第二阶段：恢复并读取
        long startTime = System.currentTimeMillis();

        RogueMap<Long, Long> map2 = RogueMap.<Long, Long>builder()
                .persistent(TEST_FILE)
                .allocateSize(1024L * 1024 * 1024)
                .keyCodec(PrimitiveCodecs.LONG)
                .valueCodec(PrimitiveCodecs.LONG)
                .initialCapacity(count)
                .build();

        long recoveryDuration = System.currentTimeMillis() - startTime;
        System.out.println("恢复耗时: " + recoveryDuration + " ms");
        System.out.println("恢复数据量: " + map2.size() + " 条");

        // 验证数据
        startTime = System.currentTimeMillis();
        int verifyCount = 0;
        for (long i = 1; i <= count; i++) {
            Long value = map2.get(i);
            if (value != null && value == i * 100) {
                verifyCount++;
            }
        }
        long verifyDuration = System.currentTimeMillis() - startTime;

        System.out.println("验证耗时: " + verifyDuration + " ms");
        System.out.println("验证成功: " + verifyCount + "/" + count);

        map2.close();
    }

    /**
     * 测试持久化读取性能
     */
    @Test
    public void testPersistentReadPerformance() {
        deleteTestFile();
        int count = 500_000;
        System.out.println("\n========== MMAP 持久化读取性能测试 ==========");
        System.out.println("数据量: " + count);

        // 准备数据
        RogueMap<Long, Long> map1 = RogueMap.<Long, Long>builder()
                .persistent(TEST_FILE)
                .allocateSize(1024L * 1024 * 1024)
                .keyCodec(PrimitiveCodecs.LONG)
                .valueCodec(PrimitiveCodecs.LONG)
                .initialCapacity(count)
                .build();

        for (long i = 1; i <= count; i++) {
            map1.put(i, i * 100);
        }
        map1.close();

        // 重新打开并测试读取
        RogueMap<Long, Long> map2 = RogueMap.<Long, Long>builder()
                .persistent(TEST_FILE)
                .allocateSize(1024L * 1024 * 1024)
                .keyCodec(PrimitiveCodecs.LONG)
                .valueCodec(PrimitiveCodecs.LONG)
                .initialCapacity(count)
                .build();

        try {
            // 顺序读取
            long startTime = System.currentTimeMillis();
            int hitCount = 0;

            for (long i = 1; i <= count; i++) {
                Long value = map2.get(i);
                if (value != null) {
                    hitCount++;
                }
            }

            long duration = System.currentTimeMillis() - startTime;
            System.out.println("顺序读取耗时: " + duration + " ms");
            System.out.println("读取吞吐量: " + (count * 1000L / duration) + " ops/sec");
            System.out.println("命中率: " + (hitCount * 100.0 / count) + "%");

            // 随机读取
            startTime = System.currentTimeMillis();
            hitCount = 0;
            int randomCount = 100_000;

            for (int i = 0; i < randomCount; i++) {
                long randomKey = (long) (Math.random() * count) + 1;
                Long value = map2.get(randomKey);
                if (value != null) {
                    hitCount++;
                }
            }

            duration = System.currentTimeMillis() - startTime;
            System.out.println("\n随机读取耗时: " + duration + " ms (读取 " + randomCount + " 次)");
            System.out.println("读取吞吐量: " + (randomCount * 1000L / duration) + " ops/sec");
            System.out.println("命中率: " + (hitCount * 100.0 / randomCount) + "%");
        } finally {
            map2.close();
        }
    }

    /**
     * 测试增量更新性能
     */
    @Test
    public void testIncrementalUpdatePerformance() {
        deleteTestFile();
        int initialCount = 100_000;
        int updateCount = 50_000;
        System.out.println("\n========== MMAP 增量更新性能测试 ==========");
        System.out.println("初始数据量: " + initialCount);
        System.out.println("更新数据量: " + updateCount);

        // 第一阶段：写入初始数据
        RogueMap<Long, Long> map1 = RogueMap.<Long, Long>builder()
                .persistent(TEST_FILE)
                .allocateSize(1024L * 1024 * 1024)
                .keyCodec(PrimitiveCodecs.LONG)
                .valueCodec(PrimitiveCodecs.LONG)
                .initialCapacity(initialCount)
                .build();

        for (long i = 1; i <= initialCount; i++) {
            map1.put(i, i * 100);
        }
        map1.close();

        // 第二阶段：重新打开并更新
        RogueMap<Long, Long> map2 = RogueMap.<Long, Long>builder()
                .persistent(TEST_FILE)
                .allocateSize(1024L * 1024 * 1024)
                .keyCodec(PrimitiveCodecs.LONG)
                .valueCodec(PrimitiveCodecs.LONG)
                .initialCapacity(initialCount)
                .build();

        try {
            long startTime = System.currentTimeMillis();

            // 更新一半旧数据,新增一半新数据
            for (long i = 1; i <= updateCount / 2; i++) {
                map2.put(i, i * 200);  // 更新
            }
            for (long i = initialCount + 1; i <= initialCount + updateCount / 2; i++) {
                map2.put(i, i * 100);  // 新增
            }

            long updateDuration = System.currentTimeMillis() - startTime;
            System.out.println("更新耗时: " + updateDuration + " ms");
            System.out.println("更新吞吐量: " + (updateCount * 1000L / updateDuration) + " ops/sec");

            startTime = System.currentTimeMillis();
            map2.close();
            long closeDuration = System.currentTimeMillis() - startTime;

            System.out.println("持久化耗时: " + closeDuration + " ms");
            System.out.println("总耗时: " + (updateDuration + closeDuration) + " ms");
        } catch (Exception e) {
            map2.close();
            throw e;
        }
    }

    /**
     * 测试多次会话性能
     */
    @Test
    public void testMultipleSessionsPerformance() {
        deleteTestFile();
        int sessionsCount = 5;
        int itemsPerSession = 10_000;
        System.out.println("\n========== MMAP 多次会话性能测试 ==========");
        System.out.println("会话数: " + sessionsCount);
        System.out.println("每会话数据量: " + itemsPerSession);

        long totalDuration = 0;

        for (int session = 0; session < sessionsCount; session++) {
            long startTime = System.currentTimeMillis();

            RogueMap<String, String> map = RogueMap.<String, String>builder()
                    .persistent(TEST_FILE)
                    .allocateSize(1024L * 1024 * 1024)
                    .keyCodec(new StringCodec())
                    .valueCodec(new StringCodec())
                    .segmentedIndex(32)
                    .build();

            // 添加本次会话数据
            for (int i = 0; i < itemsPerSession; i++) {
                map.put("session" + session + "_key" + i, "value" + i);
            }

            map.close();

            long sessionDuration = System.currentTimeMillis() - startTime;
            totalDuration += sessionDuration;

            System.out.println("会话 " + session + " 耗时: " + sessionDuration + " ms");
        }

        System.out.println("\n总耗时: " + totalDuration + " ms");
        System.out.println("平均每会话: " + (totalDuration / sessionsCount) + " ms");
        System.out.println("总吞吐量: " + (sessionsCount * itemsPerSession * 1000L / totalDuration) + " ops/sec");

        // 验证最终数据
        RogueMap<String, String> finalMap = RogueMap.<String, String>builder()
                .persistent(TEST_FILE)
                .allocateSize(1024L * 1024 * 1024)
                .keyCodec(new StringCodec())
                .valueCodec(new StringCodec())
                .segmentedIndex(32)
                .build();

        System.out.println("最终数据量: " + finalMap.size());
        finalMap.close();
    }

    /**
     * 测试大对象持久化性能
     */
    @Test
    public void testLargeObjectPersistencePerformance() {
        deleteTestFile();
        int count = 500_000;
        System.out.println("\n========== MMAP 大对象持久化性能测试 ==========");
        System.out.println("数据量: " + count);

        // 写入阶段
        RogueMap<Long, TestUserData> map1 = RogueMap.<Long, TestUserData>builder()
                .persistent(TEST_FILE)
                .allocateSize(1024L * 1024 * 1024)
                .keyCodec(PrimitiveCodecs.LONG)
                .valueCodec(KryoObjectCodec.create(TestUserData.class))
                .initialCapacity(count)
                .build();

        long startTime = System.currentTimeMillis();

        for (long i = 1; i <= count; i++) {
            TestUserData userData = createUserData(i);
            map1.put(i, userData);
        }

        long insertDuration = System.currentTimeMillis() - startTime;

        startTime = System.currentTimeMillis();
        map1.close();
        long closeDuration = System.currentTimeMillis() - startTime;

        System.out.println("写入耗时: " + insertDuration + " ms");
        System.out.println("持久化耗时: " + closeDuration + " ms");
        System.out.println("总耗时: " + (insertDuration + closeDuration) + " ms");
        System.out.println("写入吞吐量: " + (count * 1000L / insertDuration) + " ops/sec");

        // 读取阶段
        startTime = System.currentTimeMillis();

        RogueMap<Long, TestUserData> map2 = RogueMap.<Long, TestUserData>builder()
                .persistent(TEST_FILE)
                .allocateSize(1024L * 1024 * 1024)
                .keyCodec(PrimitiveCodecs.LONG)
                .valueCodec(KryoObjectCodec.create(TestUserData.class))
                .initialCapacity(count)
                .build();

        long recoveryDuration = System.currentTimeMillis() - startTime;
        System.out.println("\n恢复耗时: " + recoveryDuration + " ms");

        startTime = System.currentTimeMillis();
        int verifyCount = 0;

        for (long i = 1; i <= count; i++) {
            TestUserData userData = map2.get(i);
            if (userData != null && userData.getUserId() == i) {
                verifyCount++;
            }
        }

        long readDuration = System.currentTimeMillis() - startTime;
        System.out.println("读取耗时: " + readDuration + " ms");
        System.out.println("读取吞吐量: " + (count * 1000L / readDuration) + " ops/sec");
        System.out.println("验证成功: " + verifyCount + "/" + count);

        map2.close();
    }

    /**
     * 测试分段索引持久化性能
     */
    @Test
    public void testSegmentedIndexPersistencePerformance() {
        deleteTestFile();
        int count = 500_000;
        System.out.println("\n========== MMAP 分段索引持久化性能测试 ==========");
        System.out.println("数据量: " + count);
        System.out.println("段数: 64");

        // 写入阶段
        RogueMap<String, String> map1 = RogueMap.<String, String>builder()
                .persistent(TEST_FILE)
                .allocateSize(1024L * 1024 * 1024)
                .keyCodec(new StringCodec())
                .valueCodec(new StringCodec())
                .segmentedIndex(64)
                .initialCapacity(count / 64)
                .build();

        long startTime = System.currentTimeMillis();

        for (int i = 0; i < count; i++) {
            map1.put("key" + i, "value" + i);
        }

        long insertDuration = System.currentTimeMillis() - startTime;

        startTime = System.currentTimeMillis();
        map1.close();
        long closeDuration = System.currentTimeMillis() - startTime;

        System.out.println("写入耗时: " + insertDuration + " ms");
        System.out.println("持久化耗时: " + closeDuration + " ms");
        System.out.println("总耗时: " + (insertDuration + closeDuration) + " ms");

        // 恢复阶段
        startTime = System.currentTimeMillis();

        RogueMap<String, String> map2 = RogueMap.<String, String>builder()
                .persistent(TEST_FILE)
                .allocateSize(1024L * 1024 * 1024)
                .keyCodec(new StringCodec())
                .valueCodec(new StringCodec())
                .segmentedIndex(64)
                .initialCapacity(count / 64)
                .build();

        long recoveryDuration = System.currentTimeMillis() - startTime;
        System.out.println("\n恢复耗时: " + recoveryDuration + " ms");
        System.out.println("恢复数据量: " + map2.size());

        map2.close();
    }

    /**
     * 测试文件大小和性能关系
     */
    @Test
    public void testFileSizePerformance() {
        System.out.println("\n========== MMAP 文件大小性能测试 ==========");

        long[] fileSizes = {10L * 1024 * 1024, 100L * 1024 * 1024, 1024L * 1024 * 1024};
        String[] sizeNames = {"10MB", "100MB", "1GB"};
        int count = 10_000;

        for (int i = 0; i < fileSizes.length; i++) {
            String testFile = "target/test-size-" + i + ".db";
            System.out.println("\n文件大小: " + sizeNames[i]);

            try {
                long startTime = System.currentTimeMillis();

                RogueMap<Long, Long> map = RogueMap.<Long, Long>builder()
                        .persistent(testFile)
                        .allocateSize(fileSizes[i])
                        .keyCodec(PrimitiveCodecs.LONG)
                        .valueCodec(PrimitiveCodecs.LONG)
                        .initialCapacity(count)
                        .build();

                for (long j = 1; j <= count; j++) {
                    map.put(j, j * 100);
                }

                map.close();

                long duration = System.currentTimeMillis() - startTime;
                System.out.println("  耗时: " + duration + " ms");
                System.out.println("  吞吐量: " + (count * 1000L / duration) + " ops/sec");
            } finally {
                new File(testFile).delete();
            }
        }
    }

    /**
     * 测试删除操作性能
     */
    @Test
    public void testDeletePerformanceWithPersistence() {
        deleteTestFile();
        int count = 100_000;
        System.out.println("\n========== MMAP 删除操作性能测试 ==========");
        System.out.println("数据量: " + count);

        // 准备数据
        RogueMap<Long, Long> map1 = RogueMap.<Long, Long>builder()
                .persistent(TEST_FILE)
                .allocateSize(1024L * 1024 * 1024)
                .keyCodec(PrimitiveCodecs.LONG)
                .valueCodec(PrimitiveCodecs.LONG)
                .initialCapacity(count)
                .build();

        for (long i = 1; i <= count; i++) {
            map1.put(i, i * 100);
        }
        map1.close();

        // 重新打开并删除
        RogueMap<Long, Long> map2 = RogueMap.<Long, Long>builder()
                .persistent(TEST_FILE)
                .allocateSize(1024L * 1024 * 1024)
                .keyCodec(PrimitiveCodecs.LONG)
                .valueCodec(PrimitiveCodecs.LONG)
                .initialCapacity(count)
                .build();

        long startTime = System.currentTimeMillis();

        // 删除一半数据
        for (long i = 1; i <= count / 2; i++) {
            map2.remove(i);
        }

        long deleteDuration = System.currentTimeMillis() - startTime;

        startTime = System.currentTimeMillis();
        map2.close();
        long closeDuration = System.currentTimeMillis() - startTime;

        System.out.println("删除耗时: " + deleteDuration + " ms");
        System.out.println("持久化耗时: " + closeDuration + " ms");
        System.out.println("删除吞吐量: " + (count / 2 * 1000L / deleteDuration) + " ops/sec");

        // 验证删除
        RogueMap<Long, Long> map3 = RogueMap.<Long, Long>builder()
                .persistent(TEST_FILE)
                .allocateSize(1024L * 1024 * 1024)
                .keyCodec(PrimitiveCodecs.LONG)
                .valueCodec(PrimitiveCodecs.LONG)
                .initialCapacity(count)
                .build();

        System.out.println("剩余数据量: " + map3.size());
        map3.close();
    }

    // ========== 辅助方法 ==========

    private TestUserData createUserData(long id) {
        return new TestUserData(
                id,
                "user_" + id,
                "user" + id + "@example.com",
                20 + (int) (id % 50),
                1000.0 + (id % 10000),
                System.currentTimeMillis() - (id % 1000000),
                "Address Line 1, City " + (id % 100) + ", Country",
                "+86-138" + String.format("%08d", id % 100000000)
        );
    }

    /**
     * 测试用户数据对象
     */
    public static class TestUserData {
        private long userId;
        private String username;
        private String email;
        private int age;
        private double balance;
        private long lastLoginTime;
        private String address;
        private String phoneNumber;

        public TestUserData() {}

        public TestUserData(long userId, String username, String email, int age,
                            double balance, long lastLoginTime, String address, String phoneNumber) {
            this.userId = userId;
            this.username = username;
            this.email = email;
            this.age = age;
            this.balance = balance;
            this.lastLoginTime = lastLoginTime;
            this.address = address;
            this.phoneNumber = phoneNumber;
        }

        public long getUserId() { return userId; }
        public String getUsername() { return username; }
        public String getEmail() { return email; }
        public int getAge() { return age; }
        public double getBalance() { return balance; }
        public long getLastLoginTime() { return lastLoginTime; }
        public String getAddress() { return address; }
        public String getPhoneNumber() { return phoneNumber; }
    }
}
