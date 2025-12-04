package com.yomahub.roguemap.storage;

import com.yomahub.roguemap.memory.Allocator;
import com.yomahub.roguemap.memory.UnsafeOps;

/**
 * 堆外内存存储引擎
 *
 * 使用 DirectByteBuffer 在本地内存中存储数据
 */
public class OffHeapStorage implements StorageEngine {

    private final Allocator allocator;

    public OffHeapStorage(Allocator allocator) {
        if (allocator == null) {
            throw new IllegalArgumentException("Allocator 不能为 null");
        }
        this.allocator = allocator;
    }

    @Override
    public void put(long address, byte[] data, int offset, int length) {
        if (address == 0) {
            throw new IllegalArgumentException("无效的地址: 0");
        }
        if (data == null) {
            throw new IllegalArgumentException("数据不能为 null");
        }
        if (offset < 0 || length < 0 || offset + length > data.length) {
            throw new IllegalArgumentException("无效的偏移量或长度");
        }

        UnsafeOps.copyFromArray(data, offset, address, length);
    }

    @Override
    public byte[] get(long address, int length) {
        if (address == 0) {
            throw new IllegalArgumentException("无效的地址: 0");
        }
        if (length < 0) {
            throw new IllegalArgumentException("无效的长度: " + length);
        }

        byte[] data = new byte[length];
        UnsafeOps.copyToArray(address, data, 0, length);
        return data;
    }

    @Override
    public void delete(long address, int length) {
        if (address == 0) {
            return;
        }
        // 将内存释放回分配器
        allocator.free(address, length);
    }

    @Override
    public long capacity() {
        return allocator.totalAllocated();
    }

    @Override
    public long used() {
        return allocator.usedMemory();
    }

    @Override
    public void flush() {
        // 堆外存储无需刷新操作（非持久化）
    }

    @Override
    public void close() {
        allocator.close();
    }

    /**
     * 获取底层的分配器
     */
    public Allocator getAllocator() {
        return allocator;
    }
}
