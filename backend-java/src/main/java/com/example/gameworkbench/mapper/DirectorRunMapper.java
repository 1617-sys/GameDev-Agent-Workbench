package com.example.gameworkbench.mapper;

import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
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
}
