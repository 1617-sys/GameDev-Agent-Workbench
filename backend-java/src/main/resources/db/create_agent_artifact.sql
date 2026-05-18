CREATE TABLE IF NOT EXISTS agent_artifact (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '产物主键ID',
    artifact_uuid VARCHAR(36) NOT NULL COMMENT '产物UUID',
    project_id BIGINT NOT NULL COMMENT '关联项目ID',
    agent_run_id BIGINT NOT NULL COMMENT '关联执行记录ID',
    artifact_type VARCHAR(50) NOT NULL COMMENT '产物类型',
    title VARCHAR(200) NOT NULL COMMENT '产物标题',
    content LONGTEXT NOT NULL COMMENT '产物内容',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除，1已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_agent_artifact_uuid (artifact_uuid),
    KEY idx_agent_artifact_project_id (project_id),
    KEY idx_agent_artifact_agent_run_id (agent_run_id),
    KEY idx_agent_artifact_artifact_type (artifact_type)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='Agent产物表';
