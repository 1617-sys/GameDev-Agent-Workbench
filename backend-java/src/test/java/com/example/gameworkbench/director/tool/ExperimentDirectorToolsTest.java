package com.example.gameworkbench.director.tool;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;
import com.example.gameworkbench.experiment.PlayerExperimentService;
import com.example.gameworkbench.experiment.candidate.DeterministicCandidateGenerator;
import com.example.gameworkbench.prototype.PrototypeDraftService;
import com.fasterxml.jackson.databind.ObjectMapper;

class ExperimentDirectorToolsTest {
    @Test void exposesBoundedToolsButNeverHumanApproveOrPublish(){var tools=ExperimentDirectorTools.create(mock(PrototypeDraftService.class),mock(DeterministicCandidateGenerator.class),mock(PlayerExperimentService.class),new ObjectMapper());assertThat(tools).extracting(tool->tool.definition().name()).containsExactly("CREATE_DRAFT_VERSION","REQUEST_HUMAN_APPROVAL","GENERATE_NEIGHBOR_CANDIDATES","RUN_PLAYER_EXPERIMENT","GET_EXPERIMENT_STATUS","COMPARE_CANDIDATE_METRICS");assertThat(tools).allSatisfy(tool->assertThat(tool.definition().argumentSchema().path("additionalProperties").asBoolean()).isFalse());assertThat(tools).extracting(tool->tool.definition().name()).noneMatch(name->name.contains("APPROVE")||name.contains("PUBLISH"));}
}
