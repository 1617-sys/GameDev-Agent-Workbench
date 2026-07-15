package com.example.gameworkbench.mapper;

import org.apache.ibatis.annotations.*;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.gameworkbench.entity.PrototypeExportJob;

public interface PrototypeExportJobMapper extends BaseMapper<PrototypeExportJob> {
    @Select("select * from prototype_export_job where user_id=#{userId} and project_id=#{projectId} and operation='EXPORT_PROTOTYPE' and idempotency_key=#{key} limit 1")
    PrototypeExportJob selectIdempotent(@Param("userId") Long userId,@Param("projectId") Long projectId,@Param("key") String key);
    @Select("select * from prototype_export_job where job_uuid=#{uuid} limit 1")
    PrototypeExportJob selectByUuid(@Param("uuid") String uuid);
}
