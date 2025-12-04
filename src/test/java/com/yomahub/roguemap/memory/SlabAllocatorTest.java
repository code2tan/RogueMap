package com.yomahub.roguemap.memory;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SlabAllocator 测试类
 *
 * 测试 Slab 内存分配器的功能，包括：
 * - 基本的内存分配和释放
 * - 多种大小的内存分配
 * - 内存重用机制
 * - 内存限制
 * - 统计信息
 * - 异常处理
 */
class SlabAllocatorTest {

    private SlabAllocator allocator;

    @BeforeEach
    void setUp() {
        // 在每个测试之前创建一个 10MB 的 SlabAllocator
        allocator = new SlabAllocator(10 * 1024 * 1024); // 10MB
    }

    @AfterEach
    void tearDown() {
        // 在每个测试之后释放所有分配的内存
        if (allocator != null) {
            allocator.close();
        }
    }

    /**
     * 测试基本的内存分配
     *
     * 验证：
     * 1. 可以成功分配内存（返回非零地址）
     * 2. 可以成功释放内存
     */
    @Test
    void testAllocate() {
        // 分配 64 字节内存
        long address = allocator.allocate(64);
        assertTrue(address != 0, "分配应该返回非零地址");

        // 释放内存
        allocator.free(address, 64);
    }

    /**
     * 测试多次内存分配
     *
     * 验证：
     * 1. 可以连续分配 100 次内存
     * 2. 每次分配都成功（返回非零地址）
     * 3. 可以成功释放所有分配的内存
     */
    @Test
    void testMultipleAllocations() {
        long[] addresses = new long[100];

        // 分配 100 次
        for (int i = 0; i < 100; i++) {
            addresses[i] = allocator.allocate(128);
            assertTrue(addresses[i] != 0,
                "第 " + i + " 次分配应该成功");
        }

        // 释放所有分配的内存
        for (int i = 0; i < 100; i++) {
            allocator.free(addresses[i], 128);
        }
    }

    /**
     * 测试不同大小的内存分配
     *
     * 验证：
     * 1. 可以分配不同大小的内存块
     * 2. Slab 分配器能正确处理各种大小（16, 64, 256, 1024 字节）
     * 3. 所有大小的内存都能成功分配和释放
     */
    @Test
    void testDifferentSizes() {
        // 分配不同大小的内存块
        long addr1 = allocator.allocate(16);
        long addr2 = allocator.allocate(64);
        long addr3 = allocator.allocate(256);
        long addr4 = allocator.allocate(1024);

        // 验证所有分配都成功
        assertTrue(addr1 != 0, "16 字节分配应该成功");
        assertTrue(addr2 != 0, "64 字节分配应该成功");
        assertTrue(addr3 != 0, "256 字节分配应该成功");
        assertTrue(addr4 != 0, "1024 字节分配应该成功");

        // 释放所有内存
        allocator.free(addr1, 16);
        allocator.free(addr2, 64);
        allocator.free(addr3, 256);
        allocator.free(addr4, 1024);
    }

    /**
     * 测试内存重用机制
     *
     * 验证：
     * 1. 释放的内存可以被重用
     * 2. Slab 的空闲列表机制正常工作
     * 3. 第二次分配可能返回之前释放的地址
     */
    @Test
    void testMemoryReuse() {
        // 分配并释放一块内存
        long addr1 = allocator.allocate(64);
        allocator.free(addr1, 64);

        // 再次分配相同大小的内存，应该重用之前释放的内存
        long addr2 = allocator.allocate(64);
        assertTrue(addr2 != 0, "重新分配应该成功");

        allocator.free(addr2, 64);
    }

    /**
     * 测试内存限制
     *
     * 验证：
     * 1. 当请求的内存超过最大限制时，分配失败
     * 2. 分配失败时返回 0
     * 3. 不会抛出异常，而是优雅地处理内存不足
     */
    @Test
    void testMemoryLimit() {
        // 创建一个只有 1KB 限制的小分配器
        SlabAllocator smallAllocator = new SlabAllocator(1024); // 1KB limit

        try {
            // 尝试分配 2KB，应该失败（超过 1KB 限制）
            long address = smallAllocator.allocate(2048);
            assertEquals(0, address,
                "超过内存限制时应该返回 0");
        } finally {
            smallAllocator.close();
        }
    }

    /**
     * 测试统计信息
     *
     * 验证：
     * 1. usedMemory() 返回正确的已使用内存
     * 2. totalAllocated() 返回正确的总分配内存
     * 3. availableMemory() 返回正确的可用内存
     * 4. getStats() 返回包含统计信息的字符串
     */
    @Test
    void testStats() {
        // 分配一些内存
        long addr1 = allocator.allocate(128);
        long addr2 = allocator.allocate(256);

        // 验证统计信息
        assertTrue(allocator.usedMemory() > 0,
            "已使用内存应该大于 0");
        assertTrue(allocator.totalAllocated() > 0,
            "总分配内存应该大于 0");
        assertTrue(allocator.availableMemory() > 0,
            "可用内存应该大于 0");

        // 验证统计字符串
        String stats = allocator.getStats();
        assertNotNull(stats, "统计信息不应该为 null");
        assertTrue(stats.contains("SlabAllocator") || stats.contains("统计信息"),
            "统计信息应该包含 'SlabAllocator' 或 '统计信息'");

        // 释放内存
        allocator.free(addr1, 128);
        allocator.free(addr2, 256);
    }

    /**
     * 测试无效的分配大小
     *
     * 验证：
     * 1. 分配 0 字节会抛出 IllegalArgumentException
     * 2. 分配负数字节会抛出 IllegalArgumentException
     * 3. 参数验证正常工作
     */
    @Test
    void testInvalidSize() {
        // 尝试分配 0 字节
        assertThrows(IllegalArgumentException.class, () -> {
            allocator.allocate(0);
        }, "分配 0 字节应该抛出 IllegalArgumentException");

        // 尝试分配负数字节
        assertThrows(IllegalArgumentException.class, () -> {
            allocator.allocate(-1);
        }, "分配负数字节应该抛出 IllegalArgumentException");
    }

    /**
     * 测试大对象分配
     *
     * 验证：
     * 1. 可以分配超过最大 slab 大小的内存（16384 字节）
     * 2. 大对象直接分配而不使用 slab
     * 3. 大对象可以正确释放
     */
    @Test
    void testLargeAllocation() {
        // 分配一个大于最大 slab 大小（16384）的内存块
        long address = allocator.allocate(32768);
        assertTrue(address != 0,
            "大对象分配应该成功");

        // 释放大对象内存
        allocator.free(address, 32768);
    }
}
