package com.example.gameworkbench.generation;

import com.example.gameworkbench.common.exception.BusinessException;
import com.example.gameworkbench.entity.GenerationRun;
import com.example.gameworkbench.service.PrototypeVersionService;
import com.example.gameworkbench.vo.prototype.PrototypeVersionVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

class GenerationPrototypeBridgeServiceTest {
    private final GenerationRunService generations = mock(GenerationRunService.class);
    private final PrototypeVersionService prototypes = mock(PrototypeVersionService.class);
    private final GenerationPrototypeBridgeService service =
            new GenerationPrototypeBridgeService(generations, prototypes, new ObjectMapper());

    @Test
    void createsPrototypeFromExplicitRegisteredBridgeContract() {
        when(generations.get(11L, "project-1", "run-1")).thenReturn(run("""
                {"playerBridge":{"contractVersion":"prototype-version/1","gameConfigArtifactUuid":"artifact-1"}}
                """));
        when(prototypes.createFromArtifact(11L, "project-1", "bridge-key", "artifact-1"))
                .thenReturn(PrototypeVersionVO.builder().versionUuid("version-1").reused(false).build());

        var result = service.bridge(11L, "project-1", "run-1", "bridge-key");

        assertThat(result.compatible()).isTrue();
        assertThat(result.prototypeVersionUuid()).isEqualTo("version-1");
        assertThat(result.source().runUuid()).isEqualTo("run-1");
    }

    @Test
    void repeatedSubmissionUsesCallerKeyAndArtifactFingerprintBinding() {
        when(generations.get(11L, "project-1", "run-1")).thenReturn(run("""
                {"playerBridge":{"contractVersion":"prototype-version/1","gameConfigArtifactUuid":"artifact-1"}}
                """));
        when(prototypes.createFromArtifact(11L, "project-1", "bridge-key", "artifact-1"))
                .thenReturn(PrototypeVersionVO.builder().versionUuid("version-1").reused(false).build())
                .thenReturn(PrototypeVersionVO.builder().versionUuid("version-1").reused(true).build());

        assertThat(service.bridge(11L, "project-1", "run-1", "bridge-key").reused()).isFalse();
        assertThat(service.bridge(11L, "project-1", "run-1", "bridge-key").reused()).isTrue();
        verify(prototypes, org.mockito.Mockito.times(2))
                .createFromArtifact(11L, "project-1", "bridge-key", "artifact-1");
    }

    @Test
    void preservesCrossProjectAuthorizationFailure() {
        BusinessException forbidden = mock(BusinessException.class);
        when(generations.get(11L, "other-project", "run-1")).thenThrow(forbidden);

        assertThatThrownBy(() -> service.bridge(11L, "other-project", "run-1", "bridge-key")).isSameAs(forbidden);
        verifyNoInteractions(prototypes);
    }

    @Test
    void returnsStructuredReasonsInsteadOfPretendingCurrentV5RuntimeIsCompatible() {
        when(generations.get(11L, "project-1", "run-1")).thenReturn(run("""
                {"runtimeIrVersion":"arcade_collect/1","entities":[]}
                """));

        var result = service.bridge(11L, "project-1", "run-1", "bridge-key");

        assertThat(result.compatible()).isFalse();
        assertThat(result.prototypeVersionUuid()).isNull();
        assertThat(result.reasons()).extracting(GenerationPrototypeBridgeResponse.Incompatibility::code)
                .contains("PLAYER_BRIDGE_DECLARATION_MISSING");
        verifyNoInteractions(prototypes);
    }

    @Test
    void compatibilityInspectValidatesTheActualV4ArtifactContract() {
        when(generations.get(11L, "project-1", "run-1")).thenReturn(run("""
                {"playerBridge":{"contractVersion":"prototype-version/1","gameConfigArtifactUuid":"artifact-1"}}
                """));

        assertThat(service.inspect(11L, "project-1", "run-1").compatible()).isTrue();
        verify(prototypes).validateSourceArtifact(11L, "project-1", "artifact-1");
    }

    @Test
    void ineligibleV4ArtifactIsReportedAsStructuredIncompatibility() {
        when(generations.get(11L, "project-1", "run-1")).thenReturn(run("""
                {"playerBridge":{"contractVersion":"prototype-version/1","gameConfigArtifactUuid":"artifact-1"}}
                """));
        doThrow(new BusinessException(com.example.gameworkbench.common.enums.ErrorCode.PROTOTYPE_ARTIFACT_NOT_ELIGIBLE))
                .when(prototypes).validateSourceArtifact(11L, "project-1", "artifact-1");

        assertThat(service.inspect(11L, "project-1", "run-1").reasons())
                .extracting(GenerationPrototypeBridgeResponse.Incompatibility::code)
                .containsExactly("GAME_CONFIG_ARTIFACT_INCOMPATIBLE");
    }

    private GenerationRun run(String runtimeIr) {
        return GenerationRun.builder().runUuid("run-1").status("RELEASED")
                .sourceDigest("source-digest").runtimeIrDigest("runtime-digest")
                .runtimeIrJson(runtimeIr).build();
    }
}
