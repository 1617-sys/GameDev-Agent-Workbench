package com.example.gameworkbench.service.impl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import com.example.gameworkbench.service.MachineEpisodeService;
import com.example.gameworkbench.vo.episode.MachineEpisodeAggregateVO;
import com.example.gameworkbench.vo.episode.MachineEpisodeBatchVO;
import com.example.gameworkbench.vo.episode.MachineEpisodeVO;
import com.example.gameworkbench.vo.episode.MachineEpisodeStepPageVO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MachineEpisodeServiceImpl implements MachineEpisodeService {
    private static final Pattern KEY = Pattern.compile("[A-Za-z0-9._:@/-]{8,128}");
    private static final Pattern DIGEST = Pattern.compile("[0-9a-f]{64}");
    private static final Set<String> TERMINATIONS = Set.of("WON", "HEALTH_DEPLETED", "TIME_EXPIRED", "MAX_STEPS", "ERROR");
    private final GameProjectMapper projects;
    private final PrototypeVersionMapper versions;
    private final MachineEpisodeBatchMapper batches;
    private final MachineEpisodeMapper episodes;
    private final MachineEpisodeStepMapper steps;
    private final ObjectMapper json;

    @Override
    @Transactional
    public MachineEpisodeBatchVO persistBatch(Long userId, String projectUuid, String idempotencyKey,
            PersistMachineEpisodeBatchRequest request) {
        requireKey(idempotencyKey);
        GameProject project = ownedProject(userId, projectUuid);
        String fingerprint = digest(canonical(json.valueToTree(request)));
        MachineEpisodeBatch replay = batches.selectIdempotent(userId, project.getId(), idempotencyKey);
        if (replay != null) {
            if (!Objects.equals(replay.getRequestFingerprint(), fingerprint)) {
                throw new BusinessException(ErrorCode.EPISODE_IDEMPOTENCY_CONFLICT);
            }
            return batchVO(replay, true);
        }

        validateBatch(request, project.getId());
        Counts counts = counts(request.getEpisodes());
        LocalDateTime now = LocalDateTime.now();
        MachineEpisodeBatch batch = MachineEpisodeBatch.builder()
                .batchUuid(UUID.randomUUID().toString()).userId(userId).projectId(project.getId())
                .clientBatchKey(request.getClientBatchKey()).idempotencyKey(idempotencyKey)
                .requestFingerprint(fingerprint).episodeProtocolVersion(request.getEpisodeProtocolVersion())
                .status(counts.status()).totalCount(request.getEpisodes().size())
                .completedCount(counts.completed()).failedCount(counts.failed())
                .rejectedCount(counts.rejected()).cancelledCount(counts.cancelled())
                .createdAt(now).completedAt(now).build();
        batches.insert(batch);

        for (PersistMachineEpisodeResultRequest input : request.getEpisodes()) {
            MachineEpisode episode = episode(input, batch.getId(), project.getId(), now);
            episodes.insert(episode);
            persistSteps(episode.getId(), input.getSteps());
        }
        return batchVO(batch, false);
    }

    @Override
    public MachineEpisodeBatchVO getBatch(Long userId, String projectUuid, String batchUuid) {
        GameProject project = ownedProject(userId, projectUuid);
        MachineEpisodeBatch batch = batches.selectByUuid(batchUuid);
        if (batch == null) throw new BusinessException(ErrorCode.EPISODE_NOT_FOUND);
        requireProject(project.getId(), batch.getProjectId());
        return batchVO(batch, false);
    }

    @Override
    public MachineEpisodeVO getEpisode(Long userId, String projectUuid, String episodeUuid) {
        GameProject project = ownedProject(userId, projectUuid);
        MachineEpisode episode = episodes.selectByUuid(episodeUuid);
        if (episode == null) throw new BusinessException(ErrorCode.EPISODE_NOT_FOUND);
        requireProject(project.getId(), episode.getProjectId());
        MachineEpisodeBatch batch = batches.selectById(episode.getBatchId());
        return episodeVO(episode, batch.getBatchUuid(), true);
    }

    @Override public MachineEpisodeVO getEpisodeSummary(Long userId,String projectUuid,String episodeUuid){GameProject project=ownedProject(userId,projectUuid);MachineEpisode episode=episodes.selectByUuid(episodeUuid);if(episode==null)throw new BusinessException(ErrorCode.EPISODE_NOT_FOUND);requireProject(project.getId(),episode.getProjectId());MachineEpisodeBatch batch=batches.selectById(episode.getBatchId());return episodeVO(episode,batch.getBatchUuid(),false);}

    @Override
    public MachineEpisodeAggregateVO aggregate(Long userId, String projectUuid, String prototypeVersionUuid) {
        GameProject project = ownedProject(userId, projectUuid);
        PrototypeVersion version = ownedVersion(project.getId(), prototypeVersionUuid);
        List<MachineEpisode> values = episodes.selectForAggregate(project.getId(), prototypeVersionUuid);
        int completed = (int) values.stream().filter(value -> "COMPLETED".equals(value.getExecutionStatus())).count();
        int failed = (int) values.stream().filter(value -> "FAILED".equals(value.getExecutionStatus())).count();
        long durationTotal = values.stream().filter(value -> value.getWallDurationMs() != null)
                .mapToLong(MachineEpisode::getWallDurationMs).sum();
        long durationSamples = values.stream().filter(value -> value.getWallDurationMs() != null).count();
        Map<String, Integer> reasons = new LinkedHashMap<>();
        for (String reason : TERMINATIONS) reasons.put(reason, 0);
        values.stream().map(MachineEpisode::getTerminationReason).filter(Objects::nonNull)
                .forEach(reason -> reasons.compute(reason, (key, count) -> count == null ? 1 : count + 1));
        double actions = values.isEmpty() ? 0d : values.stream()
                .mapToInt(value -> value.getAcceptedActionCount() + value.getInvalidActionCount()).average().orElse(0d);
        return MachineEpisodeAggregateVO.builder().sampleSource("MACHINE")
                .prototypeVersionUuid(prototypeVersionUuid).configDigest(version.getConfigDigest())
                .sampleSize(values.size()).completedCount(completed).failedCount(failed)
                .completionRate(values.isEmpty() ? 0d : completed / (double) values.size())
                .averageDurationMs(durationSamples == 0 ? 0 : durationTotal / durationSamples)
                .averageActionCount(actions).terminationReasons(Map.copyOf(reasons))
                .episodeResultRefs(values.stream().map(value -> "episodes/" + value.getEpisodeUuid() + "/result").toList())
                .build();
    }

    @Override public MachineEpisodeStepPageVO getSteps(Long userId,String projectUuid,String episodeUuid,int page,int size){
        GameProject project=ownedProject(userId,projectUuid);MachineEpisode episode=episodes.selectByUuid(episodeUuid);
        if(episode==null)throw new BusinessException(ErrorCode.EPISODE_NOT_FOUND);requireProject(project.getId(),episode.getProjectId());
        int safePage=Math.max(0,page),safeSize=Math.max(1,Math.min(100,size));
        List<JsonNode> items=steps.selectPage(episode.getId(),safePage*safeSize,safeSize).stream().map(value->read(value.getStepJson())).toList();
        return MachineEpisodeStepPageVO.builder().episodeId(episodeUuid).page(safePage).size(safeSize).total(steps.countEpisodeSteps(episode.getId())).items(items).build();
    }

    private void validateBatch(PersistMachineEpisodeBatchRequest request, Long projectId) {
        Set<String> clientKeys = new HashSet<>();
        Set<String> episodeIds = new HashSet<>();
        for (PersistMachineEpisodeResultRequest input : request.getEpisodes()) {
            try { UUID.fromString(input.getEpisodeId()); }
            catch (Exception exception) { invalid(); }
            if (!clientKeys.add(input.getClientEpisodeKey()) || !episodeIds.add(input.getEpisodeId())) invalid();
            PrototypeVersion version = ownedVersion(projectId, input.getPrototypeVersionUuid());
            if (!Objects.equals(version.getConfigDigest(), input.getConfigDigest())) {
                throw new BusinessException(ErrorCode.EPISODE_BINDING_MISMATCH);
            }
            validateResult(input);
        }
    }

    private void validateResult(PersistMachineEpisodeResultRequest input) {
        if (input.getStepCount() != input.getSteps().size()
                || input.getAcceptedActionCount() + input.getInvalidActionCount() != input.getStepCount()) invalid();
        if (input.getTerminationReason() != null && !TERMINATIONS.contains(input.getTerminationReason())) invalid();
        if ("COMPLETED".equals(input.getExecutionStatus())) {
            if (input.getTerminationReason() == null || !Objects.equals(expectedOutcome(input.getTerminationReason()), input.getOutcome())) invalid();
        }
        if (input.getTrajectoryDigest() != null
                && !Objects.equals(input.getTrajectoryDigest(), digest(canonical(json.valueToTree(input.getSteps()))))) invalid();
        for (int index = 0; index < input.getSteps().size(); index++) {
            JsonNode step = input.getSteps().get(index);
            if (step.path("sequence").asInt(-1) != index + 1 || step.path("attempt").asInt(-1) < 1
                    || step.path("simulationStepBefore").asInt(-1) < 0 || step.path("simulationStepAfter").asInt(-1) < 0
                    || !DIGEST.matcher(step.path("observationDigest").asText()).matches()
                    || step.path("decision").path("requestedAction").isMissingNode()
                    || !step.path("transition").isObject()) invalid();
        }
    }

    private MachineEpisode episode(PersistMachineEpisodeResultRequest input, Long batchId, Long projectId,
            LocalDateTime now) {
        return MachineEpisode.builder().episodeUuid(input.getEpisodeId()).batchId(batchId).projectId(projectId)
                .prototypeVersionUuid(input.getPrototypeVersionUuid()).clientEpisodeKey(input.getClientEpisodeKey())
                .sampleSource("MACHINE").configDigest(input.getConfigDigest())
                .simulationProtocolVersion(input.getSimulationProtocolVersion()).coreVersion(input.getCoreVersion())
                .seed(input.getSeed()).maxSteps(input.getMaxSteps()).observationPolicyJson(write(input.getObservationPolicy()))
                .policyId(input.getPolicyId()).policyVersion(input.getPolicyVersion()).policyDigest(input.getPolicyDigest())
                .personaId(input.getPersonaId()).personaVersion(input.getPersonaVersion()).personaDigest(input.getPersonaDigest())
                .modelJson(write(input.getModel())).usageJson(write(input.getUsage())).auditJson(write(input.getAudit())).timingJson(write(input.getTiming())).errorJson(write(input.getError()))
                .metricVersion(input.getMetricVersion()).executionStatus(input.getExecutionStatus())
                .terminationReason(input.getTerminationReason()).outcome(input.getOutcome()).stepCount(input.getStepCount())
                .acceptedActionCount(input.getAcceptedActionCount()).invalidActionCount(input.getInvalidActionCount())
                .finalStateHash(input.getFinalStateHash()).finalScore(input.getFinalScore())
                .trajectoryDigest(input.getTrajectoryDigest()).trajectoryRef(input.getTrajectoryRef())
                .wallDurationMs(input.getWallDurationMs()).createdAt(now).completedAt(now).build();
    }

    private void persistSteps(Long episodeId, List<JsonNode> inputSteps) {
        for (JsonNode step : inputSteps) {
            String requested = write(step.path("decision").path("requestedAction"));
            if (requested.length() > 1024) invalid();
            steps.insert(MachineEpisodeStep.builder().episodeId(episodeId)
                    .sequenceNumber(step.path("sequence").asInt()).attemptNumber(step.path("attempt").asInt())
                    .simulationStepBefore(step.path("simulationStepBefore").asInt())
                    .simulationStepAfter(step.path("simulationStepAfter").asInt())
                    .observationDigest(step.path("observationDigest").asText()).requestedActionJson(requested)
                    .transitionJson(write(step.path("transition"))).stepJson(write(step))
                    .rewardValueMicros(step.path("reward").path("valueMicros").asLong()).build());
        }
    }

    private MachineEpisodeBatchVO batchVO(MachineEpisodeBatch batch, boolean reused) {
        List<MachineEpisodeVO> items = episodes.selectByBatchId(batch.getId()).stream()
                .map(episode -> episodeVO(episode, batch.getBatchUuid(), false)).toList();
        return MachineEpisodeBatchVO.builder().batchId(batch.getBatchUuid()).clientBatchKey(batch.getClientBatchKey())
                .requestFingerprint(batch.getRequestFingerprint()).status(batch.getStatus()).total(batch.getTotalCount())
                .completed(batch.getCompletedCount()).failed(batch.getFailedCount()).rejected(batch.getRejectedCount())
                .cancelled(batch.getCancelledCount()).reused(reused).createdAt(batch.getCreatedAt())
                .completedAt(batch.getCompletedAt()).items(items).build();
    }

    private MachineEpisodeVO episodeVO(MachineEpisode episode, String batchUuid, boolean includeSteps) {
        List<JsonNode> raw = includeSteps ? steps.selectByEpisodeId(episode.getId()).stream()
                .map(value -> read(value.getStepJson())).toList() : List.of();
        return MachineEpisodeVO.builder().episodeId(episode.getEpisodeUuid()).batchId(batchUuid)
                .clientEpisodeKey(episode.getClientEpisodeKey()).sampleSource(episode.getSampleSource())
                .prototypeVersionUuid(episode.getPrototypeVersionUuid()).configDigest(episode.getConfigDigest())
                .seed(episode.getSeed()).maxSteps(episode.getMaxSteps()).policyId(episode.getPolicyId())
                .policyVersion(episode.getPolicyVersion()).personaId(episode.getPersonaId())
                .personaVersion(episode.getPersonaVersion()).metricVersion(episode.getMetricVersion())
                .model(readNullable(episode.getModelJson())).usage(readNullable(episode.getUsageJson())).audit(readNullable(episode.getAuditJson())).timing(readNullable(episode.getTimingJson())).error(readNullable(episode.getErrorJson()))
                .executionStatus(episode.getExecutionStatus()).terminationReason(episode.getTerminationReason())
                .outcome(episode.getOutcome()).stepCount(episode.getStepCount())
                .acceptedActionCount(episode.getAcceptedActionCount()).invalidActionCount(episode.getInvalidActionCount())
                .finalStateHash(episode.getFinalStateHash()).finalScore(episode.getFinalScore())
                .trajectoryDigest(episode.getTrajectoryDigest()).trajectoryRef(episode.getTrajectoryRef())
                .wallDurationMs(episode.getWallDurationMs()).completedAt(episode.getCompletedAt()).steps(raw).build();
    }

    private Counts counts(List<PersistMachineEpisodeResultRequest> values) {
        Map<String, Integer> counts = new HashMap<>();
        values.forEach(value -> counts.merge(value.getExecutionStatus(), 1, Integer::sum));
        return new Counts(counts.getOrDefault("COMPLETED", 0), counts.getOrDefault("FAILED", 0),
                counts.getOrDefault("REJECTED", 0), counts.getOrDefault("CANCELLED", 0), values.size());
    }

    private String expectedOutcome(String reason) {
        return switch (reason) {
            case "WON" -> "WON";
            case "HEALTH_DEPLETED", "TIME_EXPIRED" -> "LOST";
            case "MAX_STEPS" -> "TRUNCATED";
            case "ERROR" -> "ERROR";
            default -> throw new BusinessException(ErrorCode.EPISODE_INVALID);
        };
    }

    private GameProject ownedProject(Long userId, String projectUuid) {
        if (userId == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        GameProject project = projects.selectOne(new LambdaQueryWrapper<GameProject>()
                .eq(GameProject::getProjectUuid, projectUuid).eq(GameProject::getUserId, userId));
        if (project == null) throw new BusinessException(ErrorCode.PROJECT_NOT_FOUND);
        return project;
    }

    private PrototypeVersion ownedVersion(Long projectId, String versionUuid) {
        PrototypeVersion version = versions.selectByUuid(versionUuid);
        if (version == null) throw new BusinessException(ErrorCode.PROTOTYPE_VERSION_NOT_FOUND);
        if (!Objects.equals(projectId, version.getProjectId())) throw new BusinessException(ErrorCode.FORBIDDEN_PROTOTYPE_VERSION_ACCESS);
        return version;
    }

    private void requireProject(Long expected, Long actual) {
        if (!Objects.equals(expected, actual)) throw new BusinessException(ErrorCode.FORBIDDEN_EPISODE_ACCESS);
    }

    private void requireKey(String key) {
        if (key == null || !KEY.matcher(key).matches()) throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_INVALID);
    }

    private void invalid() { throw new BusinessException(ErrorCode.EPISODE_INVALID); }
    private String write(Object value) { try { return json.writeValueAsString(value); } catch (Exception exception) { throw new IllegalStateException(exception); } }
    private JsonNode read(String value) { try { return json.readTree(value); } catch (Exception exception) { throw new IllegalStateException(exception); } }
    private JsonNode readNullable(String value) { return value == null ? null : read(value); }
    private String digest(String value) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch (Exception exception) { throw new IllegalStateException(exception); } }
    private String canonical(JsonNode node) {
        if (node.isArray()) {
            List<String> values = new ArrayList<>(); node.forEach(value -> values.add(canonical(value)));
            return "[" + String.join(",", values) + "]";
        }
        if (node.isObject()) {
            List<String> names = new ArrayList<>(); node.fieldNames().forEachRemaining(names::add); names.sort(Comparator.naturalOrder());
            List<String> values = names.stream().map(name -> write(name) + ":" + canonical(node.get(name))).toList();
            return "{" + String.join(",", values) + "}";
        }
        return node.toString();
    }

    private record Counts(int completed, int failed, int rejected, int cancelled, int total) {
        String status() { return completed == total ? "SUCCEEDED" : completed > 0 ? "PARTIAL_SUCCESS" : "FAILED"; }
    }
}
