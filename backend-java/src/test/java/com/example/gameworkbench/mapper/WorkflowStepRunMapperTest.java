package com.example.gameworkbench.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

class WorkflowStepRunMapperTest {

    @Test
    void readModelQueryShouldBeOrderedAndExcludeSensitiveSnapshots() throws Exception {
        Select select = WorkflowStepRunMapper.class
                .getMethod("selectReadModelByWorkflowRunUuid", String.class)
                .getAnnotation(Select.class);

        assertThat(select.value()[0])
                .contains("where workflow_run_uuid = #{workflowRunUuid}")
                .contains("order by step_order asc, step_key asc, attempt asc")
                .doesNotContain("input_snapshot")
                .doesNotContain("context_snapshot")
                .doesNotContain("output_snapshot")
                .doesNotContain("error_message");
    }

    @Test
    void workflowRunUuidQueryShouldReturnStepRunsInExecutionOrder() throws Exception {
        Select select = WorkflowStepRunMapper.class
                .getMethod("selectByWorkflowRunUuid", String.class)
                .getAnnotation(Select.class);

        assertThat(select.value()[0])
                .contains("where workflow_run_uuid = #{workflowRunUuid}")
                .contains("order by step_order asc, attempt asc");
    }
}
