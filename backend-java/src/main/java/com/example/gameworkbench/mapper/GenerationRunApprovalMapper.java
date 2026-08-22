package com.example.gameworkbench.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.gameworkbench.entity.GenerationRunApproval;

@Mapper
public interface GenerationRunApprovalMapper extends BaseMapper<GenerationRunApproval> {
    @Select("select * from generation_run_approval where generation_run_id=#{runId} limit 1")
    GenerationRunApproval selectByRunId(@Param("runId") long runId);

    @Select("select * from generation_run_approval where user_id=#{userId} and project_id=#{projectId} and idempotency_key=#{key} limit 1")
    GenerationRunApproval selectIdempotent(@Param("userId") long userId, @Param("projectId") long projectId,
            @Param("key") String key);
}
