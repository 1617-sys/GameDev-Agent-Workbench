package com.example.gameworkbench.entity;

import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @Builder @NoArgsConstructor @AllArgsConstructor @TableName("director_tool_call")
public class DirectorToolCallRecord {
    @TableId(type=IdType.AUTO) private Long id;
    private String callUuid;
    private Long directorRunId;
    private Long decisionId;
    private Long projectId;
    private String toolName;
    private String toolVersion;
    private String idempotencyKey;
    private String status;
    private String inputDigest;
    private String inputSummary;
    private String outputDigest;
    private String outputSummary;
    private String resultRef;
    private Long durationMs;
    private String errorCode;
    private Integer retryCount;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
}
