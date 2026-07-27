package com.example.gameworkbench.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;
import com.example.gameworkbench.common.enums.ErrorCode;
import com.example.gameworkbench.common.exception.BusinessException;
import com.example.gameworkbench.dto.episode.PersistMachineEpisodeBatchRequest;
import com.example.gameworkbench.dto.episode.PersistMachineEpisodeResultRequest;
import com.example.gameworkbench.entity.GameProject;
import com.example.gameworkbench.entity.MachineEpisode;
import com.example.gameworkbench.entity.MachineEpisodeBatch;
import com.example.gameworkbench.entity.MachineEpisodeStep;
import com.example.gameworkbench.entity.PrototypeVersion;
import com.example.gameworkbench.mapper.GameProjectMapper;
import com.example.gameworkbench.mapper.MachineEpisodeBatchMapper;
import com.example.gameworkbench.mapper.MachineEpisodeMapper;
import com.example.gameworkbench.mapper.MachineEpisodeStepMapper;
import com.example.gameworkbench.mapper.PrototypeVersionMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

class MachineEpisodeServiceImplTest {
    private static final String VERSION = "00000000-0000-4000-8000-000000000010";
    private static final String DIGEST = "a".repeat(64);
    private final ObjectMapper json = new ObjectMapper();

    @Test void sameIdempotencyKeyReturnsOriginalBatchWithoutDuplicateEpisodes() {
        Fixture fixture = fixture();
        PersistMachineEpisodeBatchRequest request = batch(List.of(completed(1)));
        var first = fixture.service.persistBatch(7L, "project", "episode-key-001", request);
        when(fixture.batches.selectIdempotent(7L, 1L, "episode-key-001")).thenReturn(fixture.savedBatch());
        var replay = fixture.service.persistBatch(7L, "project", "episode-key-001", request);

        assertThat(first.isReused()).isFalse();
        assertThat(replay.isReused()).isTrue();
        assertThat(replay.getBatchId()).isEqualTo(first.getBatchId());
        verify(fixture.batches, org.mockito.Mockito.times(1)).insert(any(MachineEpisodeBatch.class));
        verify(fixture.episodes, org.mockito.Mockito.times(1)).insert(any(MachineEpisode.class));
    }

    @Test void configDigestMismatchRejectsTheWholeTransactionBeforeAnyWrite() {
        Fixture fixture = fixture();
        PersistMachineEpisodeResultRequest input = completed(1);
        input.setConfigDigest("b".repeat(64));
        assertThatThrownBy(() -> fixture.service.persistBatch(7L, "project", "episode-key-002", batch(List.of(input))))
                .isInstanceOf(BusinessException.class).hasMessage(ErrorCode.EPISODE_BINDING_MISMATCH.getMessage());
        verify(fixture.batches, never()).insert(any(MachineEpisodeBatch.class));
        verify(fixture.episodes, never()).insert(any(MachineEpisode.class));
        verify(fixture.steps, never()).insert(any(MachineEpisodeStep.class));
    }

    @Test void partialFailurePersistsCompletedEvidenceAndFailedSibling() {
        Fixture fixture = fixture();
        PersistMachineEpisodeResultRequest failed = failed(2);
        var result = fixture.service.persistBatch(7L, "project", "episode-key-003", batch(List.of(completed(1), failed)));

        assertThat(result.getStatus()).isEqualTo("PARTIAL_SUCCESS");
        assertThat(result.getCompleted()).isEqualTo(1);
        assertThat(result.getFailed()).isEqualTo(1);
        assertThat(fixture.savedEpisodes).extracting(MachineEpisode::getExecutionStatus)
                .containsExactly("COMPLETED", "FAILED");
        assertThat(fixture.savedSteps).hasSize(1);
        assertThat(fixture.savedSteps.getFirst().getStepJson()).contains("observationDigest", "requestedAction");
    }

    @Test void crossProjectEpisodeReadIsForbidden() {
        Fixture fixture = fixture();
        MachineEpisode other = MachineEpisode.builder().id(20L).episodeUuid(uuid(9)).projectId(2L).batchId(10L).build();
        when(fixture.episodes.selectByUuid(uuid(9))).thenReturn(other);
        assertThatThrownBy(() -> fixture.service.getEpisode(7L, "project", uuid(9)))
                .isInstanceOf(BusinessException.class).hasMessage(ErrorCode.FORBIDDEN_EPISODE_ACCESS.getMessage());
        verify(fixture.steps, never()).selectByEpisodeId(any());
    }

