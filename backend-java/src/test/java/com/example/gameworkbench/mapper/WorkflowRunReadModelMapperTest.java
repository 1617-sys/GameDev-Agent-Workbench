package com.example.gameworkbench.mapper;

import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowRunReadModelMapperTest {

    @Test
    void readModelQueryShouldRequireBothRunAndProjectOwnership() throws Exception {
        Select select = WorkflowRunMapper.class
                .getMethod("selectReadModelByUserIdAndWorkflowRunUuid", Long.class, String.class)
                .getAnnotation(Select.class);

        assertThat(select.value()[0])
                .contains("inner join game_project gp on gp.id = wr.project_id and gp.deleted = 0")
                .contains("wr.workflow_run_uuid = #{workflowRunUuid}")
                .contains("wr.user_id = #{userId}")
                .contains("gp.user_id = #{userId}")
                .doesNotContain("input_content")
                .doesNotContain("prompt_version_snapshot")
                .doesNotContain("workflow_definition_snapshot");
    }
}
