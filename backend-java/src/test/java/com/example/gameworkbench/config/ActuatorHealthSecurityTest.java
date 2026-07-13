package com.example.gameworkbench.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.actuate.metrics.export.prometheus.PrometheusScrapeEndpoint;
import org.springframework.context.ApplicationContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "management.prometheus.metrics.export.enabled=true",
        "management.endpoints.web.exposure.include=health,prometheus",
        "management.endpoint.health.probes.enabled=true",
        "management.health.livenessstate.enabled=true",
        "management.health.readinessstate.enabled=true",
        "management.endpoint.health.group.readiness.include=readinessState",
        "management.endpoint.health.show-details=never",
        "management.endpoint.health.show-components=never"
})
@AutoConfigureMockMvc
class ActuatorHealthSecurityTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ApplicationContext applicationContext;

    @Test
    void healthIsPublicButNeverContainsComponentsOrConfiguration() throws Exception {
        String body = mockMvc.perform(get("/actuator/health"))
                .andReturn().getResponse().getContentAsString();
        assertThat(body.toLowerCase()).doesNotContain("password", "secret", "redis", "rabbit", "datasource", "components");
        mockMvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
        mockMvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
    }

    @Test
    void prometheusRequiresAuthenticationAndDangerousActuatorEndpointsStayClosed() throws Exception {
        assertThat(applicationContext.getBeansOfType(PrometheusScrapeEndpoint.class)).isNotEmpty();
        mockMvc.perform(get("/actuator/prometheus")).andExpect(status().isForbidden());
        mockMvc.perform(get("/actuator/env")).andExpect(status().isForbidden());
        mockMvc.perform(get("/actuator/configprops")).andExpect(status().isForbidden());
        mockMvc.perform(get("/actuator/shutdown")).andExpect(status().isForbidden());
    }
}
