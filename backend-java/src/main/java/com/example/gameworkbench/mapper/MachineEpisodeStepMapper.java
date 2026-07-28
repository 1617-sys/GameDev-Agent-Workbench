package com.example.gameworkbench.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.gameworkbench.entity.MachineEpisodeStep;

public interface MachineEpisodeStepMapper extends BaseMapper<MachineEpisodeStep> {
    @Select("select * from machine_episode_step where episode_id=#{episodeId} order by sequence_number")
    List<MachineEpisodeStep> selectByEpisodeId(@Param("episodeId") Long episodeId);
    @Select("select * from machine_episode_step where episode_id=#{episodeId} order by sequence_number limit #{size} offset #{offset}")
    List<MachineEpisodeStep> selectPage(@Param("episodeId") Long episodeId,@Param("offset") int offset,@Param("size") int size);
    @Select("select count(*) from machine_episode_step where episode_id=#{episodeId}") int countEpisodeSteps(@Param("episodeId") Long episodeId);
}
