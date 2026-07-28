package com.example.gameworkbench.mapper;

import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.gameworkbench.entity.DirectorExperimentRun;

public interface DirectorExperimentRunMapper extends BaseMapper<DirectorExperimentRun>{
    @Select("select * from director_experiment_run where director_run_id=#{runId} and idempotency_key=#{key} limit 1")DirectorExperimentRun selectIdempotent(@Param("runId")Long runId,@Param("key")String key);
    @Select("select * from director_experiment_run where experiment_uuid=#{uuid} limit 1")DirectorExperimentRun selectByUuid(@Param("uuid")String uuid);
    @Select("select * from director_experiment_run where baseline_player_run_uuid=#{runUuid} or candidate_player_run_uuid=#{runUuid}")List<DirectorExperimentRun> selectByPlayerRun(@Param("runUuid")String runUuid);
    @Update("update director_experiment_run set status=#{status},completed_at=#{completedAt} where id=#{id} and status in ('PENDING','RUNNING')")int complete(@Param("id")Long id,@Param("status")String status,@Param("completedAt")LocalDateTime completedAt);
    @Select("select experiment.* from director_experiment_run experiment join director_run run on run.id=experiment.director_run_id where experiment.status in ('SUCCEEDED','PARTIAL_SUCCESS','FAILED') and run.status='WAITING_EXPERIMENT' order by experiment.completed_at limit #{limit}")List<DirectorExperimentRun> selectReadyToWake(@Param("limit")int limit);
}
