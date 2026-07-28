package com.example.gameworkbench.mapper;

import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import java.util.List;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.gameworkbench.entity.DirectorRun;

public interface DirectorRunMapper extends BaseMapper<DirectorRun> {
    @Select("select * from director_run where user_id=#{userId} and project_id=#{projectId} and idempotency_key=#{key} limit 1")
    DirectorRun selectIdempotent(@Param("userId") Long userId,@Param("projectId") Long projectId,@Param("key") String key);
    @Select("select * from director_run where run_uuid=#{uuid} limit 1")
    DirectorRun selectByUuid(@Param("uuid") String uuid);
    @Update("update director_run set status=#{status},state_version=state_version+1,checkpoint_json=#{checkpoint},waiting_approval_ref=#{approvalRef},error_code=#{errorCode},updated_at=#{now},completed_at=#{completedAt} where id=#{id} and project_id=#{projectId} and state_version=#{expectedVersion} and status=#{expectedStatus}")
    int transition(@Param("id") Long id,@Param("projectId") Long projectId,@Param("expectedVersion") Long expectedVersion,
        @Param("expectedStatus") String expectedStatus,@Param("status") String status,@Param("checkpoint") String checkpoint,
        @Param("approvalRef") String approvalRef,@Param("errorCode") String errorCode,@Param("now") LocalDateTime now,
        @Param("completedAt") LocalDateTime completedAt);
    @Update("update director_run set claim_token=#{token},claim_until=#{until},execution_attempt=execution_attempt+1,updated_at=#{now} where run_uuid=#{uuid} and state_version=#{version} and (status='RUNNING' or (status='WAITING_EXPERIMENT' and checkpoint_json like '%pendingToolCall%')) and (claim_until is null or claim_until<#{now})")
    int claim(@Param("uuid") String uuid,@Param("version") Long version,@Param("token") String token,
            @Param("until") LocalDateTime until,@Param("now") LocalDateTime now);
    @Update("update director_run set claim_token=null,claim_until=null,updated_at=#{now} where run_uuid=#{uuid} and claim_token=#{token}")
    int releaseClaim(@Param("uuid") String uuid,@Param("token") String token,@Param("now") LocalDateTime now);
    @Update("update director_run set claim_token=null,claim_until=null,execution_attempt=0,updated_at=#{now} where run_uuid=#{uuid} and claim_token=#{token}")
    int releaseSuccessfulClaim(@Param("uuid")String uuid,@Param("token")String token,@Param("now")LocalDateTime now);
    @Select("select * from director_run where (status='RUNNING' or (status='WAITING_EXPERIMENT' and checkpoint_json like '%pendingToolCall%')) and (claim_until is null or claim_until<#{now}) order by updated_at limit #{limit}")
    List<DirectorRun> selectRecoverable(@Param("now") LocalDateTime now,@Param("limit") int limit);
    @Select("select * from director_run where status=#{status} and waiting_approval_ref=#{ref} limit 1")
    DirectorRun selectWaitingRef(@Param("status") String status,@Param("ref") String ref);
    @Update("update director_run set trace_id=#{traceId} where run_uuid=#{uuid} and trace_id is null")
    int setTrace(@Param("uuid")String uuid,@Param("traceId")String traceId);
    @Update("update director_run set checkpoint_json=#{checkpoint},state_version=state_version+1,claim_token=null,claim_until=null,execution_attempt=0,updated_at=#{now} where run_uuid=#{uuid} and status='WAITING_EXPERIMENT' and state_version=#{version} and claim_token=#{token}")
    int checkpointWaiting(@Param("uuid")String uuid,@Param("version")Long version,@Param("token")String token,@Param("checkpoint")String checkpoint,@Param("now")LocalDateTime now);
}
