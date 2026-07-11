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
@TableName("outbox_event")
public class OutboxEvent {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String eventUuid;
    private String aggregateType;
    private String aggregateUuid;
    private Long workflowRunId;
    private String workflowRunUuid;
    private String eventType;
    private String payloadJson;
    private String schemaVersion;
    private String status;
    private Integer publishAttempt;
    private LocalDateTime nextAttemptAt;
    private String traceId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
