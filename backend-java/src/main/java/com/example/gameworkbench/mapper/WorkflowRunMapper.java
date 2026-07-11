package com.example.gameworkbench.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.gameworkbench.entity.WorkflowRun;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
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
            where workflow_run_uuid = #{workflowRunUuid} and status in ('PENDING', 'QUEUED') and attempt = #{attempt}
            """)
    int claimForExecution(@Param("workflowRunUuid") String workflowRunUuid, @Param("attempt") int attempt,
                          @Param("now") LocalDateTime now);

    @Update("""
            update workflow_run
            set status = 'FAILED', error_message = #{errorMessage}, heartbeat_at = #{now}, last_activity_at = #{now}, updated_at = #{now}
            where workflow_run_uuid = #{workflowRunUuid} and status = 'RUNNING'
            """)
    int markConsumerFailure(@Param("workflowRunUuid") String workflowRunUuid,
                            @Param("errorMessage") String errorMessage, @Param("now") LocalDateTime now);
}
