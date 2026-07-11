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
@TableName("workflow_step_definition")
public class WorkflowStepDefinition {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long definitionVersionId;

    private String stepKey;

    private Integer stepOrder;

    private String agentType;

    private String artifactType;

    private String dependsOnStepKey;

    private String promptTemplateKey;

    private LocalDateTime createdAt;
}