    @Test void crossProjectPrototypeBindingCannotBeWritten() {
        Fixture fixture = fixture();
        when(fixture.versions.selectByUuid(VERSION)).thenReturn(PrototypeVersion.builder()
                .versionUuid(VERSION).projectId(2L).configDigest(DIGEST).build());
        assertThatThrownBy(() -> fixture.service.persistBatch(7L, "project", "episode-key-005",
                batch(List.of(completed(1)))))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.FORBIDDEN_PROTOTYPE_VERSION_ACCESS.getMessage());
        verify(fixture.batches, never()).insert(any(MachineEpisodeBatch.class));
    }

    @Test void aggregateIsMachineOnlyAndTraceableToEpisodeResults() {
        Fixture fixture = fixture();
        MachineEpisode won = aggregateEpisode(1, "COMPLETED", "WON", 100L, 3);
        MachineEpisode timeout = aggregateEpisode(2, "COMPLETED", "TIME_EXPIRED", 300L, 5);
        MachineEpisode failed = aggregateEpisode(3, "FAILED", null, 200L, 2);
        when(fixture.episodes.selectForAggregate(1L, VERSION)).thenReturn(List.of(won, timeout, failed));
        var aggregate = fixture.service.aggregate(7L, "project", VERSION);

        assertThat(aggregate.getSampleSource()).isEqualTo("MACHINE");
        assertThat(aggregate.getSampleSize()).isEqualTo(3);
        assertThat(aggregate.getCompletedCount()).isEqualTo(2);
        assertThat(aggregate.getFailedCount()).isEqualTo(1);
        assertThat(aggregate.getAverageDurationMs()).isEqualTo(200);
        assertThat(aggregate.getTerminationReasons()).containsEntry("WON", 1).containsEntry("TIME_EXPIRED", 1);
        assertThat(aggregate.getEpisodeResultRefs()).containsExactly(
                "episodes/" + uuid(1) + "/result", "episodes/" + uuid(2) + "/result", "episodes/" + uuid(3) + "/result");
    }

    @Test void persistenceBoundaryIsTransactionalSoStepFailureRollsBackTheBatchGraph() throws Exception {
        Method method = MachineEpisodeServiceImpl.class.getMethod("persistBatch", Long.class, String.class,
                String.class, PersistMachineEpisodeBatchRequest.class);
        assertThat(method.isAnnotationPresent(Transactional.class)).isTrue();
        Fixture fixture = fixture();
        when(fixture.steps.insert(any(MachineEpisodeStep.class))).thenThrow(new IllegalStateException("injected step persistence failure"));
        assertThatThrownBy(() -> fixture.service.persistBatch(7L, "project", "episode-key-004", batch(List.of(completed(1)))))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("injected step persistence failure");
        verify(fixture.batches).insert(any(MachineEpisodeBatch.class));
        verify(fixture.episodes).insert(any(MachineEpisode.class));
    }

    private Fixture fixture() {
        GameProjectMapper projects = mock(GameProjectMapper.class);
        PrototypeVersionMapper versions = mock(PrototypeVersionMapper.class);
        MachineEpisodeBatchMapper batches = mock(MachineEpisodeBatchMapper.class);
        MachineEpisodeMapper episodes = mock(MachineEpisodeMapper.class);
        MachineEpisodeStepMapper steps = mock(MachineEpisodeStepMapper.class);
        GameProject project = new GameProject(); project.setId(1L); project.setUserId(7L); project.setProjectUuid("project");
        when(projects.selectOne(any())).thenReturn(project);
        when(versions.selectByUuid(VERSION)).thenReturn(PrototypeVersion.builder()
                .versionUuid(VERSION).projectId(1L).configDigest(DIGEST).build());
        List<MachineEpisode> savedEpisodes = new ArrayList<>();
        List<MachineEpisodeStep> savedSteps = new ArrayList<>();
        MachineEpisodeBatch[] savedBatch = new MachineEpisodeBatch[1];
        when(batches.insert(any(MachineEpisodeBatch.class))).thenAnswer(invocation -> { MachineEpisodeBatch value = invocation.getArgument(0); value.setId(10L); savedBatch[0] = value; return 1; });
        when(episodes.insert(any(MachineEpisode.class))).thenAnswer(invocation -> { MachineEpisode value = invocation.getArgument(0); value.setId(20L + savedEpisodes.size()); savedEpisodes.add(value); return 1; });
        when(steps.insert(any(MachineEpisodeStep.class))).thenAnswer(invocation -> { savedSteps.add(invocation.getArgument(0)); return 1; });
        when(episodes.selectByBatchId(10L)).thenAnswer(invocation -> new ArrayList<>(savedEpisodes));
        MachineEpisodeServiceImpl service = new MachineEpisodeServiceImpl(projects, versions, batches, episodes, steps, json);
        return new Fixture(service, versions, batches, episodes, steps, savedEpisodes, savedSteps, savedBatch);
    }

    private PersistMachineEpisodeBatchRequest batch(List<PersistMachineEpisodeResultRequest> values) {
        PersistMachineEpisodeBatchRequest request = new PersistMachineEpisodeBatchRequest();
        request.setEpisodeProtocolVersion("episode/1.0"); request.setClientBatchKey("batch-fixture"); request.setEpisodes(values);
        return request;
    }

    private PersistMachineEpisodeResultRequest completed(int index) {
        PersistMachineEpisodeResultRequest value = base(index);
        ObjectNode step = json.createObjectNode(); step.put("sequence", 1); step.put("attempt", 1);
        step.put("simulationStepBefore", 0); step.put("simulationStepAfter", 1); step.put("observationDigest", "c".repeat(64));
        step.putObject("observation").put("stateHash", "d".repeat(64));
        step.putObject("decision").set("requestedAction", json.createObjectNode().put("type", "WAIT"));
        step.putObject("transition").put("accepted", true).put("stateHash", "e".repeat(64));
        step.putObject("reward").put("valueMicros", 0);
        value.setExecutionStatus("COMPLETED"); value.setTerminationReason("MAX_STEPS"); value.setOutcome("TRUNCATED");
        value.setStepCount(1); value.setAcceptedActionCount(1); value.setInvalidActionCount(0);
        value.setFinalStateHash("e".repeat(64)); value.setFinalScore(0); value.setSteps(List.of(step));
        value.setTrajectoryDigest(digest(canonical(json.valueToTree(value.getSteps())))); value.setWallDurationMs(50L);
        return value;
    }

    private PersistMachineEpisodeResultRequest failed(int index) {
        PersistMachineEpisodeResultRequest value = base(index);
        value.setExecutionStatus("FAILED"); value.setOutcome("ERROR"); value.setStepCount(0);
        value.setAcceptedActionCount(0); value.setInvalidActionCount(0); value.setFinalStateHash("f".repeat(64));
        value.setFinalScore(0); value.setSteps(List.of()); value.setTrajectoryDigest(digest("[]")); value.setWallDurationMs(10L);
        return value;
    }

    private PersistMachineEpisodeResultRequest base(int index) {
        PersistMachineEpisodeResultRequest value = new PersistMachineEpisodeResultRequest();
        value.setEpisodeId(uuid(index)); value.setClientEpisodeKey("episode-" + index); value.setPrototypeVersionUuid(VERSION);
        value.setConfigDigest(DIGEST); value.setSimulationProtocolVersion("simulation/1.0"); value.setCoreVersion("simulation-core/1.0.0+test");
        value.setSeed(41L); value.setMaxSteps(100); value.setObservationPolicy(json.createObjectNode().put("kind", "FULL"));
        value.setPolicyId("fixture-policy"); value.setPolicyVersion("1.0.0"); value.setPolicyDigest("1".repeat(64));
        value.setPersonaId("baseline-neutral"); value.setPersonaVersion("1.0.0"); value.setPersonaDigest("2".repeat(64));
        value.setMetricVersion("score-delta/1.0"); return value;
    }

    private MachineEpisode aggregateEpisode(int index, String status, String reason, Long duration, int actions) {
        return MachineEpisode.builder().episodeUuid(uuid(index)).executionStatus(status).terminationReason(reason)
                .wallDurationMs(duration).acceptedActionCount(actions).invalidActionCount(0).build();
    }

    private String uuid(int index) { return String.format("00000000-0000-4000-8000-%012d", index); }
    private String digest(String value) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch (Exception exception) { throw new IllegalStateException(exception); } }
    private String canonical(JsonNode node) {
        if (node.isArray()) { List<String> values = new ArrayList<>(); node.forEach(value -> values.add(canonical(value))); return "[" + String.join(",", values) + "]"; }
        if (node.isObject()) { List<String> names = new ArrayList<>(); node.fieldNames().forEachRemaining(names::add); names.sort(Comparator.naturalOrder()); return "{" + String.join(",", names.stream().map(name -> quote(name) + ":" + canonical(node.get(name))).toList()) + "}"; }
        return node.toString();
    }
    private String quote(String value) { try { return json.writeValueAsString(value); } catch (Exception exception) { throw new IllegalStateException(exception); } }

    private record Fixture(MachineEpisodeServiceImpl service, PrototypeVersionMapper versions, MachineEpisodeBatchMapper batches,
            MachineEpisodeMapper episodes, MachineEpisodeStepMapper steps, List<MachineEpisode> savedEpisodes,
            List<MachineEpisodeStep> savedSteps, MachineEpisodeBatch[] batchHolder) {
        MachineEpisodeBatch savedBatch() { return batchHolder[0]; }
    }
}
