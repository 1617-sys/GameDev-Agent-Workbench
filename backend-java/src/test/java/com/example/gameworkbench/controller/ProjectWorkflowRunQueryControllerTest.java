package com.example.gameworkbench.controller;

import com.example.gameworkbench.service.WorkflowRunQueryService;
import com.example.gameworkbench.vo.workflow.WorkflowRunSummaryVO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectWorkflowRunQueryControllerTest {

    private final WorkflowRunQueryService queries = mock(WorkflowRunQueryService.class);
    private final ProjectWorkflowRunQueryController controller = new ProjectWorkflowRunQueryController(queries);

    @Test
    void shouldExposeOwnedProjectRunHistory() {
        List<WorkflowRunSummaryVO> runs = List.of(
                WorkflowRunSummaryVO.builder().workflowRunUuid("run-1").status("RUNNING").build()
        );
        when(queries.listProjectRuns(7L, "project-1")).thenReturn(runs);

        assertThat(controller.listProjectRuns(7L, "project-1").getData()).isSameAs(runs);
        verify(queries).listProjectRuns(7L, "project-1");
    }
}
