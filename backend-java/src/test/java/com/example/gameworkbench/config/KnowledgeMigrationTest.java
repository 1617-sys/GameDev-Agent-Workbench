package com.example.gameworkbench.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class KnowledgeMigrationTest {
    @Test void migrationHasProjectScopedLifecycleAndChunkConstraints() throws Exception {
        String sql = new String(new ClassPathResource("db/migration/V21__add_knowledge_document_lifecycle.sql")
                .getInputStream().readAllBytes(), StandardCharsets.UTF_8).toLowerCase();
        assertThat(sql).contains("create table knowledge_document", "create table knowledge_chunk",
                "unique (project_id, content_hash)", "unique (project_id, version)",
                "unique (document_id, ordinal)", "foreign key (document_id, project_id)",
                "references knowledge_document (id, project_id)", "project_id, status, deleted");
    }
}
