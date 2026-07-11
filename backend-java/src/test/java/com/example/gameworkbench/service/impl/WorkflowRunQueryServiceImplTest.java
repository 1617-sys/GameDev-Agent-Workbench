package com.example.gameworkbench.service.impl;

import com.example.gameworkbench.common.enums.ErrorCode;
import com.example.gameworkbench.common.exception.BusinessException;
import com.example.gameworkbench.entity.AgentArtifact;
import com.example.gameworkbench.entity.WorkflowRun;
import com.example.gameworkbench.entity.WorkflowStepRun;
import com.example.gameworkbench.mapper.AgentArtifactMapper;
import com.example.gameworkbench.mapper.WorkflowRunMapper;
import com.example.gameworkbench.mapper.WorkflowStepRunMapper;
import com.example.gameworkbench.vo.workflow.WorkflowRunDetailVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkflowRunQueryServiceImplTest {

    @Mock private WorkflowRunMapper runs;
    @Mock private WorkflowStepRunMapper steps;
    @Mock private AgentArtifactMapper artifacts;

    @Test
    void shouldReturnOnlySafePersistedReadModelAndNeverWrite() {
        WorkflowRun run = WorkflowRun.builder()
                .workflowRunUuid("run-1").status("FAILED").attempt(2).workflowDefinitionVersionId(8L)
                .schemaVersion("game-config/1.0").lastErrorCode("AGENT_RUN_ERROR")
                .createdAt(LocalDateTime.of(2026, 7, 12, 8, 0)).build();
        WorkflowStepRun first = WorkflowStepRun.builder().id(10L).stepKey("concept").stepOrder(1)
                .status("SUCCESS").attempt(1).build();
        WorkflowStepRun second = WorkflowStepRun.builder().id(11L).stepKey("build").stepOrder(2)
                .status("FAILED").attempt(2).errorMessage("secret stack trace").build();
        AgentArtifact artifact = AgentArtifact.builder().artifactUuid("artifact-1").stepRunId(10L)
                .artifactType("GAME_CONFIG").title("Game config").content("must not be returned").build();
        when(runs.selectReadModelByUserIdAndWorkflowRunUuid(7L, "run-1")).thenReturn(run);
        when(steps.selectReadModelByWorkflowRunUuid("run-1")).thenReturn(List.of(first, second));
        when(artifacts.selectReadModelByStepRunIds(any())).thenReturn(List.of(artifact));

        WorkflowRunDetailVO result = service().getRun(7L, "run-1");

        assertThat(result.getWorkflowRunUuid()).isEqualTo("run-1");
        assertThat(result.getError().getCode()).isEqualTo("AGENT_RUN_ERROR");
        assertThat(result.getError().getMessage()).isEqualTo("Workflow execution failed");
        assertThat(result.getSteps()).extracting(WorkflowRunDetailVO.WorkflowStepReadVO::getStepKey)
                .containsExactly("concept", "build");
        assertThat(result.getSteps().get(1).getError().getMessage()).isEqualTo("Workflow execution failed");
        assertThat(result.getArtifacts()).singleElement().satisfies(item -> {
            assertThat(item.getUrl()).isEqualTo("/api/artifacts/artifact-1");
            assertThat(item.getDisplayName()).isEqualTo("Game config");
        });
        verify(runs, never()).insert(any(WorkflowRun.class));
        verify(runs, never()).updateById(any(WorkflowRun.class));
        verify(steps, never()).insert(any(WorkflowStepRun.class));
        verify(steps, never()).updateById(any(WorkflowStepRun.class));
        verify(artifacts, never()).insert(any(AgentArtifact.class));
        verify(artifacts, never()).updateById(any(AgentArtifact.class));
    }

    @Test
    void shouldRejectUnauthenticatedRequestsWithoutReading() {
        assertThatThrownBy(() -> service().getRun(null, "run-1"))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.UNAUTHORIZED.getMessage());
        verifyNoInteractions(runs, steps, artifacts);
    }

    @Test
    void shouldUseSameNotFoundResponseForUnknownOrForeignRun() {
        when(runs.selectReadModelByUserIdAndWorkflowRunUuid(7L, "run-1")).thenReturn(null);

        assertThatThrownBy(() -> service().getSteps(7L, "run-1"))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.WORKFLOW_RUN_NOT_FOUND.getMessage());
        verifyNoInteractions(steps, artifacts);
    }

    @Test
    void shouldSupportHistoricalRunWithNoStepsOrArtifacts() {
        WorkflowRun run = WorkflowRun.builder().workflowRunUuid("old-run").status("SUCCESS").build();
        when(runs.selectReadModelByUserIdAndWorkflowRunUuid(7L, "old-run")).thenReturn(run);
        when(steps.selectReadModelByWorkflowRunUuid("old-run")).thenReturn(List.of());

        WorkflowRunDetailVO result = service().getRun(7L, "old-run");

        assertThat(result.getSteps()).isEmpty();
        assertThat(result.getArtifacts()).isEmpty();
        assertThat(result.getError()).isNull();
        verifyNoInteractions(artifacts);
    }

    private WorkflowRunQueryServiceImpl service() {
        return new WorkflowRunQueryServiceImpl(runs, steps, artifacts);
    }
}
