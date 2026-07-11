package com.example.gameworkbench.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.gameworkbench.entity.AgentArtifact;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface AgentArtifactMapper extends BaseMapper<AgentArtifact> {
    @Select("select id, artifact_uuid, project_id, agent_run_id, step_run_id, artifact_type, title, content, created_at, updated_at, deleted from agent_artifact where step_run_id = #{stepRunId} order by id desc limit 1")
    AgentArtifact selectLatestByStepRunId(@Param("stepRunId") Long stepRunId);
}
