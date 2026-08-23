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

    @Update("update generation_run set status='BUILDING', state_version=state_version+1, "
            + "build_claim_token=#{claimToken}, build_claim_expires_at=#{claimExpiresAt}, "
            + "build_attempt=build_attempt+1, error_code=null, updated_at=now(6) "
            + "where id=#{id} and project_id=#{projectId} and state_version=#{expectedVersion} and "
            + "(status='READY_TO_BUILD' or (status='BUILDING' and build_claim_expires_at < now(6)))")
    int claimBuild(@Param("id") long id, @Param("projectId") long projectId,
            @Param("expectedVersion") long expectedVersion, @Param("claimToken") String claimToken,
            @Param("claimExpiresAt") java.time.LocalDateTime claimExpiresAt);

    @Update("update generation_run set status=#{target}, state_version=state_version+1, "
            + "package_digest=#{packageDigest}, error_code=#{errorCode}, build_claim_token=null, "
            + "build_claim_expires_at=null, updated_at=now(6), "
            + "completed_at=case when #{completed} then now(6) else null end "
            + "where id=#{id} and project_id=#{projectId} and state_version=#{claimedVersion} "
            + "and status='BUILDING' and build_claim_token=#{claimToken}")
    int completeBuild(@Param("id") long id, @Param("projectId") long projectId,
            @Param("claimedVersion") long claimedVersion, @Param("claimToken") String claimToken,
            @Param("target") String target, @Param("packageDigest") String packageDigest,
            @Param("errorCode") String errorCode, @Param("completed") boolean completed);

    @Update("update generation_run set status='READY_TO_BUILD', state_version=state_version+1, "
            + "build_claim_token=null, build_claim_expires_at=null, updated_at=now(6) "
            + "where id=#{id} and project_id=#{projectId} and state_version=#{claimedVersion} "
            + "and status='BUILDING' and build_claim_token=#{claimToken}")
    int releaseBuild(@Param("id") long id, @Param("projectId") long projectId,
            @Param("claimedVersion") long claimedVersion, @Param("claimToken") String claimToken);

    @Update("update generation_run set status=#{target}, state_version=state_version+1, updated_at=now(6), "
            + "completed_at=case when #{completed} then now(6) else completed_at end "
            + "where id=#{id} and project_id=#{projectId} and state_version=#{expectedVersion} and status=#{expectedStatus}")
    int transitionStatus(@Param("id") long id, @Param("projectId") long projectId,
            @Param("expectedVersion") long expectedVersion, @Param("expectedStatus") String expectedStatus,
            @Param("target") String target, @Param("completed") boolean completed);

    @Update("update generation_run set status=#{target}, state_version=state_version+1, package_digest=#{packageDigest}, "
            + "error_code=#{errorCode}, updated_at=now(6), completed_at=case when #{completed} then now(6) else completed_at end "
            + "where id=#{id} and project_id=#{projectId} and state_version=#{expectedVersion} and status=#{expectedStatus}")
    int transition(@Param("id") long id, @Param("projectId") long projectId,
            @Param("expectedVersion") long expectedVersion, @Param("expectedStatus") String expectedStatus,
            @Param("target") String target, @Param("packageDigest") String packageDigest,
            @Param("errorCode") String errorCode, @Param("completed") boolean completed);
}
