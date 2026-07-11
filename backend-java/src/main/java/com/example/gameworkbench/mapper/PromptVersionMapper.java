package com.example.gameworkbench.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.gameworkbench.entity.PromptVersion;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface PromptVersionMapper extends BaseMapper<PromptVersion> {

    @Select("""
            select id, version_uuid, template_id, template_uuid, agent_type, version, name,
                   system_prompt, user_prompt_template, output_schema_key, output_schema_version,
                   model_parameters_json, status, created_by, created_at, updated_at, deleted
            from prompt_version
            where agent_type = #{agentType}
              and status = 'ACTIVE'
              and deleted = 0
            order by version desc, id desc
            limit 1
            """)
    PromptVersion selectActiveByAgentType(@Param("agentType") String agentType);
}
