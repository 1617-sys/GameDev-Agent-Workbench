package com.example.gameworkbench.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class FlywayMigrationContextTest {

    @Test
    void baselineMigrationContainsTheCompleteCurrentSchema() throws Exception {
        ClassPathResource migration = new ClassPathResource("db/migration/V1__baseline.sql");
        String sql = new String(migration.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        assertThat(migration.exists()).isTrue();
        assertThat(sql.toLowerCase())
                .contains("create table sys_user")
                .contains("create table game_project")
                .contains("create table agent_run")
                .contains("create table agent_artifact")
                .contains("create table workflow_run")
                .contains("create table prompt_template")
                .doesNotContain("create database")
                .doesNotContain("use gamedev_agent_workbench");
    }
}
