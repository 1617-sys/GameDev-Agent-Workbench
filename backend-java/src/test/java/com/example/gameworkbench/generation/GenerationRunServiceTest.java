package com.example.gameworkbench.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.example.gameworkbench.artifact.PlayableArtifactAssembler;
import com.example.gameworkbench.artifact.PlayableArtifactStore;
import com.example.gameworkbench.artifact.PlayableArtifact;
import com.example.gameworkbench.cocos.CocosBuildResult;
import com.example.gameworkbench.cocos.CocosBuildWorker;
import com.example.gameworkbench.common.enums.ErrorCode;
import com.example.gameworkbench.common.exception.BusinessException;
import com.example.gameworkbench.entity.GameProject;
import com.example.gameworkbench.entity.GenerationRun;
import com.example.gameworkbench.entity.GenerationRunApproval;
import com.example.gameworkbench.dto.gamespec.GenerationApprovalRequest;
import com.example.gameworkbench.gamespec.ArcadeCollectCapabilityRegistry;
import com.example.gameworkbench.gamespec.GameSpecCompiler;
import com.example.gameworkbench.mapper.GameProjectMapper;
import com.example.gameworkbench.mapper.GenerationRunMapper;
import com.example.gameworkbench.mapper.GenerationRunApprovalMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class GenerationRunServiceTest {
    @TempDir Path temporary;
    private final ObjectMapper json = new ObjectMapper();
    private final GenerationRunMapper runs = mock(GenerationRunMapper.class);
    private final GenerationRunApprovalMapper approvals = mock(GenerationRunApprovalMapper.class);
    private final GameProjectMapper projects = mock(GameProjectMapper.class);
    private final CocosBuildWorker buildWorker = mock(CocosBuildWorker.class);
    private final PlayableArtifactAssembler artifactAssembler = mock(PlayableArtifactAssembler.class);
    private final PlayableArtifactStore artifactStore = mock(PlayableArtifactStore.class);
    private GenerationRunService service;

    @BeforeEach
    void setUp() {
        GameProject project = new GameProject();
        project.setId(7L); project.setProjectUuid("project-1"); project.setUserId(11L);
        when(projects.selectOne(any())).thenReturn(project);
        var capabilities = new ArcadeCollectCapabilityRegistry(json);
        service = new GenerationRunService(runs, approvals, projects, new GameSpecCompiler(json, capabilities),
                buildWorker, artifactAssembler, artifactStore, json);
    }

    @Test
    void persistsSuccessfulCompilationReadyForCocosBuild() throws Exception {
        GenerationRun run = service.create(11L, "project-1", "generate-1", fixture());

        assertThat(run.getStatus()).isEqualTo("READY_TO_BUILD");
        assertThat(run.getBuildAttempt()).isZero();
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

    @Test
    void claimsBeforeBuildingAndMovesSuccessfulArtifactToApprovalGate() throws Exception {
        GenerationRun run = readyRun();
        GenerationRun claimed = claimedRun();
        when(runs.selectByUuid("run-1")).thenReturn(run, claimed);
        when(runs.claimBuild(any(Long.class), any(Long.class), any(Long.class), any(), any()))
                .thenAnswer(invocation -> {
                    claimed.setBuildClaimToken(invocation.getArgument(3));
                    return 1;
                });
        when(buildWorker.build(any(), any())).thenReturn(new CocosBuildResult(
                CocosBuildResult.Status.SUCCEEDED, 0, "log", "output", Path.of("output")));
        PlayableArtifact artifact = new PlayableArtifact("a".repeat(64), "b".repeat(64), "c".repeat(64),
                json.createObjectNode(), new byte[] {1});
        when(artifactAssembler.assemble(any(), any(), any())).thenReturn(artifact);
        when(runs.completeBuild(any(Long.class), any(Long.class), any(Long.class), any(), any(), any(), any(), any(Boolean.class)))
                .thenReturn(1);

        GenerationBuildOutcome outcome = service.build(11L, "project-1", "run-1", 3L);

        assertThat(outcome.status()).isEqualTo("AWAITING_APPROVAL");
        verify(buildWorker).build(any(), any());
        verify(artifactAssembler).assemble(argThat(value -> "BUILDING".equals(value.getStatus())
                && value.getStateVersion() == 4L && value.getBuildClaimToken() != null), any(), any());
        verify(runs).completeBuild(any(Long.class), any(Long.class), org.mockito.ArgumentMatchers.eq(4L), any(),
                org.mockito.ArgumentMatchers.eq("AWAITING_APPROVAL"),
                org.mockito.ArgumentMatchers.eq("c".repeat(64)),
                org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.eq(false));
    }

    @Test
    void usesDurableClaimSnapshotWithRealArtifactAssembler() throws Exception {
        Path output = temporary.resolve("cocos-output");
        Files.createDirectories(output.resolve("assets"));
        Files.writeString(output.resolve("index.html"), "<!doctype html><script src=\"assets/main.js\"></script>");
        Files.writeString(output.resolve("assets/main.js"), "console.log('ready')");

        GenerationRun ready = readyRun();
        ready.setCanonicalSpecJson("{}");
        ready.setSourceDigest("a".repeat(64));
        ready.setRuntimeIrJson("{}");
        ready.setRuntimeIrDigest("b".repeat(64));
        ready.setBuildRequestJson("{}");
        GenerationRun claimed = claimedRun();
        claimed.setCanonicalSpecJson(ready.getCanonicalSpecJson());
        claimed.setSourceDigest(ready.getSourceDigest());
        claimed.setRuntimeIrJson(ready.getRuntimeIrJson());
        claimed.setRuntimeIrDigest(ready.getRuntimeIrDigest());
        claimed.setBuildRequestJson(ready.getBuildRequestJson());

        when(runs.selectByUuid("run-1")).thenReturn(ready, claimed);
        when(runs.claimBuild(any(Long.class), any(Long.class), any(Long.class), any(), any()))
                .thenAnswer(invocation -> {
                    claimed.setBuildClaimToken(invocation.getArgument(3));
                    return 1;
                });
        when(buildWorker.build(any(), any())).thenReturn(new CocosBuildResult(
                CocosBuildResult.Status.SUCCEEDED, 0, "c".repeat(64), "d".repeat(64), output));
        when(runs.completeBuild(any(Long.class), any(Long.class), any(Long.class), any(), any(), any(), any(),
                any(Boolean.class))).thenReturn(1);

        GenerationRunService realAssemblerService = new GenerationRunService(
                runs, approvals, projects,
                new GameSpecCompiler(json, new ArcadeCollectCapabilityRegistry(json)),
                buildWorker, new PlayableArtifactAssembler(json), artifactStore, json);

        GenerationBuildOutcome outcome = realAssemblerService.build(11L, "project-1", "run-1", 3L);

        assertThat(outcome.status()).isEqualTo("AWAITING_APPROVAL");
        assertThat(outcome.packageDigest()).hasSize(64);
        verify(artifactStore).put(org.mockito.ArgumentMatchers.eq("run-1"), any(PlayableArtifact.class));
    }

    @Test
    void doesNotStartCocosWhenAnotherCallerWinsBuildClaim() throws Exception {
        when(runs.selectByUuid("run-1")).thenReturn(readyRun());
        when(runs.claimBuild(any(Long.class), any(Long.class), any(Long.class), any(), any())).thenReturn(0);

        assertThatThrownBy(() -> service.build(11L, "project-1", "run-1", 3L))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getCode())
                        .isEqualTo(ErrorCode.GENERATION_RUN_CONCURRENT_UPDATE.getCode()));
        verifyNoInteractions(buildWorker);
    }

    @Test
    void doesNotStartCocosWhenReloadedClaimBelongsToAnotherWorker() throws Exception {
        GenerationRun claimedByAnotherWorker = claimedRun();
        claimedByAnotherWorker.setBuildClaimToken("another-worker-token");
        when(runs.selectByUuid("run-1")).thenReturn(readyRun(), claimedByAnotherWorker);
        when(runs.claimBuild(any(Long.class), any(Long.class), any(Long.class), any(), any())).thenReturn(1);

        assertThatThrownBy(() -> service.build(11L, "project-1", "run-1", 3L))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getCode())
                        .isEqualTo(ErrorCode.GENERATION_RUN_CONCURRENT_UPDATE.getCode()));
        verifyNoInteractions(buildWorker);
    }

    @Test
    void recordsApprovalAndRequiresExplicitRelease() {
        GenerationRun waiting = readyRun();
        waiting.setStatus("AWAITING_APPROVAL");
        waiting.setStateVersion(5L);
        waiting.setPackageDigest("c".repeat(64));
        when(runs.selectByUuid("run-1")).thenReturn(waiting);
        when(runs.transitionStatus(9L, 7L, 5L, "AWAITING_APPROVAL", "APPROVED", false)).thenReturn(1);

        GenerationApprovalOutcome outcome = service.approve(11L, "project-1", "run-1", "approval-1",
                new GenerationApprovalRequest("APPROVED", "manual playtest passed"));

        assertThat(outcome.decision()).isEqualTo("APPROVED");
        assertThat(outcome.reused()).isFalse();
        verify(approvals).insert(any(GenerationRunApproval.class));
    }

    @Test
    void blocksFormalArtifactBeforeExplicitRelease() {
        GenerationRun approved = readyRun();
        approved.setStatus("APPROVED");
        approved.setPackageDigest("c".repeat(64));
        when(runs.selectByUuid("run-1")).thenReturn(approved);

        assertThatThrownBy(() -> service.artifact(11L, "project-1", "run-1"))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getCode())
                        .isEqualTo(ErrorCode.GENERATION_RELEASE_FORBIDDEN.getCode()));
    }

    @Test
    void releasesOnlyAnApprovedRunWithTheExpectedVersion() {
        GenerationRun approved = readyRun();
        approved.setStatus("APPROVED");
        approved.setStateVersion(6L);
        GenerationRun released = readyRun();
        released.setStatus("RELEASED");
        released.setStateVersion(7L);
        when(runs.selectByUuid("run-1")).thenReturn(approved, released);
        when(runs.transitionStatus(9L, 7L, 6L, "APPROVED", "RELEASED", true)).thenReturn(1);

        assertThat(service.release(11L, "project-1", "run-1", 6L).getStatus()).isEqualTo("RELEASED");
    }

    private GenerationRun readyRun() {
        return GenerationRun.builder().id(9L).runUuid("run-1").userId(11L).projectId(7L)
                .status("READY_TO_BUILD").stateVersion(3L).buildClaimExpiresAt(LocalDateTime.now().minusMinutes(1))
                .buildRequestJson("{}").runtimeIrJson("{}").build();
    }

    private GenerationRun claimedRun() {
        return GenerationRun.builder().id(9L).runUuid("run-1").userId(11L).projectId(7L)
                .status("BUILDING").stateVersion(4L).buildAttempt(1)
                .buildClaimExpiresAt(LocalDateTime.now().plusMinutes(12))
                .buildRequestJson("{}").runtimeIrJson("{}").build();
    }

    private JsonNode fixture() throws Exception {
        try (InputStream stream = getClass().getResourceAsStream("/gamespec/arcade-collect-valid.json")) {
            return json.readTree(stream);
        }
    }
}
