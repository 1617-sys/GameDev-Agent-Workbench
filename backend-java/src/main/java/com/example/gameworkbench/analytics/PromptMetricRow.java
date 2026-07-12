package com.example.gameworkbench.analytics;
import lombok.Data; import java.math.BigDecimal; import java.time.LocalDateTime;
@Data public class PromptMetricRow { private Long promptVersionId; private String agentType; private String status; private String mockState; private Long latencyMs; private Integer inputTokens; private Integer outputTokens; private BigDecimal estimatedCost; private LocalDateTime createdAt; }
