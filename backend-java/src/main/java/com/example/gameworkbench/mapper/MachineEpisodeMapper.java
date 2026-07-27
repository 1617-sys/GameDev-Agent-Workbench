package com.example.gameworkbench.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.gameworkbench.entity.MachineEpisode;

public interface MachineEpisodeMapper extends BaseMapper<MachineEpisode> {
    @Select("select * from machine_episode where batch_id=#{batchId} order by id")
    List<MachineEpisode> selectByBatchId(@Param("batchId") Long batchId);
    @Select("select * from machine_episode where episode_uuid=#{uuid} limit 1")
    MachineEpisode selectByUuid(@Param("uuid") String uuid);
    @Select("select * from machine_episode where project_id=#{projectId} and prototype_version_uuid=#{versionUuid} order by id")
    List<MachineEpisode> selectForAggregate(@Param("projectId") Long projectId, @Param("versionUuid") String versionUuid);
}
