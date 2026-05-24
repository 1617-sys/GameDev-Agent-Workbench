CREATE TABLE IF NOT EXISTS workflow_run (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    workflow_run_uuid VARCHAR(36) NOT NULL COMMENT '工作流执行唯一标识',
    project_id BIGINT NOT NULL COMMENT '关联项目ID',
    user_id BIGINT NOT NULL COMMENT '发起用户ID',
    workflow_type VARCHAR(50) NOT NULL COMMENT '工作流类型',
    status VARCHAR(20) NOT NULL DEFAULT 'RUNNING' COMMENT '执行状态：RUNNING/SUCCESS/FAILED/TIMEOUT',
    input_content TEXT NOT NULL COMMENT '输入内容',
    summary TEXT DEFAULT NULL COMMENT '工作流结果摘要',
    error_message TEXT DEFAULT NULL COMMENT '错误信息',
    time_taken_ms BIGINT DEFAULT NULL COMMENT '执行耗时，单位毫秒',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除，1已删除',

    PRIMARY KEY (id),
    UNIQUE KEY uk_workflow_run_uuid (workflow_run_uuid),
    KEY idx_workflow_run_project_id (project_id),
    KEY idx_workflow_run_user_id (user_id),
    KEY idx_workflow_run_workflow_type (workflow_type),
    KEY idx_workflow_run_status (status)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='工作流执行记录表';
