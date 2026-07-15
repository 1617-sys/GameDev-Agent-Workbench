package com.example.gameworkbench.mapper;

import java.util.List;
import org.apache.ibatis.annotations.*;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.gameworkbench.entity.PlaytestSession;

public interface PlaytestSessionMapper extends BaseMapper<PlaytestSession> {
    @Select("select * from playtest_session where session_uuid=#{uuid} for update")
    PlaytestSession lockByUuid(@Param("uuid") String uuid);
    @Select("select * from playtest_session where session_uuid=#{uuid} limit 1")
    PlaytestSession selectByUuid(@Param("uuid") String uuid);
    @Select("select * from playtest_session where prototype_version_uuid=#{versionUuid} and status='ENDED'")
    List<PlaytestSession> selectEndedByVersion(@Param("versionUuid") String versionUuid);
    @Select("select count(*) from playtest_session where user_id=#{userId} and started_at >= date_sub(current_timestamp, interval 1 hour)")
    int countRecentByUser(@Param("userId") Long userId);
}
