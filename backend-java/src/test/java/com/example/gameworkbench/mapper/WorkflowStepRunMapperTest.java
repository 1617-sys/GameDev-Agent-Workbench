package com.example.gameworkbench.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

class WorkflowStepRunMapperTest {

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
