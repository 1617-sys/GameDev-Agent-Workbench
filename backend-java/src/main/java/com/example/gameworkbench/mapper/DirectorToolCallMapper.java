package com.example.gameworkbench.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.gameworkbench.entity.DirectorToolCallRecord;

public interface DirectorToolCallMapper extends BaseMapper<DirectorToolCallRecord> {
    @Select("select * from director_tool_call where director_run_id=#{runId} and project_id=#{projectId} order by id")
    List<DirectorToolCallRecord> selectRunCalls(@Param("runId") Long runId,@Param("projectId") Long projectId);
    @Select("select * from director_tool_call where call_uuid=#{uuid} limit 1")DirectorToolCallRecord selectByCallUuid(@Param("uuid")String uuid);
}
