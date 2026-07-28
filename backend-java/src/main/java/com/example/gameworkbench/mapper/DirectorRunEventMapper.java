package com.example.gameworkbench.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.gameworkbench.entity.DirectorRunEvent;

public interface DirectorRunEventMapper extends BaseMapper<DirectorRunEvent>{
    @Select("select * from director_run_event where director_run_id=#{runId} and project_id=#{projectId} order by id")
    List<DirectorRunEvent> selectRunEvents(@Param("runId")Long runId,@Param("projectId")Long projectId);
}
