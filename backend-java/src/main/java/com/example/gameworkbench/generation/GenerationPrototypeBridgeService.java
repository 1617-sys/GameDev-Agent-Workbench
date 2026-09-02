package com.example.gameworkbench.generation;

import com.example.gameworkbench.common.enums.ErrorCode;
import com.example.gameworkbench.common.exception.BusinessException;
import com.example.gameworkbench.entity.GenerationRun;
import com.example.gameworkbench.service.PrototypeVersionService;
import com.example.gameworkbench.vo.prototype.PrototypeVersionVO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class GenerationPrototypeBridgeService {
    private static final String CONTRACT = "prototype-version/1";
    private static final Pattern IDEMPOTENCY = Pattern.compile("^[A-Za-z0-9._~-]{1,128}$");
    private final GenerationRunService generations;
    private final PrototypeVersionService prototypes;
    private final ObjectMapper json;

    public GenerationPrototypeBridgeResponse inspect(Long userId, String projectUuid, String runUuid) {
        return assess(userId, projectUuid, generations.get(userId, projectUuid, runUuid)).response();
    }

    public GenerationPrototypeBridgeResponse bridge(Long userId, String projectUuid, String runUuid, String requestKey) {
        if (requestKey == null || !IDEMPOTENCY.matcher(requestKey).matches()) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_INVALID);
        }
        GenerationRun run = generations.get(userId, projectUuid, runUuid);
        Assessment assessment = assess(userId, projectUuid, run);
        if (!assessment.response().compatible()) return assessment.response();
        try {
            PrototypeVersionVO version = prototypes.createFromArtifact(
                    userId, projectUuid, requestKey, assessment.artifactUuid());
            return new GenerationPrototypeBridgeResponse(true, version.getVersionUuid(),
                    version.isReused(), assessment.response().source(), List.of());
        } catch (BusinessException exception) {
            if (exception.getCode() != ErrorCode.PROTOTYPE_ARTIFACT_NOT_ELIGIBLE.getCode()) throw exception;
            return incompatible(assessment.response().source(), "GAME_CONFIG_ARTIFACT_INCOMPATIBLE",
                    "$.playerBridge.gameConfigArtifactUuid",
                    "The referenced artifact did not satisfy the existing PrototypeVersion/Player contract.",
                    "runtime-eligible GameConfig 2.0 artifact", String.valueOf(exception.getCode()));
        }
    }

    private Assessment assess(Long userId, String projectUuid, GenerationRun run) {
        var source = new GenerationPrototypeBridgeResponse.SourceSummary(
                run.getRunUuid(), run.getSourceDigest(), run.getRuntimeIrDigest(), run.getStatus());
        if (!("APPROVED".equals(run.getStatus()) || "RELEASED".equals(run.getStatus()))) {
            return new Assessment(incompatible(source, "GENERATION_RUN_NOT_APPROVED", "$.status",
                    "Player bridge requires an approved V5 run.", "APPROVED or RELEASED", run.getStatus()), null);
        }
        JsonNode runtime;
        try { runtime = json.readTree(run.getRuntimeIrJson()); }
        catch (Exception exception) {
            return new Assessment(incompatible(source, "RUNTIME_IR_INVALID", "$",
                    "Persisted V5 Runtime IR is not valid JSON.", "JSON object", "invalid JSON"), null);
        }
        JsonNode declaration = runtime == null ? null : runtime.get("playerBridge");
        if (declaration == null || !declaration.isObject()) {
            return new Assessment(incompatible(source, "PLAYER_BRIDGE_DECLARATION_MISSING", "$.playerBridge",
                    "V5 arcade_collect/1 does not declare a safe PrototypeVersion conversion.",
                    CONTRACT + " declaration", "missing"), null);
        }
        String version = declaration.path("contractVersion").asText("");
        if (!CONTRACT.equals(version)) {
            return new Assessment(incompatible(source, "PLAYER_BRIDGE_CONTRACT_UNSUPPORTED",
                    "$.playerBridge.contractVersion", "The declared Player bridge contract is not registered.",
                    CONTRACT, version), null);
        }
        String artifactUuid = declaration.path("gameConfigArtifactUuid").asText("").trim();
        if (artifactUuid.isEmpty()) {
            return new Assessment(incompatible(source, "GAME_CONFIG_ARTIFACT_MISSING",
                    "$.playerBridge.gameConfigArtifactUuid",
                    "A pre-validated V4 GameConfig artifact is required; the frontend may not synthesize one.",
                    "eligible artifact UUID", "missing"), null);
        }
        try {
            prototypes.validateSourceArtifact(userId, projectUuid, artifactUuid);
        } catch (BusinessException exception) {
            if (exception.getCode() != ErrorCode.PROTOTYPE_ARTIFACT_NOT_ELIGIBLE.getCode()) throw exception;
            return new Assessment(incompatible(source, "GAME_CONFIG_ARTIFACT_INCOMPATIBLE",
                    "$.playerBridge.gameConfigArtifactUuid",
                    "The referenced artifact did not satisfy the existing PrototypeVersion/Player contract.",
                    "runtime-eligible GameConfig 2.0 artifact", String.valueOf(exception.getCode())), artifactUuid);
        }
        return new Assessment(new GenerationPrototypeBridgeResponse(true, null, false, source, List.of()), artifactUuid);
    }

    private static GenerationPrototypeBridgeResponse incompatible(
            GenerationPrototypeBridgeResponse.SourceSummary source, String code, String path,
            String message, String expected, String actual) {
        return new GenerationPrototypeBridgeResponse(false, null, false, source,
                List.of(new GenerationPrototypeBridgeResponse.Incompatibility(code, path, message, expected, actual)));
    }

    private record Assessment(GenerationPrototypeBridgeResponse response, String artifactUuid) {}
}
