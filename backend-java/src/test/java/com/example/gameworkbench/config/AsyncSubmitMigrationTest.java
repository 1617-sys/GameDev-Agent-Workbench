package com.example.gameworkbench.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class AsyncSubmitMigrationTest {

    @Test
    void migrationAddsDatabaseIdempotencyConstraintAndInitialOutbox() throws Exception {
        ClassPathResource migration = new ClassPathResource("db/migration/V9__add_async_submit_idempotency_and_outbox.sql");
        String sql = new String(migration.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        assertThat(sql).contains("idempotency_key varchar(128)")
                .contains("request_fingerprint char(64)")
                .contains("uk_workflow_run_async_idempotency")
                .contains("create table outbox_event")
                .contains("event_type")
                .contains("default 'PENDING'");
    }
}
