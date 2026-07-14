package com.example.gameworkbench.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import com.example.gameworkbench.common.enums.AgentType;
import com.example.gameworkbench.common.enums.ArtifactType;

class WorkflowDefinitionVersionMigrationTest {

    @Test
    void migrationCreatesVersionedDefinitionsAndSeedsTheCurrentWorkflows() throws Exception {
        ClassPathResource migration = new ClassPathResource("db/migration/V2__add_workflow_definition_version.sql");
        String sql = new String(migration.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        assertThat(migration.exists()).isTrue();
        assertThat(sql)
                .contains("create table workflow_definition_version")
                .contains("create table workflow_step_definition")
                .contains("unique (workflow_key, version)")
                .contains("'GAME_DESIGN'")
                .contains("'DEMO_GAME_CONFIG'")
                .contains("'ACTIVE'")
                .contains("'" + AgentType.GAME_CONCEPT.name() + "'")
                .contains("'" + AgentType.CORE_LOOP_DESIGN.name() + "'")
                .contains("'" + AgentType.TASK_BREAKDOWN.name() + "'")
                .contains("'" + AgentType.GAME_CONFIG_GENERATE.name() + "'")
                .contains("'" + ArtifactType.GAME_CONFIG.name() + "'");
    }

    @Test
    void gameGenerateMigrationProvidesTheFrontendSubmissionContract() throws Exception {
        ClassPathResource migration = new ClassPathResource("db/migration/V27__add_game_generate_workflow_definition.sql");
        String sql = new String(migration.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        assertThat(migration.exists()).isTrue();
        assertThat(sql)
                .contains("'GAME_GENERATE'")
                .contains("'ACTIVE'")
                .contains("'" + AgentType.GAME_CONCEPT.name() + "'")
                .contains("'" + AgentType.CORE_LOOP_DESIGN.name() + "'")
                .contains("'" + AgentType.TASK_BREAKDOWN.name() + "'")
                .contains("'" + AgentType.GAME_CONFIG_GENERATE.name() + "'")
                .contains("'" + ArtifactType.GAME_CONFIG.name() + "'");
    }
}
