package com.example.gameworkbench.config;

import com.example.gameworkbench.controller.DemoController;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ProductionProfileSecurityTest {

    @Test
    void excludesDemoEndpointAndInternalDocumentationFromProduction() throws Exception {
        Profile profile = DemoController.class.getAnnotation(Profile.class);
        String productionConfig = new String(
                new ClassPathResource("application-prod.yml").getInputStream().readAllBytes(),
                StandardCharsets.UTF_8
        ).replace("\r\n", "\n");

        assertThat(profile.value()).containsExactly("!prod");
        assertThat(productionConfig).contains(
                "api-docs:\n    enabled: false",
                "swagger-ui:\n    enabled: false",
                "allowed-origins: ${CORS_ALLOWED_ORIGINS:}"
        );
    }
}
