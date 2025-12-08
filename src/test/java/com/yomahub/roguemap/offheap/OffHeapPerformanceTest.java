package com.yomahub.roguemap.offheap;

import com.yomahub.roguemap.RogueMap;
import com.yomahub.roguemap.serialization.KryoObjectCodec;
import com.yomahub.roguemap.serialization.PrimitiveCodecs;
import com.yomahub.roguemap.serialization.StringCodec;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 堆外内存存储性能测试
 *
 * 测试各种性能指标,包括:
 * - 插入性能
 * - 读取性能
 * - 更新性能
 * - 删除性能
 * - 并发性能
 * - 内存占用
 */
public class OffHeapPerformanceTest {

    /**
     * 测试 Long 键原始索引的插入性能
     */
    @Test
    public void testPrimitiveIndexInsertPerformance() {
        int count = 1_000_000;
        System.out.println("\n========== 原始索引插入性能测试 (Long 键) ==========");
        System.out.println("数据量: " + count);

        RogueMap<Long, Long> map = RogueMap.<Long, Long>builder()
                .keyCodec(PrimitiveCodecs.LONG)
                .valueCodec(PrimitiveCodecs.LONG)
                .maxMemory(1024L * 1024 * 1024)
                .offHeap()
                .primitiveIndex()
                .initialCapacity(count)
                .build();

        try {
            long startTime = System.currentTimeMillis();

            for (long i = 1; i <= count; i++) {
                map.put(i, i * 100);
            }

            long duration = System.currentTimeMillis() - startTime;
            System.out.println("插入耗时: " + duration + " ms");
            System.out.println("吞吐量: " + (count * 1000L / duration) + " ops/sec");
            System.out.println("平均延迟: " + (duration * 1000000.0 / count) + " ns/op");
        } finally {
            map.close();
        }
    }

    /**
     * 测试分段索引的插入性能
     */
    @Test
    public void testSegmentedIndexInsertPerformance() {
        int count = 1_000_000;
        System.out.println("\n========== 分段索引插入性能测试 (String 键) ==========");
        System.out.println("数据量: " + count);

        RogueMap<String, String> map = RogueMap.<String, String>builder()
                .keyCodec(new StringCodec())
                .valueCodec(new StringCodec())
                .maxMemory(1024L * 1024 * 1024)
                .offHeap()
                .segmentedIndex(64)
                .initialCapacity(count / 64)
                .build();

        try {
            long startTime = System.currentTimeMillis();

            for (int i = 0; i < count; i++) {
                map.put("key" + i, "value" + i);
            }

            long duration = System.currentTimeMillis() - startTime;
            System.out.println("插入耗时: " + duration + " ms");
            System.out.println("吞吐量: " + (count * 1000L / duration) + " ops/sec");
            System.out.println("平均延迟: " + (duration * 1000000.0 / count) + " ns/op");
        } finally {
            map.close();
        }
    }

    /**
     * 测试读取性能
     */
    @Test
    public void testReadPerformance() {
        int count = 1_000_000;
        System.out.println("\n========== 读取性能测试 ==========");
        System.out.println("数据量: " + count);

        RogueMap<Long, Long> map = RogueMap.<Long, Long>builder()
                .keyCodec(PrimitiveCodecs.LONG)
                .valueCodec(PrimitiveCodecs.LONG)
                .maxMemory(1024L * 1024 * 1024)
                .offHeap()
                .primitiveIndex()
                .initialCapacity(count)
                .build();

        try {
            // 先插入数据
            for (long i = 1; i <= count; i++) {
                map.put(i, i * 100);
            }

            // 顺序读取测试
            long startTime = System.currentTimeMillis();
            int hitCount = 0;

            for (long i = 1; i <= count; i++) {
                Long value = map.get(i);
                if (value != null && value == i * 100) {
                    hitCount++;
                }
            }

            long duration = System.currentTimeMillis() - startTime;
            System.out.println("顺序读取耗时: " + duration + " ms");
            System.out.println("吞吐量: " + (count * 1000L / duration) + " ops/sec");
            System.out.println("命中率: " + (hitCount * 100.0 / count) + "%");

            // 随机读取测试
            startTime = System.currentTimeMillis();
            hitCount = 0;
            int randomCount = 100_000;

            for (int i = 0; i < randomCount; i++) {
                long randomKey = (long) (Math.random() * count) + 1;
                Long value = map.get(randomKey);
                if (value != null) {
                    hitCount++;
                }
            }

            duration = System.currentTimeMillis() - startTime;
            System.out.println("\n随机读取耗时: " + duration + " ms (读取 " + randomCount + " 次)");
            System.out.println("吞吐量: " + (randomCount * 1000L / duration) + " ops/sec");
            System.out.println("命中率: " + (hitCount * 100.0 / randomCount) + "%");
        } finally {
            map.close();
        }
    }

