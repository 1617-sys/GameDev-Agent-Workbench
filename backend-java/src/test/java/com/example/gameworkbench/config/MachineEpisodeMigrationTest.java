package com.example.gameworkbench.config;

import static org.assertj.core.api.Assertions.assertThat;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class MachineEpisodeMigrationTest {
    @Test void migrationCreatesIsolatedTraceableMachineEpisodeTables() throws Exception {
        String sql = Files.readString(Path.of("src/main/resources/db/migration/V33__add_machine_episode_persistence.sql"));
        assertThat(sql).contains("machine_episode_batch", "machine_episode", "machine_episode_step",
                "prototype_version_uuid", "config_digest", "sample_source", "trajectory_digest",
                "uk_machine_episode_batch_idempotency", "uk_machine_episode_step_sequence",
                "trg_machine_episode_step_prevent_update");
        assertThat(sql.toLowerCase()).doesNotContain("insert into playtest_session", "insert into playtest_event",
                "alter table playtest_session", "alter table playtest_event");
    }
}
