package com.example.gameworkbench.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("workflow_step_run")
public class WorkflowStepRun {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String stepRunUuid;

    private Long workflowRunId;

    private String workflowRunUuid;

    private Long definitionVersionId;

    private String stepKey;

    private Integer stepOrder;

    private String agentType;

    private String artifactType;

    private String status;

    private Integer attempt;

    private String inputSnapshot;

    private String contextSnapshot;

    private String outputSnapshot;

    private String errorMessage;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    private Long timeTakenMs;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
