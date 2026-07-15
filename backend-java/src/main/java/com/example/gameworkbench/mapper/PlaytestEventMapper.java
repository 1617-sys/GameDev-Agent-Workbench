package com.example.gameworkbench.mapper;

import java.util.List;
import org.apache.ibatis.annotations.*;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.gameworkbench.entity.PlaytestEvent;

public interface PlaytestEventMapper extends BaseMapper<PlaytestEvent> {
    @Select("select * from playtest_event where session_id=#{sessionId} order by sequence_number")
    List<PlaytestEvent> selectSessionEvents(@Param("sessionId") Long sessionId);
    @Select("select * from playtest_event where event_uuid=#{uuid} limit 1")
    PlaytestEvent selectByUuid(@Param("uuid") String uuid);
    @Select("select * from playtest_event where session_id=#{sessionId} and sequence_number=#{sequence} limit 1")
    PlaytestEvent selectBySequence(@Param("sessionId") Long sessionId, @Param("sequence") Integer sequence);
}
