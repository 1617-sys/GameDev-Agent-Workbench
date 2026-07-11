package com.example.gameworkbench.mapper;

import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.gameworkbench.entity.WorkflowStepRun;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface WorkflowStepRunMapper extends BaseMapper<WorkflowStepRun> {

    @Select("""
            select id, step_run_uuid, workflow_run_uuid, step_key, step_order, agent_type, artifact_type,
                   status, attempt, schema_key, schema_version, started_at, finished_at, time_taken_ms, created_at, updated_at
            from workflow_step_run
            where workflow_run_uuid = #{workflowRunUuid}
            order by step_order asc, step_key asc, attempt asc
            """)
    List<WorkflowStepRun> selectReadModelByWorkflowRunUuid(@Param("workflowRunUuid") String workflowRunUuid);

    @Select("""
            select id, step_run_uuid, workflow_run_id, workflow_run_uuid, definition_version_id,
                   step_key, step_order, agent_type, artifact_type, agent_run_id, status, attempt,
                   input_snapshot, context_snapshot, output_snapshot, schema_key, schema_version, validation_summary, error_message,
                   started_at, finished_at, time_taken_ms, created_at, updated_at
            from workflow_step_run
            where workflow_run_uuid = #{workflowRunUuid}
            order by step_order asc, attempt asc
            """)
    List<WorkflowStepRun> selectByWorkflowRunUuid(@Param("workflowRunUuid") String workflowRunUuid);
}
