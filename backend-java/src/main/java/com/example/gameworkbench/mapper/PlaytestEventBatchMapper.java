package com.example.gameworkbench.mapper;

import org.apache.ibatis.annotations.*;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.gameworkbench.entity.PlaytestEventBatch;

public interface PlaytestEventBatchMapper extends BaseMapper<PlaytestEventBatch> {
    @Select("select * from playtest_event_batch where session_id=#{sessionId} and batch_uuid=#{batchUuid} limit 1")
    PlaytestEventBatch selectBatch(@Param("sessionId") Long sessionId, @Param("batchUuid") String batchUuid);
    @Select("select count(*) from playtest_event_batch where session_id=#{sessionId} and created_at >= date_sub(current_timestamp, interval 1 minute)")
    int countRecentBySession(@Param("sessionId") Long sessionId);
    @Select("select count(*) from playtest_event_batch b join playtest_session s on s.id=b.session_id where s.user_id=#{userId} and b.created_at >= date_sub(current_timestamp, interval 1 minute)")
    int countRecentByUser(@Param("userId") Long userId);
}