    /**
     * 测试更新性能
     */
    @Test
    public void testUpdatePerformance() {
        int count = 100_000;
        System.out.println("\n========== 更新性能测试 ==========");
        System.out.println("数据量: " + count);

        RogueMap<Long, Long> map = RogueMap.<Long, Long>builder()
                .keyCodec(PrimitiveCodecs.LONG)
                .valueCodec(PrimitiveCodecs.LONG)
                .maxMemory(1024L * 1024 * 1024)
                .offHeap()
                .primitiveIndex()
                .initialCapacity(count)
                .build();

        try {
            // 先插入数据
            for (long i = 1; i <= count; i++) {
                map.put(i, i * 100);
            }

            // 更新测试
            long startTime = System.currentTimeMillis();

            for (long i = 1; i <= count; i++) {
                map.put(i, i * 200);  // 更新为新值
            }

            long duration = System.currentTimeMillis() - startTime;
            System.out.println("更新耗时: " + duration + " ms");
            System.out.println("吞吐量: " + (count * 1000L / duration) + " ops/sec");

            // 验证更新
            int correctCount = 0;
            for (long i = 1; i <= count; i++) {
                Long value = map.get(i);
                if (value != null && value == i * 200) {
                    correctCount++;
                }
            }
            System.out.println("更新正确率: " + (correctCount * 100.0 / count) + "%");
        } finally {
            map.close();
        }
    }

    /**
     * 测试删除性能
     */
    @Test
    public void testDeletePerformance() {
        int count = 100_000;
        System.out.println("\n========== 删除性能测试 ==========");
        System.out.println("数据量: " + count);

        RogueMap<Long, Long> map = RogueMap.<Long, Long>builder()
                .keyCodec(PrimitiveCodecs.LONG)
                .valueCodec(PrimitiveCodecs.LONG)
                .maxMemory(1024L * 1024 * 1024)
                .offHeap()
                .primitiveIndex()
                .initialCapacity(count)
                .build();

        try {
            // 先插入数据
            for (long i = 1; i <= count; i++) {
                map.put(i, i * 100);
            }

            // 删除测试
            long startTime = System.currentTimeMillis();

            for (long i = 1; i <= count; i++) {
                map.remove(i);
            }

            long duration = System.currentTimeMillis() - startTime;
            System.out.println("删除耗时: " + duration + " ms");
            System.out.println("吞吐量: " + (count * 1000L / duration) + " ops/sec");
            System.out.println("最终大小: " + map.size());
        } finally {
            map.close();
        }
    }

    /**
     * 测试混合操作性能
     */
    @Test
    public void testMixedOperationsPerformance() {
        int count = 100_000;
        System.out.println("\n========== 混合操作性能测试 ==========");
        System.out.println("操作总数: " + count * 3 + " (插入+读取+删除各 " + count + " 次)");

        RogueMap<Long, Long> map = RogueMap.<Long, Long>builder()
                .keyCodec(PrimitiveCodecs.LONG)
                .valueCodec(PrimitiveCodecs.LONG)
                .maxMemory(1024L * 1024 * 1024)
                .offHeap()
                .primitiveIndex()
                .initialCapacity(count)
                .build();

        try {
            long startTime = System.currentTimeMillis();

            // 插入
            for (long i = 1; i <= count; i++) {
                map.put(i, i * 100);
            }

            // 读取
            for (long i = 1; i <= count; i++) {
                map.get(i);
            }

            // 删除一半
            for (long i = 1; i <= count / 2; i++) {
                map.remove(i);
            }

            long duration = System.currentTimeMillis() - startTime;
            int totalOps = count * 2 + count / 2;
            System.out.println("总耗时: " + duration + " ms");
            System.out.println("平均吞吐量: " + (totalOps * 1000L / duration) + " ops/sec");
            System.out.println("最终大小: " + map.size());
        } finally {
            map.close();
        }
    }

