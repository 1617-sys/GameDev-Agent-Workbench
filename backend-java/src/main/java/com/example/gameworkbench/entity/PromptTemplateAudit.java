package com.example.gameworkbench.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("prompt_template_audit")
public class PromptTemplateAudit {
    @TableId(type = IdType.AUTO) private Long id;
    private String auditUuid;
    private String templateUuid;
    private Long actorUserId;
    private String operation;
    private String beforeJson;
    private String afterJson;
    private LocalDateTime createdAt;
}
