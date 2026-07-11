package com.example.gameworkbench.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class WorkflowExecutionClaimMigrationTest {

    @Test
    void migrationAddsHeartbeatFieldsWithoutChangingHistoricalRuns() throws Exception {
        ClassPathResource migration = new ClassPathResource("db/migration/V11__add_workflow_execution_heartbeat.sql");
        String sql = new String(migration.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        assertThat(sql).contains("heartbeat_at datetime null")
                .contains("last_activity_at datetime null")
                .contains("idx_workflow_run_execution_recovery");
    }
}
