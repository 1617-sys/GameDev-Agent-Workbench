package com.example.gameworkbench.evaluation;
import static org.assertj.core.api.Assertions.assertThat;
import com.example.gameworkbench.entity.AgentArtifact;
import com.example.gameworkbench.mapper.AgentArtifactMapper;
import com.example.gameworkbench.mapper.EvaluationReportMapper;
import com.example.gameworkbench.gameconfig.GameConfigContract;
import com.example.gameworkbench.gameconfig.ResourceManifestContract;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.mockito.Mockito.*;
import com.example.gameworkbench.entity.EvaluationReport;
class EvaluationOrchestratorTest {
 @Test void schemaFailurePersistsExplainableSkippedReportsAndKeepsArtifactIneligible(){ var reports=mock(EvaluationReportMapper.class);var artifacts=mock(AgentArtifactMapper.class);var orchestrator=new EvaluationOrchestrator(new SchemaEvaluator(new ObjectMapper()),new GameConfigRuleEvaluator(new ObjectMapper(),new RuntimeCapabilityRegistry()),reports,artifacts,new ObjectMapper(),mock(com.example.gameworkbench.observability.ApplicationObservability.class));var artifact=new AgentArtifact();artifact.setId(1L);artifact.setContent("{}");artifact.setSchemaKey("game-config");artifact.setSchemaVersion("1.0");orchestrator.evaluate(artifact);verify(reports,times(3)).insert(any(EvaluationReport.class));verify(artifacts).updateById(artifact); }

 @Test void validConfigPersistsThreePassedReportsAndBecomesRuntimeEligible() throws Exception {
   var reports=mock(EvaluationReportMapper.class);var artifacts=mock(AgentArtifactMapper.class);var json=new ObjectMapper();
   doAnswer(invocation->{ invocation.<EvaluationReport>getArgument(0).setId(20L); return 1; }).when(reports).insert(any(EvaluationReport.class));
   var orchestrator=new EvaluationOrchestrator(new SchemaEvaluator(json),new GameConfigRuleEvaluator(json,new RuntimeCapabilityRegistry()),reports,artifacts,json,mock(com.example.gameworkbench.observability.ApplicationObservability.class));
   var artifact=new AgentArtifact();artifact.setId(1L);artifact.setContent(Files.readString(Path.of("..","docs","requirements","v3","examples","game-config-2.0","valid-minimal.json")));artifact.setSchemaKey("game-config");artifact.setSchemaVersion("2.0");
   orchestrator.evaluate(artifact);
   ArgumentCaptor<EvaluationReport> captor=ArgumentCaptor.forClass(EvaluationReport.class);verify(reports,times(3)).insert(captor.capture());
   List<EvaluationReport> values=captor.getAllValues();
   assertThat(values).extracting(EvaluationReport::getEvaluatorType).containsExactly("SCHEMA","RULE","RUNTIME");
   assertThat(values).extracting(EvaluationReport::getStatus).containsOnly("PASSED");
   assertThat(artifact.getRuntimeEligible()).isTrue();assertThat(artifact.getRuntimeCapabilityVersion()).isEqualTo(RuntimeCapabilityRegistry.VERSION);assertThat(artifact.getLastEvaluationReportId()).isEqualTo(20L);
 }

 @Test void resourceManifestMustResolveToTheEligibleSourceDigest() throws Exception {
   var reports=mock(EvaluationReportMapper.class);var artifacts=mock(AgentArtifactMapper.class);var json=new ObjectMapper();
   doAnswer(invocation->{ invocation.<EvaluationReport>getArgument(0).setId(30L); return 1; }).when(reports).insert(any(EvaluationReport.class));
   var contract=new ResourceManifestContract(json,new RuntimeCapabilityRegistry(),new GameConfigContract(json));
   String sourceUuid="11111111-1111-1111-1111-111111111111", digest="a".repeat(64);
   String config=Files.readString(Path.of("..","docs","requirements","v3","examples","game-config-2.0","valid-minimal.json"));
   var source=new AgentArtifact();source.setArtifactUuid(sourceUuid);source.setProjectId(7L);source.setContentDigest(digest);source.setRuntimeEligible(true);
   when(artifacts.selectByArtifactUuid(sourceUuid)).thenReturn(source);
   var manifest=new AgentArtifact();manifest.setId(2L);manifest.setProjectId(7L);manifest.setSourceArtifactUuid(sourceUuid);manifest.setRuntimeCapabilityVersion(RuntimeCapabilityRegistry.VERSION);manifest.setSchemaKey(ResourceManifestContract.SCHEMA_KEY);manifest.setSchemaVersion(ResourceManifestContract.SCHEMA_VERSION);manifest.setContent(contract.derive(sourceUuid,digest,config).canonicalContent());
   var orchestrator=new EvaluationOrchestrator(new SchemaEvaluator(json),new GameConfigRuleEvaluator(json,new RuntimeCapabilityRegistry()),reports,artifacts,json,mock(com.example.gameworkbench.observability.ApplicationObservability.class),contract);
   orchestrator.evaluate(manifest);
   assertThat(manifest.getRuntimeEligible()).isTrue();
   source.setContentDigest("b".repeat(64));manifest.setRuntimeEligible(false);orchestrator.evaluate(manifest);
   assertThat(manifest.getRuntimeEligible()).isFalse();
 }
}
