package com.example.gameworkbench.controller;

import com.example.gameworkbench.service.WorkflowRunQueryService;
import com.example.gameworkbench.vo.workflow.WorkflowRunDetailVO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkflowRunQueryControllerTest {

    private final WorkflowRunQueryService queries = mock(WorkflowRunQueryService.class);
    private final WorkflowRunQueryController controller = new WorkflowRunQueryController(queries);

    @Test
    void shouldExposeReadOnlyV1DetailAndCollectionRoutes() {
        WorkflowRunDetailVO detail = WorkflowRunDetailVO.builder().workflowRunUuid("run-1").build();
        List<WorkflowRunDetailVO.WorkflowStepReadVO> steps = List.of(WorkflowRunDetailVO.WorkflowStepReadVO.builder().stepKey("design").build());
        List<WorkflowRunDetailVO.ArtifactSummaryVO> artifacts = List.of(WorkflowRunDetailVO.ArtifactSummaryVO.builder().artifactUuid("artifact-1").build());
        when(queries.getRun(7L, "run-1")).thenReturn(detail);
        when(queries.getSteps(7L, "run-1")).thenReturn(steps);
        when(queries.getArtifacts(7L, "run-1")).thenReturn(artifacts);

        assertThat(controller.getRun(7L, "run-1").getData()).isSameAs(detail);
        assertThat(controller.getSteps(7L, "run-1").getData()).isSameAs(steps);
        assertThat(controller.getArtifacts(7L, "run-1").getData()).isSameAs(artifacts);
        verify(queries).getRun(7L, "run-1");
        verify(queries).getSteps(7L, "run-1");
        verify(queries).getArtifacts(7L, "run-1");
    }
}
