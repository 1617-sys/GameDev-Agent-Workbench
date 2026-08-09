package com.example.gameworkbench.config;

import static org.assertj.core.api.Assertions.assertThat;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class GenerationControlPlaneMigrationTest {
    @Test
    void migrationPersistsFrozenCompilationAndArtifactLineage() throws Exception {
        String sql = Files.readString(Path.of("src/main/resources/db/migration/V37__add_v5_generation_control_plane.sql"));
        assertThat(sql).contains("create table generation_run", "request_fingerprint", "canonical_spec_json",
                "runtime_ir_digest", "build_request_json", "package_digest", "state_version");
        assertThat(sql).contains("uk_generation_run_idempotency", "chk_generation_run_status");
    }
}
