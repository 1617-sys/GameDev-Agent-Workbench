package com.example.gameworkbench.evaluation;
import com.example.gameworkbench.entity.AgentArtifact;
import com.example.gameworkbench.mapper.AgentArtifactMapper;
import com.example.gameworkbench.mapper.EvaluationReportMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import com.example.gameworkbench.entity.EvaluationReport;
class EvaluationOrchestratorTest {
 @Test void schemaFailurePersistsExplainableSkippedReportsAndKeepsArtifactIneligible(){ var reports=mock(EvaluationReportMapper.class);var artifacts=mock(AgentArtifactMapper.class);var orchestrator=new EvaluationOrchestrator(new SchemaEvaluator(new ObjectMapper()),new GameConfigRuleEvaluator(new ObjectMapper(),new RuntimeCapabilityRegistry()),reports,artifacts,new ObjectMapper(),mock(com.example.gameworkbench.observability.ApplicationObservability.class));var artifact=new AgentArtifact();artifact.setId(1L);artifact.setContent("{}");artifact.setSchemaKey("game-config");artifact.setSchemaVersion("1.0");orchestrator.evaluate(artifact);verify(reports,times(3)).insert(any(EvaluationReport.class));verify(artifacts).updateById(artifact); }
}
