package com.example.gameworkbench.generation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
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
import com.example.gameworkbench.gamespec.GameSpecCompilationResult;
import com.example.gameworkbench.gamespec.GameSpecCompiler;
import com.example.gameworkbench.mapper.GameProjectMapper;
import com.example.gameworkbench.mapper.GenerationRunMapper;
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
 * <p>状态迁移使用 stateVersion 乐观并发控制。当前构建仍在事务方法中同步执行，且没有在
 * 启动 Cocos 前抢占独立 BUILD_RUNNING 状态，这是需要优先整改的长事务和重复构建风险。</p>
 */
@Service
@RequiredArgsConstructor
public class GenerationRunService {
    private static final Pattern IDEMPOTENCY = Pattern.compile("^[A-Za-z0-9._~-]{1,128}$");
    private final GenerationRunMapper runs;
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
                .status(succeeded ? GenerationRunStatus.BUILDING.name() : GenerationRunStatus.FAILED.name())
                .stateVersion(0L)
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
        if (run.getPackageDigest() == null) throw new BusinessException(ErrorCode.EXPORT_NOT_READY);
        return artifactStore.get(runUuid, run.getPackageDigest());
    }

    @Transactional
    public GenerationBuildOutcome build(Long userId, String projectUuid, String runUuid, long expectedVersion) {
        GenerationRun run = get(userId, projectUuid, runUuid);
        if (!GenerationRunStatus.BUILDING.name().equals(run.getStatus()) || run.getStateVersion() != expectedVersion) {
            throw new BusinessException(ErrorCode.GAMESPEC_INVALID);
        }
        // TODO(concurrency): 应先在短事务中乐观抢占构建权，再在事务外执行最长十分钟的
        // Cocos 进程，最后用短事务写入结果。当前两个并发请求可能重复启动外部构建。
        try {
            CocosBuildResult result = buildWorker.build(readObject(run.getBuildRequestJson()), readObject(run.getRuntimeIrJson()));
            if (result.status() == CocosBuildResult.Status.SUCCEEDED) {
                PlayableArtifact artifact = artifactAssembler.assemble(run, result.outputDirectory(), result.logDigest());
                artifactStore.put(run.getRunUuid(), artifact);
                transition(run, GenerationRunStatus.PLAYTESTING, artifact.packageDigest(), null);
                return new GenerationBuildOutcome(runUuid, GenerationRunStatus.PLAYTESTING.name(), result.exitCode(),
                        result.logDigest(), result.outputDigest(), artifact.packageDigest());
            } else {
                transition(run, GenerationRunStatus.FAILED, null, "COCOS_BUILD_FAILED");
                return new GenerationBuildOutcome(runUuid, GenerationRunStatus.FAILED.name(), result.exitCode(),
                        result.logDigest(), null, null);
            }
        } catch (BusinessException exception) {
            // Missing local Cocos is recoverable configuration; keep the run ready to build.
            throw exception;
        }
    }

    private void transition(GenerationRun run, GenerationRunStatus target, String packageDigest, String errorCode) {
        int changed = runs.transition(run.getId(), run.getProjectId(), run.getStateVersion(), run.getStatus(),
                target.name(), packageDigest, errorCode, target.terminal());
        if (changed != 1) throw new BusinessException(ErrorCode.DIRECTOR_RUN_CONCURRENT_UPDATE);
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
