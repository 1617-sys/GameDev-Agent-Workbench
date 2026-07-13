package com.example.gameworkbench.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("agent_run")
public class AgentRun {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String runUuid;

    private Long userId;

    private Long projectId;

    private String projectUuid;

    private Long promptVersionId;

    private Long workflowRunId;

    private Long stepRunId;

    private String agentType;

    private String inputContent;

    private String outputContent;

    private String status;

    private String errorMessage;

    private Long timeTakenMs;

    private String provider;

    private String modelName;

    private String mockState;

    private String traceId;

    private String errorCategory;

    private String rawOutputRef;

    private Boolean ragEnabled;
    private String ragStatus;
    private Integer contextBudget;
    private String retrievalVersion;
    private String chunkingVersion;
    private String embeddingModel;
    private String ragContextSnapshot;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
