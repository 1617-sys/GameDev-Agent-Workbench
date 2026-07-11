package com.example.gameworkbench.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class WorkflowStepRunMigrationTest {

    @Test
    void migrationCreatesTraceableStepRunsAndArtifactAssociation() throws Exception {
        ClassPathResource migration = new ClassPathResource("db/migration/V3__add_workflow_step_run.sql");
        String sql = new String(migration.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        assertThat(migration.exists()).isTrue();
        assertThat(sql)
                .contains("create table workflow_step_run")
                .contains("step_run_uuid")
                .contains("workflow_run_uuid")
                .contains("definition_version_id")
                .contains("attempt")
                .contains("unique (workflow_run_id, step_key, attempt)")
                .contains("add column step_run_id")
                .contains("fk_agent_artifact_step_run");
    }
}
