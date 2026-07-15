package com.example.gameworkbench.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.gameworkbench.entity.PrototypeVersion;

public interface PrototypeVersionMapper extends BaseMapper<PrototypeVersion> {
    @Insert("insert ignore into prototype_version_sequence(project_id, next_version) values(#{projectId}, 1)")
    int ensureSequence(@Param("projectId") Long projectId);

    @Select("select next_version from prototype_version_sequence where project_id = #{projectId} for update")
    Integer lockNextVersion(@Param("projectId") Long projectId);

    @Update("update prototype_version_sequence set next_version = next_version + 1 where project_id = #{projectId} and next_version = #{expected}")
    int advanceSequence(@Param("projectId") Long projectId, @Param("expected") Integer expected);

    @Select("select * from prototype_version where created_by=#{userId} and project_id=#{projectId} and operation=#{operation} and idempotency_key=#{idempotencyKey} limit 1")
    PrototypeVersion selectIdempotent(@Param("userId") Long userId, @Param("projectId") Long projectId,
            @Param("operation") String operation, @Param("idempotencyKey") String idempotencyKey);

    @Select("select * from prototype_version where project_id=#{projectId} order by version_number desc")
    List<PrototypeVersion> selectProjectVersions(@Param("projectId") Long projectId);

    @Select("select * from prototype_version where version_uuid=#{versionUuid} limit 1")
    PrototypeVersion selectByUuid(@Param("versionUuid") String versionUuid);

    @Select("select * from prototype_version where game_config_artifact_uuid=#{artifactUuid} limit 1")
    PrototypeVersion selectByArtifactUuid(@Param("artifactUuid") String artifactUuid);

    @Select("select * from prototype_version where project_id=#{projectId} order by version_number desc limit 1")
    PrototypeVersion selectLatest(@Param("projectId") Long projectId);
}
