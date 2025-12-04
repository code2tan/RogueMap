package com.yomahub.roguemap.performance;

/**
 * 用户数据对象 - 用于性能测试
 * 
 * 这个类代表一个较大的值对象，包含多个字段，
 * 用于测试 RogueMap 在值远大于键时的性能表现。
 */
public class UserData {
    private long userId;
    private String username;
    private String email;
    private int age;
    private double balance;
    private long lastLoginTime;
    private String address;
    private String phoneNumber;

    public UserData() {
    }

    public UserData(long userId, String username, String email, int age,
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

    public long getUserId() {
        return userId;
    }

    public void setUserId(long userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public int getAge() {
        return age;
    }

    public void setAge(int age) {
        this.age = age;
    }

    public double getBalance() {
        return balance;
    }

    public void setBalance(double balance) {
        this.balance = balance;
    }

    public long getLastLoginTime() {
        return lastLoginTime;
    }

    public void setLastLoginTime(long lastLoginTime) {
        this.lastLoginTime = lastLoginTime;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    @Override
    public String toString() {
        return "UserData{" +
                "userId=" + userId +
                ", username='" + username + '\'' +
                ", email='" + email + '\'' +
                ", age=" + age +
                ", balance=" + balance +
                ", lastLoginTime=" + lastLoginTime +
                ", address='" + address + '\'' +
                ", phoneNumber='" + phoneNumber + '\'' +
                '}';
    }
}
