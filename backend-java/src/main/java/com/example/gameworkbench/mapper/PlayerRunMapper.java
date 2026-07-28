package com.example.gameworkbench.mapper;

import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.gameworkbench.entity.PlayerRun;

public interface PlayerRunMapper extends BaseMapper<PlayerRun> {
    @Select("select * from player_run where user_id=#{userId} and project_id=#{projectId} and idempotency_key=#{key} limit 1")
    PlayerRun selectIdempotent(@Param("userId") Long userId, @Param("projectId") Long projectId, @Param("key") String key);
    @Select("select * from player_run where run_uuid=#{uuid} limit 1") PlayerRun selectByUuid(@Param("uuid") String uuid);
    @Select("select * from player_run where project_id=#{projectId} and prototype_version_uuid=#{versionUuid} order by created_at desc limit #{limit}")
    List<PlayerRun> selectVersionRuns(@Param("projectId") Long projectId, @Param("versionUuid") String versionUuid, @Param("limit") int limit);
    @Select("select * from player_run where status in ('PENDING','PERSISTING') order by created_at limit #{limit}") List<PlayerRun> selectRunnable(@Param("limit") int limit);
    @Update("update player_run set status='RUNNING',attempt=attempt+1,updated_at=#{now} where run_uuid=#{uuid} and status in ('PENDING','PERSISTING')")
    int claim(@Param("uuid") String uuid, @Param("now") LocalDateTime now);
    @Update("update player_run set response_json=#{response},status='PERSISTING',updated_at=#{now} where run_uuid=#{uuid} and status='RUNNING'")
    int saveResponse(@Param("uuid") String uuid, @Param("response") String response, @Param("now") LocalDateTime now);
    @Update("update player_run set status=#{status},persisted_batch_uuid=#{batchUuid},completed_at=#{now},updated_at=#{now},error_code=null,error_message=null where run_uuid=#{uuid} and status in ('RUNNING','PERSISTING')")
    int complete(@Param("uuid") String uuid, @Param("status") String status, @Param("batchUuid") String batchUuid, @Param("now") LocalDateTime now);
    @Update("update player_run set status=case when response_json is null then 'PENDING' else 'PERSISTING' end,error_code=#{code},error_message=#{message},updated_at=#{now} where run_uuid=#{uuid} and status in ('RUNNING','PERSISTING')")
    int retry(@Param("uuid") String uuid, @Param("code") String code, @Param("message") String message, @Param("now") LocalDateTime now);
    @Update("update player_run set status='FAILED',error_code=#{code},error_message=#{message},completed_at=#{now},updated_at=#{now} where run_uuid=#{uuid} and status in ('RUNNING','PERSISTING')")
    int fail(@Param("uuid") String uuid, @Param("code") String code, @Param("message") String message, @Param("now") LocalDateTime now);
}
