package com.example.gameworkbench.service.impl;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

@ExtendWith(MockitoExtension.class)
class RedisServiceImplTest {

    private static final String LOCK_KEY = "demoStream:42";
    private static final String OWNER_TOKEN = "owner-token";

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    private RedisServiceImpl redisService;

    @BeforeEach
    void setUp() {
        redisService = new RedisServiceImpl(redisTemplate);
    }

    @Test
    void shouldAtomicallyDeleteLockWhenOwnerMatches() {
        when(redisTemplate.execute(any(RedisScript.class), eq(List.of(LOCK_KEY)), eq(OWNER_TOKEN)))
                .thenReturn(1L);

        boolean released = redisService.releaseLock(LOCK_KEY, OWNER_TOKEN);

        assertTrue(released);
        ArgumentCaptor<RedisScript<Long>> scriptCaptor = ArgumentCaptor.forClass(RedisScript.class);
        verify(redisTemplate).execute(scriptCaptor.capture(), eq(List.of(LOCK_KEY)), eq(OWNER_TOKEN));
        String script = scriptCaptor.getValue().getScriptAsString();
        assertTrue(script.contains("redis.call('get', KEYS[1]) == ARGV[1]"));
        assertTrue(script.contains("redis.call('del', KEYS[1])"));
    }

    @Test
    void shouldNotDeleteLockWhenOwnerDoesNotMatch() {
        when(redisTemplate.execute(any(RedisScript.class), eq(List.of(LOCK_KEY)), eq("wrong-owner")))
                .thenReturn(0L);

        boolean released = redisService.releaseLock(LOCK_KEY, "wrong-owner");

        assertFalse(released);
        verify(redisTemplate).execute(any(RedisScript.class), eq(List.of(LOCK_KEY)), eq("wrong-owner"));
    }

    @Test
    void shouldTreatNullRedisResultAsLockNotAcquired() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(LOCK_KEY, OWNER_TOKEN, Duration.ofSeconds(300)))
                .thenReturn(null);

        boolean acquired = redisService.tryLock(LOCK_KEY, OWNER_TOKEN, 300);

        assertFalse(acquired);
    }
}
