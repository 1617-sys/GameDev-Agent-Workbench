package com.example.gameworkbench.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.gameworkbench.entity.GenerationRun;

@Mapper
public interface GenerationRunMapper extends BaseMapper<GenerationRun> {
    @Select("select * from generation_run where run_uuid=#{uuid}")
    GenerationRun selectByUuid(@Param("uuid") String uuid);

    @Select("select * from generation_run where user_id=#{userId} and project_id=#{projectId} and idempotency_key=#{key}")
    GenerationRun selectByIdempotency(@Param("userId") long userId, @Param("projectId") long projectId, @Param("key") String key);

    @Update("update generation_run set status=#{target}, state_version=state_version+1, package_digest=#{packageDigest}, "
            + "error_code=#{errorCode}, updated_at=now(6), completed_at=case when #{completed} then now(6) else completed_at end "
            + "where id=#{id} and project_id=#{projectId} and state_version=#{expectedVersion} and status=#{expectedStatus}")
    int transition(@Param("id") long id, @Param("projectId") long projectId,
            @Param("expectedVersion") long expectedVersion, @Param("expectedStatus") String expectedStatus,
            @Param("target") String target, @Param("packageDigest") String packageDigest,
            @Param("errorCode") String errorCode, @Param("completed") boolean completed);
}
