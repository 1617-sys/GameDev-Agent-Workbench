package com.example.gameworkbench.mapper;

import org.apache.ibatis.annotations.*;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.gameworkbench.entity.PrototypePlaytestAggregate;

public interface PrototypePlaytestAggregateMapper extends BaseMapper<PrototypePlaytestAggregate> {
    @Select("select * from prototype_playtest_aggregate where prototype_version_uuid=#{versionUuid} limit 1")
    PrototypePlaytestAggregate selectByVersion(@Param("versionUuid") String versionUuid);
    @Insert("""
        insert into prototype_playtest_aggregate
          (prototype_version_uuid,ended_session_count,won_count,lost_count,abandoned_count,total_duration_ms,total_score,total_hit_count,total_collected_count,total_restart_count,health_depleted_count,time_expired_count,snapshot_at)
        select #{versionUuid},count(*),coalesce(sum(outcome='WON'),0),coalesce(sum(outcome='LOST'),0),coalesce(sum(outcome='ABANDONED'),0),
          coalesce(sum(duration_ms),0),coalesce(sum(score),0),coalesce(sum(hit_count),0),coalesce(sum(collected_count),0),coalesce(sum(restart_count),0),
          coalesce(sum(failure_reason='HEALTH_DEPLETED'),0),coalesce(sum(failure_reason='TIME_EXPIRED'),0),current_timestamp
        from playtest_session where prototype_version_uuid=#{versionUuid} and status='ENDED'
        on duplicate key update ended_session_count=values(ended_session_count),won_count=values(won_count),lost_count=values(lost_count),
          abandoned_count=values(abandoned_count),total_duration_ms=values(total_duration_ms),total_score=values(total_score),
          total_hit_count=values(total_hit_count),total_collected_count=values(total_collected_count),total_restart_count=values(total_restart_count),
          health_depleted_count=values(health_depleted_count),time_expired_count=values(time_expired_count),snapshot_at=values(snapshot_at)
        """)
    int recompute(@Param("versionUuid") String versionUuid);
}
