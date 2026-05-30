CREATE TABLE IF NOT EXISTS prompt_template (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Prompt模板主键ID',
    template_uuid VARCHAR(36) NOT NULL COMMENT 'Prompt模板唯一标识',
    agent_type VARCHAR(50) NOT NULL COMMENT 'Agent类型',
    name VARCHAR(100) NOT NULL COMMENT '模板名称',
    system_prompt TEXT NOT NULL COMMENT '系统提示词',
    user_prompt_template TEXT NOT NULL COMMENT '用户提示词模板',
    version INT NOT NULL DEFAULT 1 COMMENT '模板版本号',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '模板状态：ACTIVE/INACTIVE',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除，1已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_prompt_template_uuid (template_uuid),
    KEY idx_prompt_template_agent_type (agent_type),
    KEY idx_prompt_template_status (status),
    KEY idx_prompt_template_agent_status (agent_type, status)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='Prompt模板表';
