package com.yomahub.roguemap.index;

import java.util.concurrent.locks.StampedLock;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 使用乐观锁实现的分段哈希索引，支持高并发访问
 *
 * 使用 StampedLock 提供比 ReentrantLock 更好的读性能。
 * 分段设计减少了多线程场景下的锁竞争。
 */
public class SegmentedHashIndex<K> implements Index<K> {

    private static final int DEFAULT_SEGMENT_COUNT = 64;
    private static final int DEFAULT_INITIAL_CAPACITY = 16;

    private final Segment<K>[] segments;
    private final int segmentMask;
    private final AtomicInteger size;

    @SuppressWarnings("unchecked")
    public SegmentedHashIndex() {
        this(DEFAULT_SEGMENT_COUNT, DEFAULT_INITIAL_CAPACITY);
    }

    @SuppressWarnings("unchecked")
    public SegmentedHashIndex(int segmentCount, int initialCapacityPerSegment) {
        if (segmentCount <= 0 || (segmentCount & (segmentCount - 1)) != 0) {
            throw new IllegalArgumentException("段数必须是 2 的幂次方");
        }

        this.segments = new Segment[segmentCount];
        this.segmentMask = segmentCount - 1;
        this.size = new AtomicInteger(0);

        for (int i = 0; i < segmentCount; i++) {
            segments[i] = new Segment<>(initialCapacityPerSegment);
        }
    }

    @Override
    public long put(K key, long address, int valueSize) {
        if (key == null) {
            throw new IllegalArgumentException("键不能为 null");
        }
        if (address == 0) {
            throw new IllegalArgumentException("无效的地址: 0");
        }

        Segment<K> segment = getSegment(key);
        long oldAddress = segment.put(key, address, valueSize);

        if (oldAddress == 0) {
            size.incrementAndGet();
        }

        return oldAddress;
    }

    @Override
    public long get(K key) {
        if (key == null) {
            return 0;
        }

        Segment<K> segment = getSegment(key);
        return segment.get(key);
    }

    @Override
    public int getSize(K key) {
        if (key == null) {
            return -1;
        }

        Segment<K> segment = getSegment(key);
        return segment.getSize(key);
    }

    @Override
    public long remove(K key) {
        if (key == null) {
            return 0;
        }

        Segment<K> segment = getSegment(key);
        long address = segment.remove(key);

        if (address != 0) {
            size.decrementAndGet();
        }

        return address;
    }

    @Override
    public boolean containsKey(K key) {
        if (key == null) {
            return false;
        }

        Segment<K> segment = getSegment(key);
        return segment.containsKey(key);
    }

    @Override
    public int size() {
        return size.get();
    }

    @Override
    public void clear() {
        for (Segment<K> segment : segments) {
            segment.clear();
        }
        size.set(0);
    }

    @Override
    public void close() {
        clear();
    }

    private Segment<K> getSegment(K key) {
        int hash = key.hashCode();
        int index = hash & segmentMask;
        return segments[index];
    }

    /**
     * 每个段都有自己的锁，用于减少锁竞争
     */
    private static class Segment<K> {
        private final StampedLock lock;
        private final Map<K, Entry> map;

        Segment(int initialCapacity) {
            this.lock = new StampedLock();
            this.map = new HashMap<>(initialCapacity);
        }

        long put(K key, long address, int size) {
            long stamp = lock.writeLock();
            try {
                Entry oldEntry = map.put(key, new Entry(address, size));
                return oldEntry != null ? oldEntry.address : 0;
            } finally {
                lock.unlockWrite(stamp);
            }
        }

        long get(K key) {
            // 首先尝试乐观读（无锁）
            long stamp = lock.tryOptimisticRead();
            Entry entry = map.get(key);

            if (!lock.validate(stamp)) {
                // 如果乐观读失败，退回到读锁
                stamp = lock.readLock();
                try {
                    entry = map.get(key);
                } finally {
                    lock.unlockRead(stamp);
                }
            }

            return entry != null ? entry.address : 0;
        }

        int getSize(K key) {
            // 首先尝试乐观读
            long stamp = lock.tryOptimisticRead();
            Entry entry = map.get(key);

            if (!lock.validate(stamp)) {
                // 如果乐观读失败，退回到读锁
                stamp = lock.readLock();
                try {
                    entry = map.get(key);
                } finally {
                    lock.unlockRead(stamp);
                }
            }

            return entry != null ? entry.size : -1;
        }

        long remove(K key) {
            long stamp = lock.writeLock();
            try {
                Entry entry = map.remove(key);
                return entry != null ? entry.address : 0;
            } finally {
                lock.unlockWrite(stamp);
            }
        }

        boolean containsKey(K key) {
            // 首先尝试乐观读
            long stamp = lock.tryOptimisticRead();
            boolean contains = map.containsKey(key);

            if (!lock.validate(stamp)) {
                // 如果乐观读失败，退回到读锁
                stamp = lock.readLock();
                try {
                    contains = map.containsKey(key);
                } finally {
                    lock.unlockRead(stamp);
                }
            }

            return contains;
        }

        void clear() {
            long stamp = lock.writeLock();
            try {
                map.clear();
            } finally {
                lock.unlockWrite(stamp);
            }
        }
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
