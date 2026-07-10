package com.example.gameworkbench.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    @Test
    void rejectsMissingWeakAndPlaceholderSecrets() {
        assertThatThrownBy(() -> new JwtService("", 86400))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new JwtService("short-secret", 86400))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new JwtService("REPLACE_WITH_AT_LEAST_32_CHAR_RANDOM_JWT_SECRET", 86400))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new JwtService("SET_ME", 86400))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void acceptsStrongTestSecret() {
        JwtService jwtService = new JwtService("test-only-jwt-secret-at-least-32-bytes", 86400);

        String token = jwtService.generateToken(7L, "tester");

        assertThat(jwtService.validateToken(token)).isTrue();
        assertThat(jwtService.parseUserId(token)).isEqualTo(7L);
    }
}
