package com.example.gameworkbench.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.gameworkbench.entity.ModelCallMetric;
import com.example.gameworkbench.analytics.PromptMetricRow;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.time.LocalDateTime;
import java.util.List;

public interface ModelCallMetricMapper extends BaseMapper<ModelCallMetric> {
 @Select("select m.prompt_version_id as promptVersionId,a.agent_type as agentType,m.status,m.mock_state as mockState,m.latency_ms as latencyMs,m.input_tokens as inputTokens,m.output_tokens as outputTokens,m.estimated_cost as estimatedCost,m.created_at as createdAt from model_call_metric m join agent_run a on a.id=m.agent_run_id where a.user_id=#{userId} and a.deleted=0 and m.created_at>=#{from} and m.created_at<#{to} and (#{agentType} is null or a.agent_type=#{agentType}) and (#{projectId} is null or a.project_id=#{projectId})")
 List<PromptMetricRow> selectAnalyticsRows(@Param("userId") Long userId,@Param("projectId") Long projectId,@Param("agentType") String agentType,@Param("from") LocalDateTime from,@Param("to") LocalDateTime to);
}
