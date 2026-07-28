package com.example.gameworkbench.service.impl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.gameworkbench.common.enums.ArtifactType;
import com.example.gameworkbench.common.enums.ErrorCode;
import com.example.gameworkbench.common.exception.BusinessException;
import com.example.gameworkbench.dto.prototype.TunePrototypeVersionRequest;
import com.example.gameworkbench.entity.AgentArtifact;
import com.example.gameworkbench.entity.GameProject;
import com.example.gameworkbench.entity.PrototypeVersion;
import com.example.gameworkbench.evaluation.EvaluationOrchestrator;
import com.example.gameworkbench.evaluation.RuntimeCapabilityRegistry;
import com.example.gameworkbench.gameconfig.GameConfigContract;
import com.example.gameworkbench.gameconfig.GameConfigContractResult;
import com.example.gameworkbench.gameconfig.ResourceManifestContract;
import com.example.gameworkbench.gameconfig.ResourceManifestContractResult;
import com.example.gameworkbench.mapper.AgentArtifactMapper;
import com.example.gameworkbench.mapper.GameProjectMapper;
import com.example.gameworkbench.mapper.PrototypeVersionMapper;
import com.example.gameworkbench.service.PrototypeVersionService;
import com.example.gameworkbench.vo.prototype.PrototypeVersionComparisonVO;
import com.example.gameworkbench.vo.prototype.PrototypeVersionComparisonVO.ParameterDifference;
import com.example.gameworkbench.vo.prototype.PrototypeVersionVO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PrototypeVersionServiceImpl implements PrototypeVersionService {
    private static final String OPERATION = "CREATE_PROTOTYPE_VERSION";
    private static final Pattern IDEMPOTENCY_KEY = Pattern.compile("[A-Za-z0-9._:-]{1,128}");

    private final PrototypeVersionMapper versions;
    private final GameProjectMapper projects;
    private final AgentArtifactMapper artifacts;
    private final GameConfigContract gameConfigContract;
    private final ResourceManifestContract resourceManifestContract;
    private final EvaluationOrchestrator evaluationOrchestrator;
    private final ObjectMapper json;

    @Override
    @Transactional
    public PrototypeVersionVO createFromArtifact(Long userId, String projectUuid, String idempotencyKey,
            String artifactUuid) {
        requireKey(idempotencyKey);
        GameProject project = ownedProject(userId, projectUuid);
        AgentArtifact artifact = eligibleArtifact(project.getId(), artifactUuid);
        String fingerprint = fingerprint(Map.of("source", "AI_GENERATED", "artifactUuid", artifactUuid,
                "configDigest", artifact.getContentDigest()));
        return createLocked(userId, project.getId(), null, "AI_GENERATED", artifact, idempotencyKey, fingerprint);
    }

    @Override
    @Transactional
    public PrototypeVersionVO createFromWorkflow(Long userId, Long projectId, String workflowRunUuid,
            AgentArtifact artifact) {
        if (userId == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        GameProject project = projects.selectOne(new LambdaQueryWrapper<GameProject>()
                .eq(GameProject::getId, projectId).eq(GameProject::getUserId, userId));
        if (project == null) throw new BusinessException(ErrorCode.PROJECT_NOT_FOUND);
        AgentArtifact verified = eligibleArtifact(projectId, artifact.getArtifactUuid());
        String fingerprint = fingerprint(Map.of("source", "AI_GENERATED",
                "artifactUuid", verified.getArtifactUuid(), "configDigest", verified.getContentDigest()));
        return createLocked(userId, projectId, null, "AI_GENERATED", verified,
                "workflow:" + workflowRunUuid, fingerprint);
    }

    @Override
    @Transactional
    public PrototypeVersionVO tune(Long userId, String projectUuid, String parentVersionUuid, String idempotencyKey,
            TunePrototypeVersionRequest request) {
        requireKey(idempotencyKey);
        GameProject project = ownedProject(userId, projectUuid);
        PrototypeVersion parent = ownedVersion(project.getId(), parentVersionUuid);
        Map<String, Object> tuning = tuningFingerprint(request);
        String fingerprint = fingerprint(Map.of("source", "TUNED", "parentVersionUuid", parentVersionUuid,
                "tuning", tuning));

        versions.ensureSequence(project.getId());
        Integer next = versions.lockNextVersion(project.getId());
        PrototypeVersion replay = versions.selectIdempotent(userId, project.getId(), OPERATION, idempotencyKey);
        if (replay != null) return replay(replay, fingerprint);

        AgentArtifact parentArtifact = eligibleArtifact(project.getId(), parent.getGameConfigArtifactUuid());
        String canonical = tunedConfig(parentArtifact.getContent(), request);
        if (digest(canonical).equals(parentArtifact.getContentDigest())) {
            throw new BusinessException(ErrorCode.PROTOTYPE_TUNING_INVALID);
        }
        AgentArtifact tunedArtifact = persistAndEvaluateConfig(project.getId(), next, parentArtifact, canonical);
        persistAndEvaluateManifest(project.getId(), next, tunedArtifact);
        PrototypeVersion created = insertVersion(userId, project.getId(), next, parentVersionUuid, "TUNED",
                tunedArtifact, idempotencyKey, fingerprint);
        advance(project.getId(), next);
        return toVO(created, tunedArtifact, false, true);
    }

    @Override
    public List<PrototypeVersionVO> list(Long userId, String projectUuid) {
        GameProject project = ownedProject(userId, projectUuid);
        return versions.selectProjectVersions(project.getId()).stream()
                .map(version -> toVO(version, artifactForVersion(version), false, false))
                .toList();
    }

    @Override
    public PrototypeVersionVO get(Long userId, String projectUuid, String versionUuid) {
        GameProject project = ownedProject(userId, projectUuid);
        PrototypeVersion version = ownedVersion(project.getId(), versionUuid);
        return toVO(version, artifactForVersion(version), false, true);
    }

    @Override
    public PrototypeVersionComparisonVO compare(Long userId, String projectUuid, String leftVersionUuid,
            String rightVersionUuid) {
        PrototypeVersionVO left = get(userId, projectUuid, leftVersionUuid);
        PrototypeVersionVO right = get(userId, projectUuid, rightVersionUuid);
        List<ParameterDifference> differences = left.getParameters().keySet().stream().map(key ->
                ParameterDifference.builder().key(key).leftValue(left.getParameters().get(key))
                        .rightValue(right.getParameters().get(key))
                        .changed(!Objects.equals(left.getParameters().get(key), right.getParameters().get(key)))
                        .build()).toList();
        return PrototypeVersionComparisonVO.builder().left(left).right(right).differences(differences).build();
    }

    private PrototypeVersionVO createLocked(Long userId, Long projectId, String parentVersionUuid, String source,
            AgentArtifact artifact, String idempotencyKey, String fingerprint) {
        versions.ensureSequence(projectId);
        Integer next = versions.lockNextVersion(projectId);
        PrototypeVersion replay = versions.selectIdempotent(userId, projectId, OPERATION, idempotencyKey);
        if (replay != null) return replay(replay, fingerprint);
        PrototypeVersion byArtifact = versions.selectByArtifactUuid(artifact.getArtifactUuid());
        if (byArtifact != null) throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_CONFLICT);
        String effectiveParent = parentVersionUuid;
        if (effectiveParent == null && next > 1) {
            PrototypeVersion latest = versions.selectLatest(projectId);
            if (latest == null || !Objects.equals(latest.getVersionNumber(), next - 1)) {
                throw new IllegalStateException("Prototype version sequence is inconsistent");
            }
            effectiveParent = latest.getVersionUuid();
        }
        PrototypeVersion created = insertVersion(userId, projectId, next, effectiveParent, source, artifact,
                idempotencyKey, fingerprint);
        advance(projectId, next);
        return toVO(created, artifact, false, true);
    }

    private PrototypeVersion insertVersion(Long userId, Long projectId, Integer number, String parentVersionUuid,
            String source, AgentArtifact artifact, String idempotencyKey, String fingerprint) {
        PrototypeVersion version = PrototypeVersion.builder().versionUuid(UUID.randomUUID().toString())
                .projectId(projectId).versionNumber(number).parentVersionUuid(parentVersionUuid).source(source)
                .gameConfigArtifactUuid(artifact.getArtifactUuid()).configDigest(artifact.getContentDigest())
                .runtimeCapabilityVersion(artifact.getRuntimeCapabilityVersion()).createdBy(userId)
                .operation(OPERATION).idempotencyKey(idempotencyKey).requestFingerprint(fingerprint)
                .createdAt(LocalDateTime.now()).build();
        versions.insert(version);
        return version;
    }

    private PrototypeVersionVO replay(PrototypeVersion version, String fingerprint) {
        if (!Objects.equals(version.getRequestFingerprint(), fingerprint)) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_CONFLICT);
        }
        AgentArtifact artifact = artifactForVersion(version);
        return toVO(version, artifact, true, true);
    }

    private void advance(Long projectId, Integer expected) {
        if (expected == null || versions.advanceSequence(projectId, expected) != 1) {
            throw new IllegalStateException("Prototype version sequence lost ownership");
        }
    }

    private AgentArtifact persistAndEvaluateConfig(Long projectId, Integer versionNumber, AgentArtifact parent,
            String canonical) {
        AgentArtifact artifact = AgentArtifact.builder().artifactUuid(UUID.randomUUID().toString()).projectId(projectId)
                .artifactType(ArtifactType.GAME_CONFIG.name()).title("prototype-version-" + versionNumber + "-config")
                .content(canonical).contentDigest(digest(canonical)).schemaKey(GameConfigContract.SCHEMA_KEY)
                .schemaVersion(GameConfigContract.SCHEMA_VERSION).validationSummary("Tuned GameConfig 2.0 candidate")
                .sourceAttempt(1).sourceArtifactUuid(parent.getArtifactUuid())
                .runtimeCapabilityVersion(RuntimeCapabilityRegistry.VERSION).runtimeEligible(false)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();
        artifacts.insert(artifact);
        evaluationOrchestrator.evaluate(artifact);
        if (!Boolean.TRUE.equals(artifact.getRuntimeEligible())) {
            throw new BusinessException(ErrorCode.PROTOTYPE_TUNING_INVALID);
        }
        return artifact;
    }

    private void persistAndEvaluateManifest(Long projectId, Integer versionNumber, AgentArtifact source) {
        ResourceManifestContractResult result = resourceManifestContract.derive(source.getArtifactUuid(),
                source.getContentDigest(), source.getContent());
        if (!result.valid()) throw new BusinessException(ErrorCode.PROTOTYPE_TUNING_INVALID);
        AgentArtifact manifest = AgentArtifact.builder().artifactUuid(UUID.randomUUID().toString()).projectId(projectId)
                .artifactType(ArtifactType.RESOURCE_MANIFEST.name())
                .title("prototype-version-" + versionNumber + "-resources").content(result.canonicalContent())
                .contentDigest(digest(result.canonicalContent())).schemaKey(ResourceManifestContract.SCHEMA_KEY)
                .schemaVersion(ResourceManifestContract.SCHEMA_VERSION)
                .validationSummary("Tuned built-in resource manifest").sourceAttempt(1)
                .sourceArtifactUuid(source.getArtifactUuid()).runtimeCapabilityVersion(RuntimeCapabilityRegistry.VERSION)
                .runtimeEligible(false).createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();
        artifacts.insert(manifest);
        evaluationOrchestrator.evaluate(manifest);
        if (!Boolean.TRUE.equals(manifest.getRuntimeEligible())) {
            throw new BusinessException(ErrorCode.PROTOTYPE_TUNING_INVALID);
        }
    }

    private String tunedConfig(String content, TunePrototypeVersionRequest request) {
        GameConfigContractResult parent = gameConfigContract.process(content);
        if (!parent.valid() || parent.migrated()) throw new BusinessException(ErrorCode.PROTOTYPE_ARTIFACT_NOT_ELIGIBLE);
        ObjectNode root = parent.canonicalConfig().deepCopy();
        if (request.getTimeLimitSeconds() != null) ((ObjectNode) root.path("balance")).put("timeLimitSeconds", request.getTimeLimitSeconds());
        if (request.getPlayerSpeed() != null) ((ObjectNode) root.path("player")).put("speed", request.getPlayerSpeed());
        if (request.getPlayerMaxHealth() != null) ((ObjectNode) root.path("player")).put("maxHealth", request.getPlayerMaxHealth());
        if (request.getTargetCollectibles() != null) ((ObjectNode) root.path("objectives")).put("targetCollectibles", request.getTargetCollectibles());
        applyEnemySpeeds(root, request.getEnemySpeeds());
        if (request.getEnemyCount() != null) resizeEnemies(root, request.getEnemyCount());
        GameConfigContractResult result = gameConfigContract.process(write(root));
        if (!result.valid() || result.migrated()) throw new BusinessException(ErrorCode.PROTOTYPE_TUNING_INVALID);
        return gameConfigContract.canonicalJson(result.canonicalConfig());
    }

    private void resizeEnemies(ObjectNode root, int target) {
        ArrayNode enemies = (ArrayNode) root.path("entities").path("enemies");
        ArrayNode patrols = (ArrayNode) root.path("behaviors").path("enemyPatrols");
        while (enemies.size() > target) {
            String removed = enemies.remove(enemies.size() - 1).path("id").asText();
            for (int index = patrols.size() - 1; index >= 0; index--) {
                if (removed.equals(patrols.get(index).path("enemyId").asText())) patrols.remove(index);
            }
        }
        while (enemies.size() < target) addEnemy(root, enemies, patrols);
    }

    private void addEnemy(ObjectNode root, ArrayNode enemies, ArrayNode patrols) {
        String id = nextEnemyId(enemies);
        ObjectNode enemy = json.createObjectNode().put("id", id).put("x", 96).put("y", 96)
                .put("size", 28).put("speed", 90).put("spriteKey", "enemy.guard");
        ObjectNode patrol = json.createObjectNode().put("enemyId", id).put("axis", "x").put("distance", 32);
        enemies.add(enemy); patrols.add(patrol);
        int width = root.path("world").path("width").asInt();
        int height = root.path("world").path("height").asInt();
        for (int y = 96; y <= height - 96; y += 64) {
            for (int x = 96; x <= width - 96; x += 64) {
                enemy.put("x", x).put("y", y);
                if (candidateClear(root, enemy) && gameConfigContract.process(write(root)).valid()) return;
            }
        }
        enemies.remove(enemies.size() - 1); patrols.remove(patrols.size() - 1);
        throw new BusinessException(ErrorCode.PROTOTYPE_TUNING_INVALID);
    }

    private boolean candidateClear(ObjectNode root, ObjectNode candidate) {
        double radius = candidate.path("size").asDouble() / 2.0;
        JsonNode spawn = root.path("world").path("spawn");
        double playerRadius = root.path("player").path("size").asDouble() / 2.0;
        if (overlap(candidate, radius, spawn, playerRadius)) return false;
        for (JsonNode enemy : root.path("entities").path("enemies")) {
            if (enemy == candidate || Objects.equals(enemy.path("id").asText(), candidate.path("id").asText())) continue;
            if (overlap(candidate, radius, enemy, enemy.path("size").asDouble() / 2.0)) return false;
        }
        return true;
    }

    private boolean overlap(JsonNode left, double leftRadius, JsonNode right, double rightRadius) {
        double dx = left.path("x").asDouble() - right.path("x").asDouble();
        double dy = left.path("y").asDouble() - right.path("y").asDouble();
        double distance = leftRadius + rightRadius;
        return dx * dx + dy * dy < distance * distance;
    }

    private String nextEnemyId(ArrayNode enemies) {
        int serial = 1;
        while (true) {
            String candidate = "enemy-tuned-" + serial++;
            boolean exists = false;
            for (JsonNode enemy : enemies) if (candidate.equals(enemy.path("id").asText())) exists = true;
            if (!exists) return candidate;
        }
    }

    private void applyEnemySpeeds(ObjectNode root, Map<String, Integer> overrides) {
        if (overrides == null || overrides.isEmpty()) return;
        Map<String, ObjectNode> byId = new LinkedHashMap<>();
        root.path("entities").path("enemies").forEach(node -> byId.put(node.path("id").asText(), (ObjectNode) node));
        for (Map.Entry<String, Integer> entry : overrides.entrySet()) {
            ObjectNode enemy = byId.get(entry.getKey());
            if (enemy == null) throw new BusinessException(ErrorCode.PROTOTYPE_TUNING_INVALID);
            enemy.put("speed", entry.getValue());
        }
    }

    private AgentArtifact eligibleArtifact(Long projectId, String artifactUuid) {
        AgentArtifact artifact = artifacts.selectByArtifactUuid(artifactUuid);
        if (artifact == null) throw new BusinessException(ErrorCode.ARTIFACT_NOT_FOUND);
        if (!Objects.equals(projectId, artifact.getProjectId())) throw new BusinessException(ErrorCode.FORBIDDEN_ARTIFACT_ACCESS);
        boolean valid = ArtifactType.GAME_CONFIG.name().equals(artifact.getArtifactType())
                && GameConfigContract.SCHEMA_KEY.equals(artifact.getSchemaKey())
                && GameConfigContract.SCHEMA_VERSION.equals(artifact.getSchemaVersion())
                && Boolean.TRUE.equals(artifact.getRuntimeEligible())
                && RuntimeCapabilityRegistry.VERSION.equals(artifact.getRuntimeCapabilityVersion())
                && digest(artifact.getContent()).equals(artifact.getContentDigest());
        if (!valid) throw new BusinessException(ErrorCode.PROTOTYPE_ARTIFACT_NOT_ELIGIBLE);
        return artifact;
    }

    private AgentArtifact artifactForVersion(PrototypeVersion version) {
        AgentArtifact artifact = eligibleArtifact(version.getProjectId(), version.getGameConfigArtifactUuid());
        if (!Objects.equals(version.getConfigDigest(), artifact.getContentDigest())
                || !Objects.equals(version.getRuntimeCapabilityVersion(), artifact.getRuntimeCapabilityVersion())) {
            throw new BusinessException(ErrorCode.PROTOTYPE_ARTIFACT_NOT_ELIGIBLE);
        }
        return artifact;
    }

    private PrototypeVersion ownedVersion(Long projectId, String versionUuid) {
        PrototypeVersion version = versions.selectByUuid(versionUuid);
        if (version == null) throw new BusinessException(ErrorCode.PROTOTYPE_VERSION_NOT_FOUND);
        if (!Objects.equals(projectId, version.getProjectId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN_PROTOTYPE_VERSION_ACCESS);
        }
        return version;
    }

    private GameProject ownedProject(Long userId, String projectUuid) {
        if (userId == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        GameProject project = projects.selectOne(new LambdaQueryWrapper<GameProject>()
                .eq(GameProject::getProjectUuid, projectUuid).eq(GameProject::getUserId, userId));
        if (project == null) throw new BusinessException(ErrorCode.PROJECT_NOT_FOUND);
        return project;
    }

    private PrototypeVersionVO toVO(PrototypeVersion version, AgentArtifact artifact, boolean reused, boolean content) {
        return PrototypeVersionVO.builder().versionUuid(version.getVersionUuid())
                .lifecycleStatus(version.getLifecycleStatus() == null ? "APPROVED" : version.getLifecycleStatus())
                .directorRunUuid(version.getDirectorRunUuid()).approvalUpdatedAt(version.getApprovalUpdatedAt())
                .versionNumber(version.getVersionNumber()).parentVersionUuid(version.getParentVersionUuid())
                .source(version.getSource()).gameConfigArtifactUuid(version.getGameConfigArtifactUuid())
                .configDigest(version.getConfigDigest()).runtimeCapabilityVersion(version.getRuntimeCapabilityVersion())
                .createdAt(version.getCreatedAt()).parameters(parameters(artifact.getContent()))
                .gameConfig(content ? artifact.getContent() : null).reused(reused).build();
    }

    private Map<String, Object> parameters(String content) {
        try {
            JsonNode root = json.readTree(content);
            Map<String, Integer> speeds = new TreeMap<>();
            root.path("entities").path("enemies").forEach(enemy ->
                    speeds.put(enemy.path("id").asText(), enemy.path("speed").asInt()));
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("timeLimitSeconds", root.path("balance").path("timeLimitSeconds").asInt());
            values.put("playerSpeed", root.path("player").path("speed").asInt());
            values.put("playerMaxHealth", root.path("player").path("maxHealth").asInt());
            values.put("targetCollectibles", root.path("objectives").path("targetCollectibles").asInt());
            values.put("enemyCount", root.path("entities").path("enemies").size());
            values.put("enemySpeeds", speeds);
            return values;
        } catch (Exception exception) {
            throw new IllegalStateException("Stored prototype config is invalid", exception);
        }
    }

    private Map<String, Object> tuningFingerprint(TunePrototypeVersionRequest request) {
        Map<String, Object> values = new TreeMap<>();
        if (request.getTimeLimitSeconds() != null) values.put("timeLimitSeconds", request.getTimeLimitSeconds());
        if (request.getPlayerSpeed() != null) values.put("playerSpeed", request.getPlayerSpeed());
        if (request.getPlayerMaxHealth() != null) values.put("playerMaxHealth", request.getPlayerMaxHealth());
        if (request.getTargetCollectibles() != null) values.put("targetCollectibles", request.getTargetCollectibles());
        if (request.getEnemyCount() != null) values.put("enemyCount", request.getEnemyCount());
        if (request.getEnemySpeeds() != null && !request.getEnemySpeeds().isEmpty()) {
            values.put("enemySpeeds", new TreeMap<>(request.getEnemySpeeds()));
        }
        return values;
    }

    private void requireKey(String key) {
        if (key == null || !IDEMPOTENCY_KEY.matcher(key).matches()) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_INVALID);
        }
    }

    private String fingerprint(Object value) {
        try {
            JsonNode tree = json.valueToTree(value);
            if (!(tree instanceof ObjectNode object)) {
                throw new IllegalArgumentException("Prototype request fingerprint must be an object");
            }
            return digest(gameConfigContract.canonicalJson(object));
        }
        catch (Exception exception) { throw new IllegalStateException("Unable to fingerprint prototype request", exception); }
    }

    private String write(JsonNode value) {
        try { return json.writeValueAsString(value); }
        catch (Exception exception) { throw new IllegalStateException("Unable to serialize GameConfig", exception); }
    }

    private String digest(String value) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
