ALTER TABLE agent_run
    ADD COLUMN project_id BIGINT DEFAULT NULL COMMENT '关联项目ID' AFTER user_id,
    ADD COLUMN project_uuid VARCHAR(36) DEFAULT NULL COMMENT '关联项目UUID' AFTER project_id;

ALTER TABLE agent_run
    ADD KEY idx_agent_run_project_id (project_id),
    ADD KEY idx_agent_run_project_uuid (project_uuid);
