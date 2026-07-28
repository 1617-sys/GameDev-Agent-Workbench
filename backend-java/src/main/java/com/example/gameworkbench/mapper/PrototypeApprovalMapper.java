package com.example.gameworkbench.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.gameworkbench.entity.PrototypeApproval;

public interface PrototypeApprovalMapper extends BaseMapper<PrototypeApproval> {
    @Select("select * from prototype_approval where prototype_version_uuid=#{uuid} limit 1") PrototypeApproval selectByVersion(@Param("uuid")String uuid);
    @Select("select * from prototype_approval where actor_user_id=#{userId} and project_id=#{projectId} and idempotency_key=#{key} limit 1")
    PrototypeApproval selectIdempotent(@Param("userId")Long userId,@Param("projectId")Long projectId,@Param("key")String key);
}
