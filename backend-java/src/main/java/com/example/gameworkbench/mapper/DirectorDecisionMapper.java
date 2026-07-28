package com.example.gameworkbench.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.gameworkbench.entity.DirectorDecisionRecord;

public interface DirectorDecisionMapper extends BaseMapper<DirectorDecisionRecord> {
    @Select("select * from director_decision where director_run_id=#{runId} and project_id=#{projectId} order by round_number")
    List<DirectorDecisionRecord> selectRunDecisions(@Param("runId") Long runId,@Param("projectId") Long projectId);
}
