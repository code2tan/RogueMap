package com.yomahub.roguemap.index;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 使用 ConcurrentHashMap 实现的基础哈希索引
 *
 * 存储键到内存地址的映射关系，内存地址指向值的存储位置。
 */
public class HashIndex<K> implements Index<K> {

    private final ConcurrentHashMap<K, Entry> map;
    private final AtomicInteger size;

    public HashIndex() {
        this(16);
    }

    public HashIndex(int initialCapacity) {
        this.map = new ConcurrentHashMap<>(initialCapacity);
        this.size = new AtomicInteger(0);
    }

    @Override
    public long put(K key, long address, int valueSize) {
        if (key == null) {
            throw new IllegalArgumentException("键不能为 null");
        }
        if (address == 0) {
            throw new IllegalArgumentException("无效的地址: 0");
        }

        Entry newEntry = new Entry(address, valueSize);
        Entry oldEntry = map.put(key, newEntry);

        if (oldEntry == null) {
            size.incrementAndGet();
            return 0;
        } else {
            return oldEntry.address;
        }
    }

    @Override
    public long get(K key) {
        if (key == null) {
            return 0;
        }

        Entry entry = map.get(key);
        return entry != null ? entry.address : 0;
    }

    @Override
    public int getSize(K key) {
        if (key == null) {
            return -1;
        }

        Entry entry = map.get(key);
        return entry != null ? entry.size : -1;
    }

    @Override
    public long remove(K key) {
        if (key == null) {
            return 0;
        }

        Entry entry = map.remove(key);
        if (entry != null) {
            size.decrementAndGet();
            return entry.address;
        }
        return 0;
    }

    @Override
    public boolean containsKey(K key) {
        return key != null && map.containsKey(key);
    }

    @Override
    public int size() {
        return size.get();
    }

    @Override
    public void clear() {
        map.clear();
        size.set(0);
    }

    @Override
    public void close() {
        clear();
    }

    /**
     * Entry 保存值的内存地址和大小
     */
    private static class Entry {
        final long address;
        final int size;

        Entry(long address, int size) {
            this.address = address;
            this.size = size;
        }
    }
}
