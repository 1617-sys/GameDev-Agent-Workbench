package com.example.gameworkbench.entity;

import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.*;
import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
@TableName("prototype_export_job")
public class PrototypeExportJob {
    @TableId(type=IdType.AUTO) private Long id;
    private String jobUuid;
    private Long userId;
    private Long projectId;
    private String prototypeVersionUuid;
    private String operation;
    private String idempotencyKey;
    private String requestFingerprint;
    private String frozenInputJson;
    private String status;
    private String packageName;
    private String packageDigest;
    private Long packageSize;
    private byte[] packageBytes;
    private Integer attemptCount;
    private String errorCode;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
}
