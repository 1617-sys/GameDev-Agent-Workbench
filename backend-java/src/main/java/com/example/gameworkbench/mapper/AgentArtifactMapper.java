package com.example.gameworkbench.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.gameworkbench.entity.AgentArtifact;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

public interface AgentArtifactMapper extends BaseMapper<AgentArtifact> {
    @Select("select id, artifact_uuid, project_id, agent_run_id, step_run_id, artifact_type, title, content, schema_key, schema_version, validation_summary, created_at, updated_at, deleted from agent_artifact where step_run_id = #{stepRunId} order by id desc limit 1")
    AgentArtifact selectLatestByStepRunId(@Param("stepRunId") Long stepRunId);

    @Select("""
            <script>
            select id, artifact_uuid, step_run_id, artifact_type, title, created_at
            from agent_artifact
            where deleted = 0 and step_run_id in
            <foreach item='stepRunId' collection='stepRunIds' open='(' separator=',' close=')'>#{stepRunId}</foreach>
            order by step_run_id asc, created_at asc, id asc
            </script>
            """)
    List<AgentArtifact> selectReadModelByStepRunIds(@Param("stepRunIds") Collection<Long> stepRunIds);
}
