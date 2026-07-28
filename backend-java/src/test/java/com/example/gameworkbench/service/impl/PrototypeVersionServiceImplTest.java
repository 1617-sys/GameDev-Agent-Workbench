package com.example.gameworkbench.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import org.junit.jupiter.api.Test;

import com.example.gameworkbench.common.enums.ErrorCode;
import com.example.gameworkbench.common.exception.BusinessException;
import com.example.gameworkbench.dto.prototype.TunePrototypeVersionRequest;
import com.example.gameworkbench.entity.AgentArtifact;
import com.example.gameworkbench.entity.GameProject;
import com.example.gameworkbench.entity.PrototypeVersion;
import com.example.gameworkbench.evaluation.EvaluationOrchestrator;
import com.example.gameworkbench.evaluation.RuntimeCapabilityRegistry;
import com.example.gameworkbench.gameconfig.GameConfigContract;
import com.example.gameworkbench.gameconfig.ResourceManifestContract;
import com.example.gameworkbench.mapper.AgentArtifactMapper;
import com.example.gameworkbench.mapper.GameProjectMapper;
import com.example.gameworkbench.mapper.PrototypeVersionMapper;
import com.example.gameworkbench.vo.prototype.PrototypeVersionVO;
import com.fasterxml.jackson.databind.ObjectMapper;

class PrototypeVersionServiceImplTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void concurrentCreatesReceiveDistinctProjectVersionNumbers() throws Exception {
        PrototypeVersionMapper versions = mock(PrototypeVersionMapper.class);
        GameProjectMapper projects = projectMapper();
        AgentArtifactMapper artifacts = mock(AgentArtifactMapper.class);
        when(artifacts.selectByArtifactUuid("artifact-a")).thenReturn(artifact("artifact-a"));
        when(artifacts.selectByArtifactUuid("artifact-b")).thenReturn(artifact("artifact-b"));
        ReentrantLock rowLock = new ReentrantLock();
        AtomicInteger next = new AtomicInteger(1);
        List<PrototypeVersion> inserted = java.util.Collections.synchronizedList(new ArrayList<>());
        when(versions.lockNextVersion(1L)).thenAnswer(invocation -> { rowLock.lock(); return next.get(); });
        when(versions.advanceSequence(any(), any())).thenAnswer(invocation -> {
            int expected = invocation.getArgument(1);
            int updated = next.compareAndSet(expected, expected + 1) ? 1 : 0;
            rowLock.unlock();
            return updated;
        });
        when(versions.insert(any(PrototypeVersion.class))).thenAnswer(invocation -> { inserted.add(invocation.getArgument(0)); return 1; });
        when(versions.selectLatest(1L)).thenAnswer(invocation -> inserted.stream()
                .max(java.util.Comparator.comparing(PrototypeVersion::getVersionNumber)).orElse(null));
        PrototypeVersionServiceImpl service = service(versions, projects, artifacts);

        CompletableFuture<PrototypeVersionVO> first = CompletableFuture.supplyAsync(() ->
                service.createFromArtifact(7L, "project", "key-a", "artifact-a"));
        CompletableFuture<PrototypeVersionVO> second = CompletableFuture.supplyAsync(() ->
                service.createFromArtifact(7L, "project", "key-b", "artifact-b"));

