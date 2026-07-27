package com.example.gameworkbench.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.gameworkbench.entity.MachineEpisodeBatch;

public interface MachineEpisodeBatchMapper extends BaseMapper<MachineEpisodeBatch> {
    @Select("select * from machine_episode_batch where user_id=#{userId} and project_id=#{projectId} and idempotency_key=#{key} limit 1")
    MachineEpisodeBatch selectIdempotent(@Param("userId") Long userId, @Param("projectId") Long projectId, @Param("key") String key);
    @Select("select * from machine_episode_batch where batch_uuid=#{uuid} limit 1")
    MachineEpisodeBatch selectByUuid(@Param("uuid") String uuid);
}
