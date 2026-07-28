package com.example.gameworkbench.config;

import static org.assertj.core.api.Assertions.assertThat;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class DirectorExperimentLoopMigrationTest {
    @Test void addsClaimsDraftApprovalExperimentAndComparisonEvidence()throws Exception{String sql=Files.readString(Path.of("src/main/resources/db/migration/V36__add_director_experiment_loop.sql"));assertThat(sql).contains("claim_token","director_run_event","lifecycle_status","default 'APPROVED'","prototype_approval","actor_type = 'USER'","director_experiment_run","experiment_comparison","drop trigger trg_prototype_version_prevent_update");assertThat(sql).doesNotContain("update prototype_version set game_config");}
}
