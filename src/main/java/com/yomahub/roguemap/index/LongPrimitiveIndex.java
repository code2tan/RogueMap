package com.yomahub.roguemap.index;

import java.util.concurrent.locks.StampedLock;

/**
 * 极致优化的Long键索引 - 使用原始类型数组
 *
 * 内存占用（100万条，负载因子0.75）：
 * - keys: 8 bytes × 1,333,333 = 10.7 MB
 * - addresses: 8 bytes × 1,333,333 = 10.7 MB
 * - sizes: 4 bytes × 1,333,333 = 5.3 MB
 * - 总计: ~27 MB（实际存储100万条约20MB）
 *
 * 相比HashMap的104MB，节省约81%的内存
 */
public class LongPrimitiveIndex implements Index<Long> {

    private static final long EMPTY_KEY = 0L;
    private static final long DELETED_KEY = Long.MIN_VALUE;
    private static final int DEFAULT_CAPACITY = 16;
    private static final float LOAD_FACTOR = 0.75f;

    private long[] keys;           // 键数组（原始类型）
    private long[] addresses;      // 地址数组
    private int[] sizes;           // 大小数组
    private int size;              // 当前元素数量
    private int threshold;         // 扩容阈值
    private final StampedLock lock;

    public LongPrimitiveIndex() {
        this(DEFAULT_CAPACITY);
    }

    public LongPrimitiveIndex(int initialCapacity) {
        int capacity = tableSizeFor(initialCapacity);
        this.keys = new long[capacity];
        this.addresses = new long[capacity];
        this.sizes = new int[capacity];
        this.size = 0;
        this.threshold = (int) (capacity * LOAD_FACTOR);
        this.lock = new StampedLock();
    }

    @Override
    public long put(Long key, long address, int valueSize) {
        if (key == null || key == EMPTY_KEY || key == DELETED_KEY) {
            throw new IllegalArgumentException("无效的键: " + key);
        }
        if (address == 0) {
            throw new IllegalArgumentException("无效的地址: 0");
        }

        long stamp = lock.writeLock();
        try {
            return putInternal(key, address, valueSize);
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    private long putInternal(long key, long address, int valueSize) {
        if (size >= threshold) {
            resize();
        }

        int index = findSlot(key);
        long oldAddress = addresses[index];

        if (keys[index] == EMPTY_KEY || keys[index] == DELETED_KEY) {
            // 新插入
            size++;
        }

        keys[index] = key;
        addresses[index] = address;
        sizes[index] = valueSize;

        return oldAddress;
    }

    @Override
    public long get(Long key) {
        if (key == null || key == EMPTY_KEY || key == DELETED_KEY) {
            return 0;
        }

        // 乐观读（无锁）
        long stamp = lock.tryOptimisticRead();
        int index = probe(key);
        long addr = (index >= 0) ? addresses[index] : 0;

        if (!lock.validate(stamp)) {
            // 乐观读失败，降级到读锁
            stamp = lock.readLock();
            try {
                index = probe(key);
                addr = (index >= 0) ? addresses[index] : 0;
            } finally {
                lock.unlockRead(stamp);
            }
        }

        return addr;
    }

    @Override
    public int getSize(Long key) {
        if (key == null || key == EMPTY_KEY || key == DELETED_KEY) {
            return -1;
        }

        // 乐观读
        long stamp = lock.tryOptimisticRead();
        int index = probe(key);
        int sz = (index >= 0) ? sizes[index] : -1;

        if (!lock.validate(stamp)) {
            stamp = lock.readLock();
            try {
                index = probe(key);
                sz = (index >= 0) ? sizes[index] : -1;
            } finally {
                lock.unlockRead(stamp);
            }
        }

        return sz;
    }

    @Override
    public long remove(Long key) {
        if (key == null || key == EMPTY_KEY || key == DELETED_KEY) {
            return 0;
        }

        long stamp = lock.writeLock();
        try {
            int index = probe(key);
            if (index < 0) {
                return 0;
            }

            long oldAddress = addresses[index];
            keys[index] = DELETED_KEY;  // 标记为已删除
            addresses[index] = 0;
            sizes[index] = 0;
            size--;

            return oldAddress;
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    @Override
    public boolean containsKey(Long key) {
        return get(key) != 0;
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public void clear() {
        long stamp = lock.writeLock();
        try {
            keys = new long[DEFAULT_CAPACITY];
            addresses = new long[DEFAULT_CAPACITY];
            sizes = new int[DEFAULT_CAPACITY];
            size = 0;
            threshold = (int) (DEFAULT_CAPACITY * LOAD_FACTOR);
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    @Override
    public void close() {
        clear();
    }

    /**
     * 线性探测查找键的位置
     * @return 键的索引，如果不存在返回-1
     */
    private int probe(long key) {
        int index = hash(key) & (keys.length - 1);
        int start = index;

        do {
            long k = keys[index];
            if (k == key) {
                return index;
            }
            if (k == EMPTY_KEY) {
                return -1;
            }
            index = (index + 1) & (keys.length - 1);
        } while (index != start);

        return -1;
    }

    /**
     * 找到可以插入的槽位（用于插入）
     */
    private int findSlot(long key) {
        int index = hash(key) & (keys.length - 1);
        int start = index;
        int firstDeleted = -1;

        do {
            long k = keys[index];
            if (k == key) {
                return index;
            }
            if (k == EMPTY_KEY) {
                return firstDeleted >= 0 ? firstDeleted : index;
            }
            if (k == DELETED_KEY && firstDeleted < 0) {
                firstDeleted = index;
            }
            index = (index + 1) & (keys.length - 1);
        } while (index != start);

        return firstDeleted >= 0 ? firstDeleted : index;
    }

    /**
     * 扩容
     */
    private void resize() {
        int newCapacity = keys.length * 2;
        long[] oldKeys = keys;
        long[] oldAddresses = addresses;
        int[] oldSizes = sizes;

        keys = new long[newCapacity];
        addresses = new long[newCapacity];
        sizes = new int[newCapacity];
        threshold = (int) (newCapacity * LOAD_FACTOR);
        size = 0;

        // 重新哈希
        for (int i = 0; i < oldKeys.length; i++) {
            long k = oldKeys[i];
            if (k != EMPTY_KEY && k != DELETED_KEY) {
                putInternal(k, oldAddresses[i], oldSizes[i]);
            }
        }
    }

    /**
     * MurmurHash3 finalization mix - 高质量哈希函数
     */
    private int hash(long key) {
        long h = key;
        h ^= h >>> 33;
        h *= 0xff51afd7ed558ccdL;
        h ^= h >>> 33;
        h *= 0xc4ceb9fe1a85ec53L;
        h ^= h >>> 33;
        return (int) h;
    }

    /**
     * 计算大于等于cap的最小2的幂次方
     */
    private static int tableSizeFor(int cap) {
        int n = cap - 1;
        n |= n >>> 1;
        n |= n >>> 2;
        n |= n >>> 4;
        n |= n >>> 8;
        n |= n >>> 16;
        return (n < 0) ? 1 : n + 1;
    }
}
