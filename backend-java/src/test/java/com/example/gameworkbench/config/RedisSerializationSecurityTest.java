package com.example.gameworkbench.config;

import com.example.gameworkbench.entity.SysUser;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.io.File;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class RedisSerializationSecurityTest {

    @Test
    void permitsOnlyTheCachedUserDomainType() {
        RedisSerializer<Object> serializer = serializer();
        SysUser user = new SysUser();
        user.setId(7L);
        user.setUsername("owner");
        user.setCreatedAt(LocalDateTime.of(2026, 7, 13, 12, 0));

        assertThat(serializer.deserialize(serializer.serialize(user)))
                .isInstanceOfSatisfying(SysUser.class, cached ->
                        assertThat(cached.getCreatedAt()).isEqualTo(user.getCreatedAt()));
    }

    @Test
    void rejectsUnexpectedPolymorphicTypes() {
        RedisSerializer<Object> serializer = serializer();
        byte[] untrusted = serializer.serialize(new File("controlled-fixture"));

        assertThatThrownBy(() -> serializer.deserialize(untrusted))
                .isInstanceOf(RuntimeException.class);
    }

    @SuppressWarnings("unchecked")
    private RedisSerializer<Object> serializer() {
        return (RedisSerializer<Object>) new RedisConfig()
                .redisTemplate(mock(RedisConnectionFactory.class))
                .getValueSerializer();
    }
}
