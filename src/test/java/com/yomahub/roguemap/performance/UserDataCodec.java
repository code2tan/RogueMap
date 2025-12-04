package com.yomahub.roguemap.performance;

import com.yomahub.roguemap.memory.UnsafeOps;
import com.yomahub.roguemap.serialization.Codec;

import java.nio.charset.StandardCharsets;

/**
 * UserData 编解码器
 * 
 * 负责将 UserData 对象序列化到堆外内存，以及从堆外内存反序列化。
 */
public class UserDataCodec implements Codec<UserData> {

    public static final UserDataCodec INSTANCE = new UserDataCodec();

    private UserDataCodec() {
    }

    // 线程本地缓存，避免重复编码
    private static final ThreadLocal<EncodingCache> CACHE = ThreadLocal.withInitial(EncodingCache::new);

    @Override
    public int calculateSize(UserData value) {
        if (value == null) {
            return -1;
        }

        // 使用缓存避免重复编码
        EncodingCache cache = CACHE.get();
        cache.usernameBytes = value.getUsername() != null ? value.getUsername().getBytes(StandardCharsets.UTF_8) : null;
        cache.emailBytes = value.getEmail() != null ? value.getEmail().getBytes(StandardCharsets.UTF_8) : null;
        cache.addressBytes = value.getAddress() != null ? value.getAddress().getBytes(StandardCharsets.UTF_8) : null;
        cache.phoneBytes = value.getPhoneNumber() != null ? value.getPhoneNumber().getBytes(StandardCharsets.UTF_8)
                : null;

        int size = 0;
        size += 8; // userId (long)
        size += 4; // username length
        size += cache.usernameBytes != null ? cache.usernameBytes.length : 0;
        size += 4; // email length
        size += cache.emailBytes != null ? cache.emailBytes.length : 0;
        size += 4; // age (int)
        size += 8; // balance (double)
        size += 8; // lastLoginTime (long)
        size += 4; // address length
        size += cache.addressBytes != null ? cache.addressBytes.length : 0;
        size += 4; // phoneNumber length
        size += cache.phoneBytes != null ? cache.phoneBytes.length : 0;

        return size;
    }

    @Override
    public int encode(long address, UserData value) {
        if (value == null) {
            return 0;
        }

        // 使用缓存的 byte 数组，避免重复编码
        EncodingCache cache = CACHE.get();
        long offset = address;

        // userId
        UnsafeOps.putLong(offset, value.getUserId());
        offset += 8;

        // username
        offset = writeStringFromCache(offset, cache.usernameBytes);

        // email
        offset = writeStringFromCache(offset, cache.emailBytes);

        // age
        UnsafeOps.putInt(offset, value.getAge());
        offset += 4;

        // balance
        UnsafeOps.putDouble(offset, value.getBalance());
        offset += 8;

        // lastLoginTime
        UnsafeOps.putLong(offset, value.getLastLoginTime());
        offset += 8;

        // address
        offset = writeStringFromCache(offset, cache.addressBytes);

        // phoneNumber
        offset = writeStringFromCache(offset, cache.phoneBytes);

        // 清空缓存
        cache.clear();

        return (int) (offset - address);
    }

    @Override
    public UserData decode(long address) {
        long offset = address;

        UserData userData = new UserData();

        // userId
        userData.setUserId(UnsafeOps.getLong(offset));
        offset += 8;

        // username - 优化：直接从内存读取长度，避免重复调用getBytes()
        int usernameLen = UnsafeOps.getInt(offset);
        String username = readString(offset);
        userData.setUsername(username);
        offset += 4 + (usernameLen >= 0 ? usernameLen : 0);

        // email
        int emailLen = UnsafeOps.getInt(offset);
        String email = readString(offset);
        userData.setEmail(email);
        offset += 4 + (emailLen >= 0 ? emailLen : 0);

        // age
        userData.setAge(UnsafeOps.getInt(offset));
        offset += 4;

        // balance
        userData.setBalance(UnsafeOps.getDouble(offset));
        offset += 8;

        // lastLoginTime
        userData.setLastLoginTime(UnsafeOps.getLong(offset));
        offset += 8;

        // address
        int addressLen = UnsafeOps.getInt(offset);
        String address1 = readString(offset);
        userData.setAddress(address1);
        offset += 4 + (addressLen >= 0 ? addressLen : 0);

        // phoneNumber
        String phoneNumber = readString(offset);
        userData.setPhoneNumber(phoneNumber);

        return userData;
    }

    private long writeStringFromCache(long address, byte[] bytes) {
        if (bytes == null) {
            UnsafeOps.putInt(address, -1);
            return address + 4;
        }

        UnsafeOps.putInt(address, bytes.length);
        UnsafeOps.copyFromArray(bytes, 0, address + 4, bytes.length);
        return address + 4 + bytes.length;
    }

    private String readString(long address) {
        int length = UnsafeOps.getInt(address);
        if (length < 0) {
            return null;
        }

        byte[] bytes = new byte[length];
        UnsafeOps.copyToArray(address + 4, bytes, 0, length);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /**
     * 编码缓存，避免重复调用 String.getBytes()
     */
    private static class EncodingCache {
        byte[] usernameBytes;
        byte[] emailBytes;
        byte[] addressBytes;
        byte[] phoneBytes;

        void clear() {
            usernameBytes = null;
            emailBytes = null;
            addressBytes = null;
            phoneBytes = null;
        }
    }
}
