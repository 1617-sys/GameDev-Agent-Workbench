package com.example.gameworkbench.service.impl;

import com.example.gameworkbench.service.RedisService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;

@Service
@RequiredArgsConstructor
public class RedisServiceImpl implements RedisService {

    private static final DefaultRedisScript<Long> RELEASE_LOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then "
                    + "return redis.call('del', KEYS[1]) "
                    + "else return 0 end",
            Long.class
    );

    private final RedisTemplate<String, Object> redisTemplate;
    @Override
    public void set(String key, Object value) {
        redisTemplate.opsForValue().set(key, value);
    }

    @Override
    public void set(String key, Object value, long timeoutSeconds) {
        redisTemplate.opsForValue().set(key, value, Duration.ofSeconds(timeoutSeconds));
    }

    @Override
    public Object get(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    @Override
    public void delete(String key) {
        redisTemplate.delete(key);
    }

    @Override
    public Boolean setIfAbsent(String key, Object value, long timeoutSeconds) {
        return redisTemplate.opsForValue().setIfAbsent(key, value, Duration.ofSeconds(timeoutSeconds));
    }

    @Override
    public boolean tryLock(String key, String ownerToken, long timeoutSeconds) {
        return Boolean.TRUE.equals(setIfAbsent(key, ownerToken, timeoutSeconds));
    }

    @Override
    public boolean releaseLock(String key, String ownerToken) {
        Long deleted = redisTemplate.execute(
                RELEASE_LOCK_SCRIPT,
                Collections.singletonList(key),
                ownerToken
        );
        return Long.valueOf(1L).equals(deleted);
    }
}
