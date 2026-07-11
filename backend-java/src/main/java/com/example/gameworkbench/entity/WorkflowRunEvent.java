package com.example.gameworkbench.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("workflow_run_event")
public class WorkflowRunEvent {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String eventUuid;
    private String workflowRunUuid;
    private Long sequence;
    private String eventType;
    private String eventKey;
    private String stepKey;
    private String status;
    private Integer attempt;
    private String artifactUuid;
    private String payloadJson;
    private String traceId;
    private LocalDateTime occurredAt;
}
