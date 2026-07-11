package com.example.gameworkbench.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.gameworkbench.entity.WorkflowRun;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import java.util.List;
import java.time.LocalDateTime;

public interface WorkflowRunMapper extends BaseMapper<WorkflowRun> {

    @Select("""
            select * from workflow_run
            where user_id = #{userId} and project_id = #{projectId} and idempotency_key = #{idempotencyKey}
              and deleted = 0
            order by id asc limit 1
            """)
    WorkflowRun selectByProjectIdempotencyKey(
            @Param("userId") Long userId,
            @Param("projectId") Long projectId,
            @Param("idempotencyKey") String idempotencyKey
    );

    @Update("""
            update workflow_run
            set status = 'RUNNING', status_version = status_version + 1, heartbeat_at = #{now}, last_activity_at = #{now}, updated_at = #{now}
            where workflow_run_uuid = #{workflowRunUuid} and status = 'QUEUED' and attempt = #{attempt}
              and status_version = #{statusVersion} and deleted = 0
            """)
    int claimForExecution(@Param("workflowRunUuid") String workflowRunUuid, @Param("attempt") int attempt,
                          @Param("statusVersion") Long statusVersion, @Param("now") LocalDateTime now);

    @Update("""
            update workflow_run set status = 'QUEUED', status_version = status_version + 1,
                last_activity_at = #{now}, updated_at = #{now}
            where workflow_run_uuid = #{workflowRunUuid} and status = 'PENDING' and deleted = 0
            """)
    int markQueuedAfterOutboxConfirm(@Param("workflowRunUuid") String workflowRunUuid, @Param("now") LocalDateTime now);

    @Update("""
            update workflow_run set status = 'QUEUED', status_version = status_version + 1,
                last_activity_at = #{now}, updated_at = #{now}
            where workflow_run_uuid = #{workflowRunUuid} and status = 'RETRY_WAIT'
              and status_version = #{statusVersion} and deleted = 0
            """)
    int queueRetryForDelivery(@Param("workflowRunUuid") String workflowRunUuid,
                              @Param("statusVersion") Long statusVersion, @Param("now") LocalDateTime now);

    @Update("""
            update workflow_run
            set status = 'FAILED', error_message = #{errorMessage}, heartbeat_at = #{now}, last_activity_at = #{now}, updated_at = #{now}
            where workflow_run_uuid = #{workflowRunUuid} and status = 'RUNNING'
            """)
    int markConsumerFailure(@Param("workflowRunUuid") String workflowRunUuid,
                            @Param("errorMessage") String errorMessage, @Param("now") LocalDateTime now);

    @Update("""
            update workflow_run set status = 'RETRY_WAIT', status_version = status_version + 1, retry_count = retry_count + 1,
                last_error_code = #{errorCode}, last_error_message = #{errorMessage}, next_retry_at = #{nextRetryAt},
                heartbeat_at = #{now}, last_activity_at = #{now}, updated_at = #{now}
            where workflow_run_uuid = #{workflowRunUuid} and status = 'RUNNING'
            """)
    int recordRetryableFailure(@Param("workflowRunUuid") String workflowRunUuid, @Param("errorCode") String errorCode,
                               @Param("errorMessage") String errorMessage, @Param("nextRetryAt") LocalDateTime nextRetryAt,
                               @Param("now") LocalDateTime now);

    @Update("""
            update workflow_run set status = 'FAILED', status_version = status_version + 1, last_error_code = #{errorCode}, last_error_message = #{errorMessage},
                error_message = #{errorMessage}, failed_at = #{now}, heartbeat_at = #{now}, last_activity_at = #{now}, updated_at = #{now}
            where workflow_run_uuid = #{workflowRunUuid} and status = 'RUNNING'
            """)
    int recordTerminalFailure(@Param("workflowRunUuid") String workflowRunUuid, @Param("errorCode") String errorCode,
                              @Param("errorMessage") String errorMessage, @Param("now") LocalDateTime now);

    @Select("""
            select count(*) from workflow_run
            where status not in ('SUCCESS', 'FAILED', 'CANCELED') and deleted = 0
            """)
    long countNonTerminalRuns();

    @Select("""
            select * from workflow_run where status = 'PENDING' and deleted = 0
              and coalesce(last_activity_at, created_at) < #{staleBefore} order by id asc limit #{limit}
            """)
    List<WorkflowRun> selectStalePending(@Param("staleBefore") LocalDateTime staleBefore, @Param("limit") int limit);

    @Select("""
            select * from workflow_run where status = 'QUEUED' and deleted = 0
              and coalesce(last_activity_at, created_at) < #{staleBefore} order by id asc limit #{limit}
            """)
    List<WorkflowRun> selectStaleQueued(@Param("staleBefore") LocalDateTime staleBefore, @Param("limit") int limit);

    @Select("""
            select * from workflow_run where status = 'RUNNING' and deleted = 0 and heartbeat_at < #{staleBefore}
            order by id asc limit #{limit}
            """)
    List<WorkflowRun> selectStaleRunning(@Param("staleBefore") LocalDateTime staleBefore, @Param("limit") int limit);

    @Update("""
            update workflow_run set status = #{nextStatus}, status_version = status_version + 1,
                recovery_attempt = recovery_attempt + 1, heartbeat_at = #{now}, last_activity_at = #{now}, updated_at = #{now}
            where workflow_run_uuid = #{workflowRunUuid} and status = #{status} and status_version = #{statusVersion}
              and deleted = 0
              and ((#{status} = 'RUNNING' and heartbeat_at < #{staleBefore})
                   or (#{status} in ('PENDING', 'QUEUED') and coalesce(last_activity_at, created_at) < #{staleBefore}))
            """)
    int claimForRecovery(@Param("workflowRunUuid") String workflowRunUuid, @Param("status") String status,
                         @Param("statusVersion") Long statusVersion, @Param("staleBefore") LocalDateTime staleBefore,
                         @Param("now") LocalDateTime now,
                         @Param("nextStatus") String nextStatus);

    @Update("""
            update workflow_run set heartbeat_at = #{now}, last_activity_at = #{now}, updated_at = #{now}
            where workflow_run_uuid = #{workflowRunUuid} and status = 'RUNNING' and deleted = 0
            """)
    int touchHeartbeat(@Param("workflowRunUuid") String workflowRunUuid, @Param("now") LocalDateTime now);
}
