package com.example.gameworkbench.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("workflow_run")
public class WorkflowRun {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String workflowRunUuid;

    private Long projectId;

    private Long userId;

    private String workflowType;

    private Long workflowDefinitionVersionId;

    private String workflowDefinitionSnapshot;

    private String promptVersionSnapshot;

    private String schemaVersion;

    private Integer attempt;

    private Long statusVersion;

    private String idempotencyKey;

    private String requestFingerprint;

    private String traceId;

    private Long eventSequence;

    private LocalDateTime heartbeatAt;

    private LocalDateTime lastActivityAt;

    private Integer retryCount;

    private Integer recoveryAttempt;

    private String lastErrorCode;

    private String lastErrorMessage;

    private LocalDateTime nextRetryAt;

    private LocalDateTime failedAt;

    private String status;

    private String inputContent;

    private String summary;

    private String errorMessage;

    private Long timeTakenMs;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
