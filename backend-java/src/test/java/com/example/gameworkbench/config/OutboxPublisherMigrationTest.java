package com.example.gameworkbench.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class OutboxPublisherMigrationTest {

    @Test
    void migrationAddsPublisherLeaseConfirmAndFailureEvidence() throws Exception {
        ClassPathResource migration = new ClassPathResource("db/migration/V10__extend_outbox_for_publisher_confirm.sql");
        String sql = new String(migration.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        assertThat(sql).contains("claim_owner varchar(64)")
                .contains("claim_until datetime")
                .contains("message_id varchar(64)")
                .contains("confirmed_at datetime")
                .contains("last_error_code varchar(80)")
                .contains("idx_outbox_event_claim_until");
    }
}
