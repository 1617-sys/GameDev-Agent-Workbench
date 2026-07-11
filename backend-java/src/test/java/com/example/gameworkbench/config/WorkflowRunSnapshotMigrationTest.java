package com.example.gameworkbench.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class WorkflowRunSnapshotMigrationTest {

    @Test
    void migrationAddsNullableWorkflowSnapshotsForHistoricalCompatibility() throws Exception {
        ClassPathResource migration = new ClassPathResource("db/migration/V5__extend_workflow_run_for_domain_snapshot.sql");
        String sql = new String(migration.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        assertThat(migration.exists()).isTrue();
        assertThat(sql)
                .contains("workflow_definition_version_id bigint null")
                .contains("workflow_definition_snapshot json null")
                .contains("prompt_version_snapshot json null")
                .contains("schema_version varchar(40) null")
                .contains("attempt int null")
                .contains("status_version bigint null")
                .contains("fk_workflow_run_definition_version");
    }
}
