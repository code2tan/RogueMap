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

    /**
     * 序列化索引到内存地址（用于持久化）
     *
     * @param address 目标地址
     * @return 写入的字节数
     */
    int serialize(long address);

    /**
     * 从内存地址反序列化索引（用于恢复）
     *
     * @param address 源地址
     * @param size 数据大小
     */
    void deserialize(long address, int size);

    /**
     * 计算序列化后的大小
     *
     * @return 字节数
     */
    int serializedSize();

    /**
     * 序列化索引到内存地址（使用相对偏移量，用于 MMAP 持久化）
     *
     * @param address 目标地址
     * @param baseAddress 基础地址，用于计算相对偏移量
     * @return 写入的字节数
     */
    int serializeWithOffsets(long address, long baseAddress);

    /**
     * 从内存地址反序列化索引（使用相对偏移量，用于 MMAP 恢复）
     *
     * @param address 源地址
     * @param size 数据大小
     * @param baseAddress 基础地址，用于重新计算内存地址
     */
    void deserializeWithOffsets(long address, int size, long baseAddress);
}
