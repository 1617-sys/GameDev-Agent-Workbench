package com.example.gameworkbench.service;

public interface RedisService {
    void set(String key, Object value);

    void set(String key, Object value, long timeoutSeconds);

    Object get(String key);

    void delete(String key);

    Boolean setIfAbsent(String key, Object value, long timeoutSeconds);

    boolean tryLock(String key, String ownerToken, long timeoutSeconds);

    boolean releaseLock(String key, String ownerToken);
}
