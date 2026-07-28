package com.example.gameworkbench.config;

import static org.assertj.core.api.Assertions.assertThat;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class DirectorFoundationMigrationTest {
    @Test void migrationAddsDedicatedTraceableDirectorTables() throws Exception {
        String sql=Files.readString(Path.of("src/main/resources/db/migration/V35__add_director_foundation.sql"));
        assertThat(sql).contains("create table director_run","state_version","create table director_decision","create table director_tool_call","create table experiment_candidate","prototype_version_uuid","player_run_uuid","machine_episode_uuid");
        assertThat(sql).doesNotContain("alter table workflow_run");
    }
}
