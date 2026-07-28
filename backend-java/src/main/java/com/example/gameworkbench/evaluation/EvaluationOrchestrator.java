package com.example.gameworkbench.evaluation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.gameworkbench.entity.AgentArtifact;
import com.example.gameworkbench.entity.EvaluationReport;
import com.example.gameworkbench.gameconfig.GameConfigContract;
import com.example.gameworkbench.gameconfig.ResourceManifestContract;
import com.example.gameworkbench.gameconfig.ResourceManifestContractResult;
import com.example.gameworkbench.mapper.AgentArtifactMapper;
import com.example.gameworkbench.mapper.EvaluationReportMapper;
import com.example.gameworkbench.observability.ApplicationObservability;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class EvaluationOrchestrator {
    private final SchemaEvaluator schema;
    private final GameConfigRuleEvaluator rule;
    private final EvaluationReportMapper reports;
    private final AgentArtifactMapper artifacts;
    private final ObjectMapper json;
    private final ApplicationObservability observability;
    private final ResourceManifestContract resourceManifest;

    public EvaluationOrchestrator(SchemaEvaluator schema, GameConfigRuleEvaluator rule,
            EvaluationReportMapper reports, AgentArtifactMapper artifacts, ObjectMapper json,
            ApplicationObservability observability) {
        this(schema, rule, reports, artifacts, json, observability,
                new ResourceManifestContract(json, new RuntimeCapabilityRegistry(), new GameConfigContract(json)));
    }

    @Autowired
    public EvaluationOrchestrator(SchemaEvaluator schema, GameConfigRuleEvaluator rule,
            EvaluationReportMapper reports, AgentArtifactMapper artifacts, ObjectMapper json,
            ApplicationObservability observability, ResourceManifestContract resourceManifest) {
        this.schema = schema;
        this.rule = rule;
        this.reports = reports;
        this.artifacts = artifacts;
        this.json = json;
        this.observability = observability;
        this.resourceManifest = resourceManifest;
    }

    public void evaluate(AgentArtifact artifact) {
        if (ResourceManifestContract.SCHEMA_KEY.equals(artifact.getSchemaKey())) {
            evaluateResourceManifest(artifact);
            return;
        }
        evaluateGameConfig(artifact);
    }

    private void evaluateGameConfig(AgentArtifact artifact) {
        SchemaEvaluationResult schemaResult = schema.evaluate(artifact.getContent(), artifact.getSchemaKey(), artifact.getSchemaVersion());
        EvaluationReport schemaReport = save(artifact, "SCHEMA", schemaResult.status(), null, schemaResult.violations());
        if (!schemaResult.passed()) {
            skipped(artifact, schemaReport, "SCHEMA_NOT_PASSED");
            return;
        }
        RuleEvaluationResult ruleResult = rule.evaluate(artifact.getContent());
        EvaluationReport ruleReport = save(artifact, "RULE", ruleResult.status(), ruleResult.ruleVersion(), ruleResult.violations());
        if (!ruleResult.blockingPassed()) {
            EvaluationReport runtime = save(artifact, "RUNTIME", "SKIPPED", ruleResult.ruleVersion(),
                    List.of(Map.of("code", "RULE_NOT_PASSED")));
            ineligible(artifact, runtime);
            return;
        }
        EvaluationReport runtime = save(artifact, "RUNTIME", "PASSED", ruleResult.ruleVersion(),
                List.of(Map.of("code", "RUNTIME_CAPABILITY_VERIFIED", "version", ruleResult.ruleVersion())));
        eligible(artifact, runtime, ruleResult.ruleVersion());
    }

    private void evaluateResourceManifest(AgentArtifact artifact) {
        ResourceManifestContractResult result = resourceManifest.validate(artifact.getContent());
        EvaluationReport schemaReport = save(artifact, "SCHEMA", result.valid() ? "PASSED" : "FAILED", null, result.violations());
        if (!result.valid()) {
            skipped(artifact, schemaReport, "RESOURCE_MANIFEST_SCHEMA_NOT_PASSED");
            return;
        }
        AgentArtifact source = artifacts.selectByArtifactUuid(artifact.getSourceArtifactUuid());
        String manifestSourceDigest = read(artifact.getContent(), "sourceConfigDigest");
        String manifestSourceUuid = read(artifact.getContent(), "sourceArtifactUuid");
        boolean sourceVerified = source != null
                && Boolean.TRUE.equals(source.getRuntimeEligible())
                && Objects.equals(artifact.getSourceArtifactUuid(), manifestSourceUuid)
                && Objects.equals(source.getContentDigest(), manifestSourceDigest)
                && Objects.equals(artifact.getProjectId(), source.getProjectId());
        if (!sourceVerified) {
            EvaluationReport ruleReport = save(artifact, "RULE", "FAILED", artifact.getRuntimeCapabilityVersion(),
                    List.of(Map.of("code", "RESOURCE_MANIFEST_SOURCE_MISMATCH")));
            EvaluationReport runtime = save(artifact, "RUNTIME", "SKIPPED", artifact.getRuntimeCapabilityVersion(),
                    List.of(Map.of("code", "RESOURCE_MANIFEST_SOURCE_MISMATCH")));
            ineligible(artifact, runtime.getId() == null ? ruleReport : runtime);
            return;
        }
        save(artifact, "RULE", "PASSED", artifact.getRuntimeCapabilityVersion(),
                List.of(Map.of("code", "BUILT_IN_KEYS_AND_SOURCE_VERIFIED")));
        EvaluationReport runtime = save(artifact, "RUNTIME", "PASSED", artifact.getRuntimeCapabilityVersion(),
                List.of(Map.of("code", "RESOURCE_MANIFEST_CAPABILITY_VERIFIED")));
        eligible(artifact, runtime, artifact.getRuntimeCapabilityVersion());
    }

    private String read(String content, String field) {
        try { return json.readTree(content).path(field).asText(); }
        catch (Exception exception) { return ""; }
    }

    private void skipped(AgentArtifact artifact, EvaluationReport last, String code) {
        save(artifact, "RULE", "SKIPPED", null, List.of(Map.of("code", code)));
        EvaluationReport runtime = save(artifact, "RUNTIME", "SKIPPED", null, List.of(Map.of("code", code)));
        ineligible(artifact, runtime.getId() == null ? last : runtime);
    }

    private EvaluationReport save(AgentArtifact artifact, String type, String status, String ruleVersion, Object violations) {
        EvaluationReport report = new EvaluationReport();
        report.setArtifactId(artifact.getId());
        report.setEvaluatorType(type);
        report.setStatus(status);
        report.setSchemaKey(artifact.getSchemaKey());
        report.setSchemaVersion(artifact.getSchemaVersion());
        report.setRuleVersion(ruleVersion);
        report.setInputHash(hash(artifact.getContent()));
        report.setEvaluationAttempt(1);
        report.setEvaluatedAt(LocalDateTime.now());
        try {
            report.setViolationsJson(json.writeValueAsString(violations));
        } catch (Exception exception) {
            report.setStatus("ERROR");
            report.setViolationsJson("[]");
        }
        reports.insert(report);
        observability.evaluationPersisted(type, report.getStatus());
        return report;
    }

    private void eligible(AgentArtifact artifact, EvaluationReport last, String capabilityVersion) {
        artifact.setRuntimeEligible(true);
        artifact.setRuntimeCapabilityVersion(capabilityVersion);
        artifact.setLastEvaluationReportId(last.getId());
        artifacts.updateById(artifact);
    }

    private void ineligible(AgentArtifact artifact, EvaluationReport last) {
        artifact.setRuntimeEligible(false);
        artifact.setLastEvaluationReportId(last.getId());
        artifacts.updateById(artifact);
    }

    private String hash(String value) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
