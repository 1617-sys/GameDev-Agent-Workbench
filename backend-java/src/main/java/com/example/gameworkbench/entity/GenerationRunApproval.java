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
@TableName("generation_run_approval")
public class GenerationRunApproval {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String approvalUuid;
    private Long generationRunId;
    private String generationRunUuid;
    private Long userId;
    private Long projectId;
    private Long actorUserId;
    private String decision;
    private String reason;
    private String idempotencyKey;
    private String requestFingerprint;
    private LocalDateTime createdAt;
}
