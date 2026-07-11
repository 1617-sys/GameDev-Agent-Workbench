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
@TableName("prompt_version")
public class PromptVersion {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String versionUuid;

    private Long templateId;

    private String templateUuid;

    private String agentType;

    private Integer version;

    private String name;

    private String systemPrompt;

    private String userPromptTemplate;

    private String outputSchemaKey;

    private String outputSchemaVersion;

    private String modelParametersJson;

    private String status;

    private Long createdBy;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Integer deleted;
}
