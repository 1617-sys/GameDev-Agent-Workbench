package com.example.gameworkbench.generation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.gameworkbench.cocos.CocosBuildResult;
import com.example.gameworkbench.cocos.CocosBuildWorker;
import com.example.gameworkbench.artifact.PlayableArtifact;
import com.example.gameworkbench.artifact.PlayableArtifactAssembler;
import com.example.gameworkbench.artifact.PlayableArtifactStore;
import com.example.gameworkbench.common.enums.ErrorCode;
import com.example.gameworkbench.common.exception.BusinessException;
import com.example.gameworkbench.entity.GameProject;
import com.example.gameworkbench.entity.GenerationRun;
import com.example.gameworkbench.entity.GenerationRunApproval;
import com.example.gameworkbench.dto.gamespec.GenerationApprovalRequest;
import com.example.gameworkbench.gamespec.GameSpecCompilationResult;
import com.example.gameworkbench.gamespec.GameSpecCompiler;
import com.example.gameworkbench.mapper.GameProjectMapper;
import com.example.gameworkbench.mapper.GenerationRunMapper;
import com.example.gameworkbench.mapper.GenerationRunApprovalMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;

/**
 * V5 生成运行的控制面服务。
 *
 * <p>创建阶段会再次编译 GameSpec，并冻结 canonical spec、Runtime IR、Build Request 及摘要；
 * 构建阶段只能使用这些持久化输入，不能接受客户端临时注入命令或构建参数。</p>
 *
 * <p>状态迁移使用 stateVersion 乐观并发控制。构建先以短数据库更新领取有期限的 claim，
 * 再在事务外执行 Cocos，最后由持有同一 token 的执行者提交结果；过期 claim 可以接管。</p>
 */
@Service
@RequiredArgsConstructor
public class GenerationRunService {
    private static final Pattern IDEMPOTENCY = Pattern.compile("^[A-Za-z0-9._~-]{1,128}$");
    private static final Duration BUILD_LEASE = Duration.ofMinutes(12);
    private final GenerationRunMapper runs;
    private final GenerationRunApprovalMapper approvals;
    private final GameProjectMapper projects;
    private final GameSpecCompiler compiler;
    private final CocosBuildWorker buildWorker;
    private final PlayableArtifactAssembler artifactAssembler;
    private final PlayableArtifactStore artifactStore;
    private final ObjectMapper json;

