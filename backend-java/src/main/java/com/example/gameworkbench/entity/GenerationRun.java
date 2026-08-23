package com.example.gameworkbench.entity;

import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import com.fasterxml.jackson.annotation.JsonIgnore;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("generation_run")
public class GenerationRun {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String runUuid;
    private Long userId;
    private Long projectId;
    private String idempotencyKey;
    private String requestFingerprint;
    private String status;
    private Long stateVersion;
    @JsonIgnore
    private String buildClaimToken;
    private LocalDateTime buildClaimExpiresAt;
    private Integer buildAttempt;
    private String canonicalSpecJson;
    private String sourceDigest;
    private String runtimeIrJson;
    private String runtimeIrDigest;
    private String buildRequestJson;
    private String diagnosticsJson;
    private String packageDigest;
    private String errorCode;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime completedAt;
}
