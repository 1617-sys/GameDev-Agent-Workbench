package com.example.gameworkbench.evaluation;

import com.example.gameworkbench.entity.AgentArtifact;
import com.example.gameworkbench.entity.EvaluationReport;
import com.example.gameworkbench.mapper.AgentArtifactMapper;
import com.example.gameworkbench.mapper.EvaluationReportMapper;
import com.example.gameworkbench.observability.ApplicationObservability;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Map;

@Service @RequiredArgsConstructor
public class EvaluationOrchestrator {
 private final SchemaEvaluator schema; private final GameConfigRuleEvaluator rule; private final EvaluationReportMapper reports; private final AgentArtifactMapper artifacts; private final ObjectMapper json; private final ApplicationObservability observability;
 public void evaluate(AgentArtifact artifact) {
  SchemaEvaluationResult schemaResult=schema.evaluate(artifact.getContent(),artifact.getSchemaKey(),artifact.getSchemaVersion());
  EvaluationReport schemaReport=save(artifact,"SCHEMA",schemaResult.status(),null,schemaResult.violations());
  if(!schemaResult.passed()){ save(artifact,"RULE","SKIPPED",null,java.util.List.of(Map.of("code","SCHEMA_NOT_PASSED"))); save(artifact,"RUNTIME","SKIPPED",null,java.util.List.of(Map.of("code","SCHEMA_NOT_PASSED"))); ineligible(artifact,schemaReport); return; }
  RuleEvaluationResult ruleResult=rule.evaluate(artifact.getContent()); EvaluationReport ruleReport=save(artifact,"RULE",ruleResult.status(),ruleResult.ruleVersion(),ruleResult.violations());
  if(!ruleResult.blockingPassed()){ save(artifact,"RUNTIME","SKIPPED",ruleResult.ruleVersion(),java.util.List.of(Map.of("code","RULE_NOT_PASSED"))); ineligible(artifact,ruleReport); return; }
  EvaluationReport runtimeReport=save(artifact,"RUNTIME","SKIPPED",ruleResult.ruleVersion(),java.util.List.of(Map.of("code","RUNTIME_SMOKE_NOT_RECORDED"))); ineligible(artifact,runtimeReport);
 }
 private EvaluationReport save(AgentArtifact a,String type,String status,String ruleVersion,Object violations){ EvaluationReport r=new EvaluationReport(); r.setArtifactId(a.getId()); r.setEvaluatorType(type); r.setStatus(status); r.setSchemaKey(a.getSchemaKey()); r.setSchemaVersion(a.getSchemaVersion()); r.setRuleVersion(ruleVersion); r.setInputHash(hash(a.getContent())); r.setEvaluationAttempt(1); r.setEvaluatedAt(LocalDateTime.now()); try{r.setViolationsJson(json.writeValueAsString(violations));}catch(Exception e){r.setStatus("ERROR");r.setViolationsJson("[]");} reports.insert(r); observability.evaluationPersisted(type,r.getStatus()); return r; }
 private void ineligible(AgentArtifact a,EvaluationReport last){ a.setRuntimeEligible(false); a.setLastEvaluationReportId(last.getId()); artifacts.updateById(a); }
 private String hash(String v){try{return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((v==null?"":v).getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
}
