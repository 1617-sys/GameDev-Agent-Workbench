package com.example.gameworkbench.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("workflow_recovery_audit_event")
public class WorkflowRecoveryAuditEvent {
    @TableId(type = IdType.AUTO) private Long id;
    private String workflowRunUuid;
    private String previousStatus;
    private String newStatus;
    private String reason;
    private Integer recoveryAttempt;
    private String eventId;
    private String traceId;
    private LocalDateTime createdAt;
}
