package com.example.gameworkbench.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.InputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.example.gameworkbench.artifact.PlayableArtifactAssembler;
import com.example.gameworkbench.artifact.PlayableArtifactStore;
import com.example.gameworkbench.cocos.CocosBuildWorker;
import com.example.gameworkbench.common.enums.ErrorCode;
import com.example.gameworkbench.common.exception.BusinessException;
import com.example.gameworkbench.entity.GameProject;
import com.example.gameworkbench.entity.GenerationRun;
import com.example.gameworkbench.gamespec.ArcadeCollectCapabilityRegistry;
import com.example.gameworkbench.gamespec.GameSpecCompiler;
import com.example.gameworkbench.mapper.GameProjectMapper;
import com.example.gameworkbench.mapper.GenerationRunMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class GenerationRunServiceTest {
    private final ObjectMapper json = new ObjectMapper();
    private final GenerationRunMapper runs = mock(GenerationRunMapper.class);
    private final GameProjectMapper projects = mock(GameProjectMapper.class);
    private GenerationRunService service;

    @BeforeEach
    void setUp() {
        GameProject project = new GameProject();
        project.setId(7L); project.setProjectUuid("project-1"); project.setUserId(11L);
        when(projects.selectOne(any())).thenReturn(project);
        var capabilities = new ArcadeCollectCapabilityRegistry(json);
        service = new GenerationRunService(runs, projects, new GameSpecCompiler(json, capabilities),
                mock(CocosBuildWorker.class), mock(PlayableArtifactAssembler.class), mock(PlayableArtifactStore.class), json);
    }

    @Test
    void persistsSuccessfulCompilationReadyForCocosBuild() throws Exception {
        GenerationRun run = service.create(11L, "project-1", "generate-1", fixture());

        assertThat(run.getStatus()).isEqualTo("BUILDING");
        assertThat(run.getSourceDigest()).hasSize(64);
        assertThat(run.getRuntimeIrDigest()).hasSize(64);
        assertThat(run.getBuildRequestJson()).contains("web-mobile");
        verify(runs).insert(run);
    }

    @Test
    void persistsDiagnosticsInsteadOfProducingCandidate() throws Exception {
        JsonNode spec = fixture();
        ((com.fasterxml.jackson.databind.node.ObjectNode) spec).put("runtimeScript", "anything");

        GenerationRun run = service.create(11L, "project-1", "generate-invalid", spec);

        assertThat(run.getStatus()).isEqualTo("FAILED");
        assertThat(run.getCanonicalSpecJson()).isNull();
        assertThat(run.getDiagnosticsJson()).contains("GS1001_UNKNOWN_FIELD");
        assertThat(run.getErrorCode()).isEqualTo("GAMESPEC_VALIDATION_FAILED");
    }

    @Test
    void rejectsIdempotencyKeyReuseWithDifferentInput() throws Exception {
        GenerationRun existing = GenerationRun.builder().requestFingerprint("0".repeat(64)).build();
        when(runs.selectByIdempotency(11L, 7L, "same-key")).thenReturn(existing);

        assertThatThrownBy(() -> service.create(11L, "project-1", "same-key", fixture()))
                .isInstanceOfSatisfying(BusinessException.class, error ->
                        assertThat(error.getCode()).isEqualTo(ErrorCode.GENERATION_RUN_IDEMPOTENCY_CONFLICT.getCode()));
    }

    private JsonNode fixture() throws Exception {
        try (InputStream stream = getClass().getResourceAsStream("/gamespec/arcade-collect-valid.json")) {
            return json.readTree(stream);
        }
    }
}
