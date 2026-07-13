package com.example.gameworkbench.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.gameworkbench.common.exception.BusinessException;
import com.example.gameworkbench.entity.AgentRun;
import com.example.gameworkbench.entity.RetrievalRecord;
import com.example.gameworkbench.entity.WorkflowRun;
import com.example.gameworkbench.entity.WorkflowStepRun;
import com.example.gameworkbench.evaluation.RagComparisonService;
import com.example.gameworkbench.evaluation.RagComparisonReport;
import com.example.gameworkbench.evaluation.RagCohortStats;
import com.example.gameworkbench.mapper.AgentRunMapper;
import com.example.gameworkbench.mapper.RetrievalRecordMapper;
import com.example.gameworkbench.mapper.WorkflowRunMapper;
import com.example.gameworkbench.mapper.WorkflowStepRunMapper;

class RagEvidenceReadServiceTest {

    @Test
    void returnsOnlyPersistedSelectedReferencesForOwnedRun() {
        Fixture fixture = new Fixture();
        WorkflowRun workflow = WorkflowRun.builder().workflowRunUuid("workflow-1").projectId(9L).build();
        WorkflowStepRun step = WorkflowStepRun.builder().stepKey("design").agentRunId(12L).build();
        AgentRun agentRun = new AgentRun();
        agentRun.setId(12L);
        agentRun.setRunUuid("agent-1");
        agentRun.setRagEnabled(true);
        agentRun.setRagStatus("AVAILABLE");
        agentRun.setMockState("FALSE");
        agentRun.setRagExperimentKey("experiment-key");
        agentRun.setPromptVersionId(4L);
        agentRun.setProvider("fixture");
        agentRun.setModelName("model");
        agentRun.setCreatedAt(LocalDateTime.of(2026, 7, 13, 12, 0));
        RetrievalRecord record = new RetrievalRecord();
        record.setDocumentUuid("document-1");
        record.setDocumentVersion(3);
        record.setChunkUuid("chunk-1");
        record.setRankNo(1);
        record.setScore(new BigDecimal("0.91"));
        record.setQueryHash("must-not-be-returned");
        when(fixture.workflowRuns.selectReadModelByUserIdAndWorkflowRunUuid(7L, "workflow-1"))
                .thenReturn(workflow);
        when(fixture.workflowSteps.selectByWorkflowRunUuid("workflow-1")).thenReturn(List.of(step));
        when(fixture.agentRuns.selectOne(any())).thenReturn(agentRun);
        when(fixture.retrievalRecords.selectList(any())).thenReturn(List.of(record));
        RagCohortStats stats = new RagCohortStats(1, 1, 1, 1, 1, 10, 10, 1, 1,
                BigDecimal.ZERO, 0, 0, 1, 0, 0, 0);
        when(fixture.comparisons.compare(any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean()))
                .thenAnswer(invocation -> new RagComparisonReport(
                        "COMPARABLE", "must-not-be-returned", 9L, 4L, "fixture", "model",
                        invocation.getArgument(6), invocation.getArgument(7), "retrieval-v1", "chunking-v1",
                        "embedding-v1", List.of("private-document-snapshot"), List.of("RULE:v1"),
                        stats, stats, null, null));

        var result = fixture.service().list(7L, "workflow-1");

        assertThat(result).singleElement().satisfies(evidence -> {
            assertThat(evidence.ragStatus()).isEqualTo("AVAILABLE");
            assertThat(evidence.references()).singleElement().satisfies(reference -> {
                assertThat(reference.documentUuid()).isEqualTo("document-1");
                assertThat(reference.rank()).isEqualTo(1);
            });
            assertThat(evidence.comparison().from()).isEqualTo(LocalDateTime.of(2026, 6, 13, 12, 0));
            assertThat(evidence.comparison().evaluationVersions()).containsExactly("RULE:v1");
        });
    }

    @Test
    void refusesForeignWorkflowBeforeReadingStepsOrRecords() {
        Fixture fixture = new Fixture();
        when(fixture.workflowRuns.selectReadModelByUserIdAndWorkflowRunUuid(7L, "foreign"))
                .thenReturn(null);

        assertThatThrownBy(() -> fixture.service().list(7L, "foreign"))
                .isInstanceOf(BusinessException.class);
        verify(fixture.workflowSteps, never()).selectByWorkflowRunUuid(any());
        verify(fixture.retrievalRecords, never()).selectList(any());
    }

    private static class Fixture {
        final WorkflowRunMapper workflowRuns = mock(WorkflowRunMapper.class);
        final WorkflowStepRunMapper workflowSteps = mock(WorkflowStepRunMapper.class);
        final AgentRunMapper agentRuns = mock(AgentRunMapper.class);
        final RetrievalRecordMapper retrievalRecords = mock(RetrievalRecordMapper.class);
        final RagComparisonService comparisons = mock(RagComparisonService.class);

        RagEvidenceReadService service() {
            return new RagEvidenceReadService(
                    workflowRuns,
                    workflowSteps,
                    agentRuns,
                    retrievalRecords,
                    comparisons
            );
        }
    }
}
