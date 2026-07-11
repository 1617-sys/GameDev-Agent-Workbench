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
@TableName("workflow_definition_version")
public class WorkflowDefinitionVersion {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String workflowKey;

    private Integer version;

    private String name;

    private String status;

    private String definitionJson;

    private LocalDateTime createdAt;

    private Long createdBy;
}
