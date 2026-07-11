package com.example.gameworkbench.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.gameworkbench.entity.WorkflowRun;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

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
}
