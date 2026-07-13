package com.example.gameworkbench.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.example.gameworkbench.common.exception.BusinessException;
import com.example.gameworkbench.entity.AgentRun;
import com.example.gameworkbench.entity.GameProject;
import com.example.gameworkbench.mapper.AgentArtifactMapper;
import com.example.gameworkbench.mapper.AgentRunMapper;
import com.example.gameworkbench.mapper.EvaluationReportMapper;
import com.example.gameworkbench.mapper.GameProjectMapper;
import com.example.gameworkbench.mapper.ModelCallMetricMapper;
import com.example.gameworkbench.mapper.RetrievalRecordMapper;

class RagComparisonServiceTest {

    private GameProjectMapper projects;
    private AgentRunMapper runs;
    private ModelCallMetricMapper metrics;
    private AgentArtifactMapper artifacts;
    private EvaluationReportMapper reports;
    private RetrievalRecordMapper retrievals;
    private RagComparisonService service;

    @BeforeEach
    void setUp() {
        projects = mock(GameProjectMapper.class);
        runs = mock(AgentRunMapper.class);
        metrics = mock(ModelCallMetricMapper.class);
        artifacts = mock(AgentArtifactMapper.class);
        reports = mock(EvaluationReportMapper.class);
        retrievals = mock(RetrievalRecordMapper.class);
        service = new RagComparisonService(projects, runs, metrics, artifacts, reports, retrievals);

        GameProject project = new GameProject();
        project.setId(1L);
        when(projects.selectOne(any())).thenReturn(project);
        when(artifacts.selectList(any())).thenReturn(List.of());
    }

    @Test
    void keepsMockRunsInSeparateCohorts() {
        when(runs.selectList(any())).thenReturn(List.of(
                run(1L, false, "DISABLED", null, "FALSE"),
                run(2L, true, "AVAILABLE", "snapshot-v1", "FALSE"),
                run(3L, true, "AVAILABLE", "snapshot-v1", "TRUE")
        ));
        when(retrievals.selectCount(any())).thenReturn(1L);

        RagComparisonReport result = compare(true);

        assertThat(result.status()).isEqualTo("COMPARABLE");
        assertThat(result.ragOff().samples()).isEqualTo(1);
        assertThat(result.ragOn().samples()).isEqualTo(1);
        assertThat(result.ragOn().retrievalCovered()).isEqualTo(1);
        assertThat(result.ragOn().mockExcluded()).isEqualTo(1);
        assertThat(result.mockRagOn().samples()).isEqualTo(1);
    }

    @Test
    void reportsEmptyAndFailedRetrievalSeparately() {
        when(runs.selectList(any())).thenReturn(List.of(
                run(1L, false, "DISABLED", null, "FALSE"),
                run(2L, true, "EMPTY", "snapshot-v1", "FALSE"),
                run(3L, true, "UNAVAILABLE", "snapshot-v1", "FALSE")
        ));
        when(retrievals.selectCount(any())).thenReturn(0L);

        RagComparisonReport result = compare(false);

        assertThat(result.ragOn().emptyRetrieval()).isEqualTo(1);
        assertThat(result.ragOn().failedRetrieval()).isEqualTo(1);
        assertThat(result.ragOn().missingCostSamples()).isEqualTo(2);
    }

    @Test
    void reportsInsufficientSamplesWhenOneSideIsMissing() {
        when(runs.selectList(any())).thenReturn(List.of(
                run(1L, false, "DISABLED", null, "FALSE"),
                run(2L, false, "DISABLED", null, "FALSE")
        ));

        assertThat(compare(false).status()).isEqualTo("INSUFFICIENT_SAMPLES");
    }

    @Test
    void rejectsMixedDocumentSnapshots() {
        when(runs.selectList(any())).thenReturn(List.of(
                run(1L, false, "DISABLED", null, "FALSE"),
                run(2L, true, "AVAILABLE", "snapshot-v1", "FALSE"),
                run(3L, true, "AVAILABLE", "snapshot-v2", "FALSE")
        ));
        when(retrievals.selectCount(any())).thenReturn(1L);

        assertThat(compare(false).status()).isEqualTo("INCOMPARABLE_VERSION_MIX");
    }

    @Test
    void refusesCrossProjectAccessBeforeReadingRuns() {
        when(projects.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> compare(false)).isInstanceOf(BusinessException.class);
        verify(runs, never()).selectList(any());
    }

    private RagComparisonReport compare(boolean includeMock) {
        return service.compare(9L, 1L, "experiment-key", 7L, "provider", "model",
                null, null, includeMock);
    }

    private static AgentRun run(Long id, boolean rag, String status, String snapshot, String mockState) {
        AgentRun run = new AgentRun();
        run.setId(id);
        run.setRagEnabled(rag);
        run.setRagStatus(status);
        run.setRagContextSnapshot(snapshot);
        run.setMockState(mockState);
        run.setRetrievalVersion("retrieval-v1");
        run.setChunkingVersion("chunking-v1");
        run.setEmbeddingModel("embedding-v1");
        return run;
    }
}
