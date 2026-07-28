package com.example.gameworkbench.entity;

import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
@TableName("player_run")
public class PlayerRun {
    @TableId(type = IdType.AUTO) private Long id;
    private String runUuid;
    private Long userId;
    private Long projectId;
    private String projectUuid;
    private String prototypeVersionUuid;
    private String idempotencyKey;
    private String requestFingerprint;
    private String clientBatchKey;
    private String status;
    private String requestJson;
    private String responseJson;
    private String persistedBatchUuid;
    private String traceId;
    private Integer attempt;
    private String errorCode;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime completedAt;
}
