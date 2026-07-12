package com.example.gameworkbench.service;
import com.example.gameworkbench.vo.analytics.PromptVersionMetricVO; import java.time.LocalDateTime; import java.util.List;
public interface PromptVersionAnalyticsService { List<PromptVersionMetricVO> metrics(Long userId,Long projectId,String agentType,LocalDateTime from,LocalDateTime to,boolean includeMock); }
