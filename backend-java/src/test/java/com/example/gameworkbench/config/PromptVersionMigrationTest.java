package com.example.gameworkbench.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class PromptVersionMigrationTest {

    @Test
    void migrationBackfillsActiveTemplatesAndProtectsImmutableVersions() throws Exception {
        ClassPathResource migration = new ClassPathResource("db/migration/V4__add_prompt_version.sql");
        String sql = new String(migration.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        assertThat(migration.exists()).isTrue();
        assertThat(sql)
                .contains("create table prompt_version")
                .contains("unique (template_id, version, deleted)")
                .contains("from prompt_template template")
                .contains("template.status = 'ACTIVE'")
                .contains("existing_version.version = 1")
                .contains("add column prompt_version_id")
                .contains("create trigger trg_prompt_version_prevent_update")
                .contains("create trigger trg_prompt_version_prevent_delete")
                .contains("prompt_version is immutable; create a new version instead");
    }
}
