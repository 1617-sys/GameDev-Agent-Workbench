package com.example.gameworkbench.config;

import static org.assertj.core.api.Assertions.assertThat;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class GenerationReleaseGateMigrationTest {
    @Test
    void migrationAddsBuildLeaseApprovalEvidenceAndReleaseState() throws Exception {
        String sql = Files.readString(Path.of("src/main/resources/db/migration/V38__converge_v5_generation_release_gate.sql"));
        assertThat(sql).contains("build_claim_token", "build_claim_expires_at", "build_attempt",
                "READY_TO_BUILD", "RELEASED", "create table generation_run_approval",
                "uk_generation_approval_run", "uk_generation_approval_idempotency");
    }
}
