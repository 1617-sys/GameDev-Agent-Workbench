package com.example.gameworkbench.evaluation;
import com.example.gameworkbench.entity.AgentArtifact;
import com.example.gameworkbench.entity.EvaluationReport;
import com.example.gameworkbench.mapper.EvaluationReportMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
@Service @RequiredArgsConstructor
public class SchemaEvaluationService {
 private final SchemaEvaluator evaluator; private final EvaluationReportMapper mapper; private final ObjectMapper objectMapper;
 public void evaluateAndPersist(AgentArtifact artifact) {
  SchemaEvaluationResult result=evaluator.evaluate(artifact.getContent(),artifact.getSchemaKey(),artifact.getSchemaVersion());
  EvaluationReport report=new EvaluationReport(); report.setArtifactId(artifact.getId()); report.setEvaluatorType("SCHEMA"); report.setStatus(result.status()); report.setSchemaKey(result.schemaKey()); report.setSchemaVersion(result.schemaVersion()); report.setInputHash(sha256(artifact.getContent())); report.setEvaluationAttempt(1); report.setEvaluatedAt(LocalDateTime.now());
  try { report.setViolationsJson(objectMapper.writeValueAsString(result.violations().stream().map(code -> java.util.Map.of("code",code)).toList())); } catch(Exception e){ report.setViolationsJson("[]"); report.setStatus("ERROR"); }
  mapper.insert(report);
 }
 private String sha256(String input) { try { byte[] hash=MessageDigest.getInstance("SHA-256").digest((input==null?"":input).getBytes(StandardCharsets.UTF_8)); return java.util.HexFormat.of().formatHex(hash); } catch(Exception e){ throw new IllegalStateException(e); } }
}