    /**
     * 测试并发写入性能
     */
    @Test
    public void testConcurrentWritePerformance() throws InterruptedException {
        int threadCount = 10;
        int itemsPerThread = 50_000;
        System.out.println("\n========== 并发写入性能测试 ==========");
        System.out.println("线程数: " + threadCount);
        System.out.println("每线程数据量: " + itemsPerThread);
        System.out.println("总数据量: " + (threadCount * itemsPerThread));

        RogueMap<String, String> map = RogueMap.<String, String>builder()
                .keyCodec(new StringCodec())
                .valueCodec(new StringCodec())
                .maxMemory(1024L * 1024 * 1024)
                .offHeap()
                .segmentedIndex(64)
                .initialCapacity(threadCount * itemsPerThread / 64)
                .build();

        try {
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);

            long startTime = System.currentTimeMillis();

            for (int t = 0; t < threadCount; t++) {
                final int threadId = t;
                executor.submit(() -> {
                    try {
                        for (int i = 0; i < itemsPerThread; i++) {
                            map.put("thread" + threadId + "_key" + i, "value" + i);
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await();
            executor.shutdown();

            long duration = System.currentTimeMillis() - startTime;
            int totalOps = threadCount * itemsPerThread;
            System.out.println("总耗时: " + duration + " ms");
            System.out.println("吞吐量: " + (totalOps * 1000L / duration) + " ops/sec");
            System.out.println("最终大小: " + map.size());
        } finally {
            map.close();
        }
    }

    /**
     * 测试并发读写性能
     */
    @Test
    public void testConcurrentReadWritePerformance() throws InterruptedException {
        int count = 100_000;
        int threadCount = 10;
        System.out.println("\n========== 并发读写性能测试 ==========");
        System.out.println("初始数据量: " + count);
        System.out.println("线程数: " + threadCount + " (5读 + 5写)");

        RogueMap<String, String> map = RogueMap.<String, String>builder()
                .keyCodec(new StringCodec())
                .valueCodec(new StringCodec())
                .maxMemory(1024L * 1024 * 1024)
                .offHeap()
                .segmentedIndex(64)
                .build();

        try {
            // 预填充数据
            for (int i = 0; i < count; i++) {
                map.put("key" + i, "value" + i);
            }

            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);

            long startTime = System.currentTimeMillis();

            for (int t = 0; t < threadCount; t++) {
                final int threadId = t;
                executor.submit(() -> {
                    try {
                        if (threadId % 2 == 0) {
                            // 读线程
                            for (int i = 0; i < 100_000; i++) {
                                map.get("key" + (i % count));
                            }
                        } else {
                            // 写线程
                            for (int i = 0; i < 50_000; i++) {
                                map.put("thread" + threadId + "_key" + i, "value" + i);
                            }
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await();
            executor.shutdown();

            long duration = System.currentTimeMillis() - startTime;
            int totalOps = (threadCount / 2) * 100_000 + (threadCount / 2) * 50_000;
            System.out.println("总耗时: " + duration + " ms");
            System.out.println("平均吞吐量: " + (totalOps * 1000L / duration) + " ops/sec");
            System.out.println("最终大小: " + map.size());
        } finally {
            map.close();
        }
    }

    /**
     * 测试大对象存储性能
     */
    @Test
    public void testLargeObjectPerformance() {
        int count = 10_000;
        System.out.println("\n========== 大对象存储性能测试 ==========");
        System.out.println("数据量: " + count);

        RogueMap<Long, TestUserData> map = RogueMap.<Long, TestUserData>builder()
                .keyCodec(PrimitiveCodecs.LONG)
                .valueCodec(KryoObjectCodec.create(TestUserData.class))
                .maxMemory(1024L * 1024 * 1024)
                .offHeap()
                .primitiveIndex()
                .initialCapacity(count)
                .build();

        try {
            // 插入测试
            long startTime = System.currentTimeMillis();

            for (long i = 1; i <= count; i++) {
                TestUserData userData = createUserData(i);
                map.put(i, userData);
            }

            long insertDuration = System.currentTimeMillis() - startTime;
            System.out.println("插入耗时: " + insertDuration + " ms");
            System.out.println("插入吞吐量: " + (count * 1000L / insertDuration) + " ops/sec");

            // 读取测试
            startTime = System.currentTimeMillis();
            int verifyCount = 0;

            for (long i = 1; i <= count; i++) {
                TestUserData userData = map.get(i);
                if (userData != null && userData.getUserId() == i) {
                    verifyCount++;
                }
            }

            long readDuration = System.currentTimeMillis() - startTime;
            System.out.println("读取耗时: " + readDuration + " ms");
            System.out.println("读取吞吐量: " + (count * 1000L / readDuration) + " ops/sec");
            System.out.println("验证成功: " + verifyCount + "/" + count);
        } finally {
            map.close();
        }
    }

    /**
     * 测试内存占用
     */
    @Test
    public void testMemoryUsage() {
        int count = 100_000;
        System.out.println("\n========== 内存占用测试 ==========");
        System.out.println("数据量: " + count);

        Runtime runtime = Runtime.getRuntime();
        System.gc();
        try { Thread.sleep(100); } catch (InterruptedException e) {}

        long beforeHeap = runtime.totalMemory() - runtime.freeMemory();

        RogueMap<Long, Long> map = RogueMap.<Long, Long>builder()
                .keyCodec(PrimitiveCodecs.LONG)
                .valueCodec(PrimitiveCodecs.LONG)
                .maxMemory(1024L * 1024 * 1024)
                .offHeap()
                .primitiveIndex()
                .initialCapacity(count)
                .build();

        try {
            for (long i = 1; i <= count; i++) {
                map.put(i, i * 100);
            }

            System.gc();
            try { Thread.sleep(100); } catch (InterruptedException e) {}

            long afterHeap = runtime.totalMemory() - runtime.freeMemory();
            long heapUsed = afterHeap - beforeHeap;

            System.out.println("堆内存使用: " + (heapUsed / 1024 / 1024) + " MB");
            System.out.println("堆外内存使用(估算): " + (count * 8 / 1024 / 1024) + " MB (值数据)");
            System.out.println("索引内存使用(估算): " + (count * 20 / 1024 / 1024) + " MB");
            System.out.println("平均每条目: " + (heapUsed / count) + " bytes (堆内)");
        } finally {
            map.close();
        }
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
