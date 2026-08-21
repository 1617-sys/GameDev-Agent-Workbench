package com.example.gameworkbench.service.impl;

import com.example.gameworkbench.service.RedisService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;

/**
 * Redis 的最小通用适配层。
 *
 * <p>分布式锁使用 SET NX + TTL 获取，并使用 owner token 的 Lua 脚本释放。
 * owner token 用于避免旧持有者在锁过期并被新任务获取后误删新锁。</p>
 *
 * <p>当前锁没有续租机制，因此它只能作为快速防重手段；长任务仍必须依靠数据库
 * 状态版本和业务幂等约束保护最终一致性。</p>
 */
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
        // CONCURRENCY: “比较 owner”和“删除”必须在 Redis 内原子完成，不能先 GET 再 DEL。
        Long deleted = redisTemplate.execute(
                RELEASE_LOCK_SCRIPT,
                Collections.singletonList(key),
                ownerToken
        );
        return Long.valueOf(1L).equals(deleted);
    }
}
