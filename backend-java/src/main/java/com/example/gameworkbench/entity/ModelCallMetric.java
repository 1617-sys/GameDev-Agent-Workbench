package com.example.gameworkbench.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("model_call_metric")
public class ModelCallMetric {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long agentRunId;
    private Long workflowRunId;
    private Long stepRunId;
    private Long promptVersionId;
    private String provider;
    private String modelName;
    private Integer inputTokens;
    private Integer outputTokens;
    private BigDecimal estimatedCost;
    private Long latencyMs;
    private String mockState;
    private String status;
    private String usageState;
    private String errorCategory;
    private String traceId;
    private LocalDateTime createdAt;
}
