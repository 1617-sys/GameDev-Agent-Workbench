package com.example.gameworkbench.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.gameworkbench.entity.WorkflowDefinitionVersion;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface WorkflowDefinitionVersionMapper extends BaseMapper<WorkflowDefinitionVersion> {

    @Select("""
            select id, workflow_key, version, name, status, definition_json, created_at, created_by
            from workflow_definition_version
            where workflow_key = #{workflowKey}
              and status = 'ACTIVE'
            order by version desc
            limit 1
            """)
    WorkflowDefinitionVersion selectActiveByWorkflowKey(@Param("workflowKey") String workflowKey);
}
