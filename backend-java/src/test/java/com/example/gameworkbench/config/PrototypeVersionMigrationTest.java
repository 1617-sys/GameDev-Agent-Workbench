package com.example.gameworkbench.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.apache.ibatis.annotations.Select;

import com.example.gameworkbench.mapper.PrototypeVersionMapper;

class PrototypeVersionMigrationTest {
    @Test
    void migrationEnforcesImmutableConcurrentAndIdempotentVersioning() throws Exception {
        String sql = new String(new ClassPathResource("db/migration/V30__add_immutable_prototype_version.sql")
                .getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(sql).contains("prototype_version_sequence", "uk_prototype_version_number",
                "uk_prototype_version_idempotency", "uk_prototype_version_artifact",
                "trg_prototype_version_prevent_update", "trg_prototype_version_prevent_delete",
                "trg_versioned_artifact_freeze");
        Select lockQuery = PrototypeVersionMapper.class.getMethod("lockNextVersion", Long.class)
                .getAnnotation(Select.class);
        assertThat(String.join(" ", lockQuery.value())).containsIgnoringCase("for update");
    }
}
