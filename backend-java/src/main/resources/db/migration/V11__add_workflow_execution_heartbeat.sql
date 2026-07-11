alter table workflow_run
    add column heartbeat_at datetime null comment 'Consumer/Runner 最近心跳' after request_fingerprint,
    add column last_activity_at datetime null comment '最近持久化执行活动' after heartbeat_at;

create index idx_workflow_run_execution_recovery on workflow_run (status, heartbeat_at);
