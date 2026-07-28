package com.example.gameworkbench.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.gameworkbench.entity.ExperimentCandidate;

public interface ExperimentCandidateMapper extends BaseMapper<ExperimentCandidate> {
    @Select("select * from experiment_candidate where director_run_id=#{runId} and project_id=#{projectId} order by ordinal_number")
    List<ExperimentCandidate> selectRunCandidates(@Param("runId") Long runId,@Param("projectId") Long projectId);
    @Select("select * from experiment_candidate where director_run_id=#{runId} and input_digest=#{digest} order by ordinal_number")
    List<ExperimentCandidate> selectPlan(@Param("runId")Long runId,@Param("digest")String digest);
    @Select("select coalesce(max(ordinal_number),0) from experiment_candidate where director_run_id=#{runId}")
    Integer selectMaxOrdinal(@Param("runId")Long runId);
}
