package com.yomahub.roguemap.index;

/**
 * 索引接口，用于键值查找
 *
 * @param <K> 键类型
 */
public interface Index<K> {

    /**
     * 将键值对放入索引
     *
     * @param key 键
     * @param address 值存储的内存地址
     * @param size 值的字节大小
     * @return 如果键已存在则返回之前的地址，否则返回 0
     */
    long put(K key, long address, int size);

    /**
     * 获取键对应的内存地址
     *
     * @param key 键
     * @return 内存地址，如果未找到则返回 0
     */
    long get(K key);

    /**
     * 获取键对应值的大小
     *
     * @param key 键
     * @return 字节大小，如果未找到则返回 -1
     */
    int getSize(K key);

    /**
     * 从索引中移除键
     *
     * @param key 键
     * @return 被移除值的地址，如果未找到则返回 0
     */
    long remove(K key);

    /**
     * 检查键是否存在于索引中
     *
     * @param key 键
     * @return 如果存在返回 true，否则返回 false
     */
    boolean containsKey(K key);

    /**
     * 获取索引中的条目数量
     *
     * @return 条目数量
     */
    int size();

    /**
     * 从索引中移除所有条目
     */
    void clear();

    /**
     * 关闭索引并释放资源
     */
    void close();
}
