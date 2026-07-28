package com.example.gameworkbench.entity;

import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @Builder @NoArgsConstructor @AllArgsConstructor @TableName("director_run")
public class DirectorRun {
    @TableId(type=IdType.AUTO) private Long id;
    private String runUuid;
    private Long userId;
    private Long projectId;
    private String idempotencyKey;
    private String requestFingerprint;
    private String goalJson;
    private String goalDigest;
    private String budgetJson;
    private String status;
    private Long stateVersion;
    private String checkpointJson;
    private String waitingApprovalRef;
    private String errorCode;
    private String traceId;
    private String claimToken;
    private LocalDateTime claimUntil;
    private Integer executionAttempt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime completedAt;
}