        assertThat(List.of(first.get().getVersionNumber(), second.get().getVersionNumber()))
                .containsExactlyInAnyOrder(1, 2);
        assertThat(inserted).extracting(PrototypeVersion::getVersionNumber).containsExactlyInAnyOrder(1, 2);
        PrototypeVersion version1 = inserted.stream().filter(value -> value.getVersionNumber() == 1).findFirst().orElseThrow();
        PrototypeVersion version2 = inserted.stream().filter(value -> value.getVersionNumber() == 2).findFirst().orElseThrow();
        assertThat(version1.getParentVersionUuid()).isNull();
        assertThat(version2.getParentVersionUuid()).isEqualTo(version1.getVersionUuid());
    }

    @Test
    void idempotentReplayReturnsSameVersionAndConflictIsRejected() throws Exception {
        PrototypeVersionMapper versions = mock(PrototypeVersionMapper.class);
        AgentArtifactMapper artifacts = mock(AgentArtifactMapper.class);
        AgentArtifact artifact = artifact("artifact-a");
        when(artifacts.selectByArtifactUuid("artifact-a")).thenReturn(artifact);
        PrototypeVersion existing = PrototypeVersion.builder().versionUuid("version-1").projectId(1L).versionNumber(1)
                .source("AI_GENERATED").gameConfigArtifactUuid("artifact-a").configDigest(artifact.getContentDigest())
                .runtimeCapabilityVersion(RuntimeCapabilityRegistry.VERSION).createdBy(7L).idempotencyKey("same-key")
                .requestFingerprint(fingerprint(Map.of("source", "AI_GENERATED", "artifactUuid", "artifact-a",
                        "configDigest", artifact.getContentDigest()))).build();
        when(versions.lockNextVersion(1L)).thenReturn(2);
        when(versions.selectIdempotent(7L, 1L, "CREATE_PROTOTYPE_VERSION", "same-key")).thenReturn(existing);
        PrototypeVersionServiceImpl service = service(versions, projectMapper(), artifacts);

        assertThat(service.createFromArtifact(7L, "project", "same-key", "artifact-a").isReused()).isTrue();
        existing.setRequestFingerprint("different");
        assertThatThrownBy(() -> service.createFromArtifact(7L, "project", "same-key", "artifact-a"))
                .isInstanceOf(BusinessException.class).hasMessage(ErrorCode.IDEMPOTENCY_KEY_CONFLICT.getMessage());
    }

    @Test
    void tuningCreatesValidatedChildArtifactsWithoutChangingParent() throws Exception {
        PrototypeVersionMapper versions = mock(PrototypeVersionMapper.class);
        AgentArtifactMapper artifacts = mock(AgentArtifactMapper.class);
        AgentArtifact parentArtifact = artifact("artifact-parent");
        String original = parentArtifact.getContent();
        PrototypeVersion parent = PrototypeVersion.builder().versionUuid("version-parent").projectId(1L)
                .versionNumber(1).gameConfigArtifactUuid("artifact-parent").build();
        when(versions.selectByUuid("version-parent")).thenReturn(parent);
        when(versions.lockNextVersion(1L)).thenReturn(2);
        when(versions.advanceSequence(1L, 2)).thenReturn(1);
        when(artifacts.selectByArtifactUuid("artifact-parent")).thenReturn(parentArtifact);
        List<AgentArtifact> insertedArtifacts = new ArrayList<>();
        when(artifacts.insert(any(AgentArtifact.class))).thenAnswer(invocation -> {
            AgentArtifact value = invocation.getArgument(0); value.setId((long) insertedArtifacts.size() + 10);
            insertedArtifacts.add(value); return 1;
        });
        List<PrototypeVersion> insertedVersions = new ArrayList<>();
        when(versions.insert(any(PrototypeVersion.class))).thenAnswer(invocation -> { insertedVersions.add(invocation.getArgument(0)); return 1; });
        EvaluationOrchestrator evaluator = mock(EvaluationOrchestrator.class);
        doAnswer(invocation -> { invocation.<AgentArtifact>getArgument(0).setRuntimeEligible(true); return null; })
                .when(evaluator).evaluate(any());
        PrototypeVersionServiceImpl service = service(versions, projectMapper(), artifacts, evaluator);
        TunePrototypeVersionRequest request = new TunePrototypeVersionRequest(); request.setTimeLimitSeconds(120);
        request.setEnemyCount(2); request.setEnemySpeeds(Map.of("enemy-1", 120));

        PrototypeVersionVO result = service.tune(7L, "project", "version-parent", "tune-1", request);

        assertThat(result.getVersionNumber()).isEqualTo(2);
        assertThat(result.getParentVersionUuid()).isEqualTo("version-parent");
        assertThat(result.getSource()).isEqualTo("TUNED");
        assertThat(result.getParameters()).containsEntry("timeLimitSeconds", 120);
        assertThat(result.getParameters()).containsEntry("enemyCount", 2);
        assertThat(parentArtifact.getContent()).isEqualTo(original);
        assertThat(insertedArtifacts).extracting(AgentArtifact::getArtifactType)
                .containsExactly("GAME_CONFIG", "RESOURCE_MANIFEST");
        assertThat(insertedVersions).singleElement().extracting(PrototypeVersion::getParentVersionUuid)
                .isEqualTo("version-parent");
    }

    @Test
    void crossProjectArtifactCannotBecomeAVersion() throws Exception {
        PrototypeVersionMapper versions = mock(PrototypeVersionMapper.class);
        AgentArtifactMapper artifacts = mock(AgentArtifactMapper.class);
        AgentArtifact foreign = artifact("foreign"); foreign.setProjectId(9L);
        when(artifacts.selectByArtifactUuid("foreign")).thenReturn(foreign);
        PrototypeVersionServiceImpl service = service(versions, projectMapper(), artifacts);
        assertThatThrownBy(() -> service.createFromArtifact(7L, "project", "key", "foreign"))
                .isInstanceOf(BusinessException.class).hasMessage(ErrorCode.FORBIDDEN_ARTIFACT_ACCESS.getMessage());
    }

    @Test
    void crossProjectVersionCannotBeRead() {
        PrototypeVersionMapper versions = mock(PrototypeVersionMapper.class);
        PrototypeVersion foreign = PrototypeVersion.builder().versionUuid("foreign-version").projectId(9L).build();
        when(versions.selectByUuid("foreign-version")).thenReturn(foreign);
        PrototypeVersionServiceImpl service = service(versions, projectMapper(), mock(AgentArtifactMapper.class));
        assertThatThrownBy(() -> service.get(7L, "project", "foreign-version"))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.FORBIDDEN_PROTOTYPE_VERSION_ACCESS.getMessage());
    }

    private PrototypeVersionServiceImpl service(PrototypeVersionMapper versions, GameProjectMapper projects,
            AgentArtifactMapper artifacts) {
        return service(versions, projects, artifacts, mock(EvaluationOrchestrator.class));
    }

    private PrototypeVersionServiceImpl service(PrototypeVersionMapper versions, GameProjectMapper projects,
            AgentArtifactMapper artifacts, EvaluationOrchestrator evaluator) {
        GameConfigContract contract = new GameConfigContract(json);
        ResourceManifestContract manifests = new ResourceManifestContract(json, new RuntimeCapabilityRegistry(), contract);
        return new PrototypeVersionServiceImpl(versions, projects, artifacts, contract, manifests, evaluator, json);
    }

    private GameProjectMapper projectMapper() {
        GameProjectMapper mapper = mock(GameProjectMapper.class);
        GameProject project = new GameProject(); project.setId(1L); project.setProjectUuid("project"); project.setUserId(7L);
        when(mapper.selectOne(any())).thenReturn(project);
        return mapper;
    }

    private AgentArtifact artifact(String uuid) throws Exception {
        String content = Files.readString(Path.of("..", "docs", "requirements", "v3", "examples",
                "game-config-2.0", "valid-minimal.json"));
        return AgentArtifact.builder().artifactUuid(uuid).projectId(1L).artifactType("GAME_CONFIG")
                .content(content).contentDigest(digest(content)).schemaKey("game-config").schemaVersion("2.0")
                .runtimeEligible(true).runtimeCapabilityVersion(RuntimeCapabilityRegistry.VERSION).build();
    }

    private String fingerprint(Object value) throws Exception {
        return digest(new GameConfigContract(json).canonicalJson((com.fasterxml.jackson.databind.node.ObjectNode) json.valueToTree(value)));
    }
    private String digest(String value) throws Exception {
        return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}