    @Transactional
    public GenerationRun create(Long userId, String projectUuid, String idempotencyKey, JsonNode spec) {
        GameProject project = ownedProject(userId, projectUuid);
        if (idempotencyKey == null || !IDEMPOTENCY.matcher(idempotencyKey).matches()) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_INVALID);
        }
        // WHY: 对象字段排序后再计算指纹，使字段顺序不同但语义相同的请求能够安全重放。
        String fingerprint = digest(write(sort(spec)));
        GenerationRun existing = runs.selectByIdempotency(userId, project.getId(), idempotencyKey);
        if (existing != null) {
            if (!existing.getRequestFingerprint().equals(fingerprint)) {
                throw new BusinessException(ErrorCode.GENERATION_RUN_IDEMPOTENCY_CONFLICT);
            }
            return existing;
        }

        GameSpecCompilationResult result = compiler.compile(spec);
        LocalDateTime now = LocalDateTime.now();
        boolean succeeded = result.status() == GameSpecCompilationResult.Status.SUCCEEDED;
        GenerationRun run = GenerationRun.builder()
                .runUuid(UUID.randomUUID().toString()).userId(userId).projectId(project.getId())
                .idempotencyKey(idempotencyKey).requestFingerprint(fingerprint)
                .status(succeeded ? GenerationRunStatus.READY_TO_BUILD.name() : GenerationRunStatus.FAILED.name())
                .stateVersion(0L)
                .buildAttempt(0)
                .canonicalSpecJson(succeeded ? write(result.canonicalSpec()) : null)
                .sourceDigest(result.sourceDigest())
                .runtimeIrJson(succeeded ? write(result.runtimeIr()) : null)
                .runtimeIrDigest(result.runtimeIrDigest())
                .buildRequestJson(succeeded ? write(result.buildRequest()) : null)
                .diagnosticsJson(write(json.valueToTree(result.diagnostics())))
                .errorCode(succeeded ? null : "GAMESPEC_VALIDATION_FAILED")
                .createdAt(now).updatedAt(now).completedAt(succeeded ? null : now)
                .build();
        runs.insert(run);
        return run;
    }

    @Transactional(readOnly = true)
    public GenerationRun get(Long userId, String projectUuid, String runUuid) {
        GameProject project = ownedProject(userId, projectUuid);
        GenerationRun run = runs.selectByUuid(runUuid);
        if (run == null) throw new BusinessException(ErrorCode.GENERATION_RUN_NOT_FOUND);
        if (!Objects.equals(run.getProjectId(), project.getId()) || !Objects.equals(run.getUserId(), userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN_PROJECT_ACCESS);
        }
        return run;
    }

    @Transactional(readOnly = true)
    public byte[] artifact(Long userId, String projectUuid, String runUuid) {
        GenerationRun run = get(userId, projectUuid, runUuid);
        if (!GenerationRunStatus.RELEASED.name().equals(run.getStatus()) || run.getPackageDigest() == null) {
            throw new BusinessException(ErrorCode.GENERATION_RELEASE_FORBIDDEN);
        }
        return artifactStore.get(runUuid, run.getPackageDigest());
    }

    @Transactional(readOnly = true)
    public byte[] previewArtifact(Long userId, String projectUuid, String runUuid) {
        GenerationRun run = get(userId, projectUuid, runUuid);
        if (run.getPackageDigest() == null || !(GenerationRunStatus.AWAITING_APPROVAL.name().equals(run.getStatus())
                || GenerationRunStatus.APPROVED.name().equals(run.getStatus())
                || GenerationRunStatus.RELEASED.name().equals(run.getStatus()))) {
            throw new BusinessException(ErrorCode.EXPORT_NOT_READY);
        }
        return artifactStore.get(runUuid, run.getPackageDigest());
    }

    public GenerationBuildOutcome build(Long userId, String projectUuid, String runUuid, long expectedVersion) {
        GenerationRun run = get(userId, projectUuid, runUuid);
        boolean ready = GenerationRunStatus.READY_TO_BUILD.name().equals(run.getStatus());
        boolean expired = GenerationRunStatus.BUILDING.name().equals(run.getStatus())
                && run.getBuildClaimExpiresAt() != null && run.getBuildClaimExpiresAt().isBefore(LocalDateTime.now());
        if ((!ready && !expired) || run.getStateVersion() != expectedVersion) {
            throw new BusinessException(ErrorCode.GENERATION_RUN_CONCURRENT_UPDATE);
        }
        String claimToken = UUID.randomUUID().toString();
        if (runs.claimBuild(run.getId(), run.getProjectId(), expectedVersion, claimToken,
                LocalDateTime.now().plus(BUILD_LEASE)) != 1) {
            throw new BusinessException(ErrorCode.GENERATION_RUN_CONCURRENT_UPDATE);
        }
        long claimedVersion = expectedVersion + 1;
        // The conditional UPDATE changes the database row, not the already-loaded Java object.
        // Re-read and verify the durable claim before any external work or artifact assembly.
        GenerationRun claimedRun = runs.selectByUuid(runUuid);
        if (claimedRun == null
                || !Objects.equals(claimedRun.getId(), run.getId())
                || !Objects.equals(claimedRun.getProjectId(), run.getProjectId())
                || !GenerationRunStatus.BUILDING.name().equals(claimedRun.getStatus())
                || !Objects.equals(claimedRun.getStateVersion(), claimedVersion)
                || !Objects.equals(claimedRun.getBuildClaimToken(), claimToken)) {
            throw new BusinessException(ErrorCode.GENERATION_RUN_CONCURRENT_UPDATE);
        }
        try {
            CocosBuildResult result = buildWorker.build(
                    readObject(claimedRun.getBuildRequestJson()), readObject(claimedRun.getRuntimeIrJson()));
            if (result.status() == CocosBuildResult.Status.SUCCEEDED) {
                PlayableArtifact artifact = artifactAssembler.assemble(
                        claimedRun, result.outputDirectory(), result.logDigest());
                artifactStore.put(claimedRun.getRunUuid(), artifact);
                completeBuild(claimedRun, claimedVersion, claimToken, GenerationRunStatus.AWAITING_APPROVAL,
                        artifact.packageDigest(), null);
                return new GenerationBuildOutcome(runUuid, GenerationRunStatus.AWAITING_APPROVAL.name(), result.exitCode(),
                        result.logDigest(), result.outputDigest(), artifact.packageDigest());
            } else {
                completeBuild(claimedRun, claimedVersion, claimToken, GenerationRunStatus.FAILED, null,
                        "COCOS_BUILD_FAILED");
                return new GenerationBuildOutcome(runUuid, GenerationRunStatus.FAILED.name(), result.exitCode(),
                        result.logDigest(), null, null);
            }
        } catch (BusinessException exception) {
            if (exception.getCode().equals(ErrorCode.COCOS_BUILD_UNAVAILABLE.getCode())) {
                releaseBuild(claimedRun, claimedVersion, claimToken);
            } else {
                completeBuild(claimedRun, claimedVersion, claimToken, GenerationRunStatus.FAILED, null,
                        "BUILD_SECURITY_REJECTED");
            }
            throw exception;
        } catch (RuntimeException exception) {
            completeBuild(claimedRun, claimedVersion, claimToken, GenerationRunStatus.FAILED, null,
                    "COCOS_BUILD_FAILED");
            throw exception;
        }
    }

    @Transactional
    public GenerationApprovalOutcome approve(Long userId, String projectUuid, String runUuid,
            String idempotencyKey, GenerationApprovalRequest request) {
        if (idempotencyKey == null || !IDEMPOTENCY.matcher(idempotencyKey).matches()) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_INVALID);
        }
        GenerationRun run = get(userId, projectUuid, runUuid);
        String fingerprint = digest(runUuid + "\n" + request.decision() + "\n" + request.reason());
        GenerationRunApproval replay = approvals.selectIdempotent(userId, run.getProjectId(), idempotencyKey);
        if (replay != null) {
            if (!Objects.equals(replay.getRequestFingerprint(), fingerprint)) approvalConflict();
            return approvalOutcome(replay, true);
        }
        GenerationRunApproval existing = approvals.selectByRunId(run.getId());
        if (existing != null) {
            if (Objects.equals(existing.getRequestFingerprint(), fingerprint)) return approvalOutcome(existing, true);
            approvalConflict();
        }
        if (!GenerationRunStatus.AWAITING_APPROVAL.name().equals(run.getStatus())) approvalConflict();
        LocalDateTime now = LocalDateTime.now();
        GenerationRunApproval approval = GenerationRunApproval.builder()
                .approvalUuid(UUID.randomUUID().toString()).generationRunId(run.getId())
                .generationRunUuid(runUuid).userId(userId).projectId(run.getProjectId()).actorUserId(userId)
                .decision(request.decision()).reason(request.reason()).idempotencyKey(idempotencyKey)
                .requestFingerprint(fingerprint).createdAt(now).build();
        approvals.insert(approval);
        GenerationRunStatus target = GenerationRunStatus.valueOf(request.decision());
        if (runs.transitionStatus(run.getId(), run.getProjectId(), run.getStateVersion(), run.getStatus(),
                target.name(), target.terminal()) != 1) {
            throw new BusinessException(ErrorCode.GENERATION_RUN_CONCURRENT_UPDATE);
        }
        return approvalOutcome(approval, false);
    }

    @Transactional
    public GenerationRun release(Long userId, String projectUuid, String runUuid, long expectedVersion) {
        GenerationRun run = get(userId, projectUuid, runUuid);
        if (!GenerationRunStatus.APPROVED.name().equals(run.getStatus()) || run.getStateVersion() != expectedVersion) {
            throw new BusinessException(ErrorCode.GENERATION_RELEASE_FORBIDDEN);
        }
        if (runs.transitionStatus(run.getId(), run.getProjectId(), expectedVersion,
                GenerationRunStatus.APPROVED.name(), GenerationRunStatus.RELEASED.name(), true) != 1) {
            throw new BusinessException(ErrorCode.GENERATION_RUN_CONCURRENT_UPDATE);
        }
        return get(userId, projectUuid, runUuid);
    }

    private void completeBuild(GenerationRun run, long claimedVersion, String claimToken,
            GenerationRunStatus target, String packageDigest, String errorCode) {
        if (runs.completeBuild(run.getId(), run.getProjectId(), claimedVersion, claimToken,
                target.name(), packageDigest, errorCode, target.terminal()) != 1) {
            throw new BusinessException(ErrorCode.GENERATION_RUN_CONCURRENT_UPDATE);
        }
    }

    private void releaseBuild(GenerationRun run, long claimedVersion, String claimToken) {
        if (runs.releaseBuild(run.getId(), run.getProjectId(), claimedVersion, claimToken) != 1) {
            throw new BusinessException(ErrorCode.GENERATION_RUN_CONCURRENT_UPDATE);
        }
    }

    private GenerationApprovalOutcome approvalOutcome(GenerationRunApproval approval, boolean reused) {
        return new GenerationApprovalOutcome(approval.getApprovalUuid(), approval.getGenerationRunUuid(),
                approval.getDecision(), approval.getReason(), approval.getActorUserId(), approval.getCreatedAt(), reused);
    }

    private void approvalConflict() {
        throw new BusinessException(ErrorCode.GENERATION_APPROVAL_CONFLICT);
    }

    private GameProject ownedProject(Long userId, String projectUuid) {
        if (userId == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        GameProject project = projects.selectOne(new LambdaQueryWrapper<GameProject>()
                .eq(GameProject::getProjectUuid, projectUuid));
        if (project == null) throw new BusinessException(ErrorCode.PROJECT_NOT_FOUND);
        if (!Objects.equals(project.getUserId(), userId)) throw new BusinessException(ErrorCode.FORBIDDEN_PROJECT_ACCESS);
        return project;
    }

    private ObjectNode readObject(String value) {
        try { return (ObjectNode) json.readTree(value); }
        catch (Exception exception) { throw new IllegalStateException("Persisted generation JSON is invalid", exception); }
    }

    private JsonNode sort(JsonNode input) {
        if (input == null) return json.nullNode();
        if (input.isObject()) {
            ObjectNode output = json.createObjectNode();
            List<String> names = new ArrayList<>();
            input.fieldNames().forEachRemaining(names::add);
            names.stream().sorted().forEach(name -> output.set(name, sort(input.get(name))));
            return output;
        }
        if (input.isArray()) {
            ArrayNode output = json.createArrayNode();
            input.forEach(value -> output.add(sort(value)));
            return output;
        }
        return input.deepCopy();
    }

    private String write(JsonNode value) {
        try { return json.writeValueAsString(value); }
        catch (Exception exception) { throw new IllegalStateException(exception); }
    }

    private String digest(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception exception) { throw new IllegalStateException(exception); }
    }
}
