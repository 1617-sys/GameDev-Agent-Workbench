alter table workflow_run
    add column retry_count int default 0 not null comment '持久化重试次数' after last_activity_at,
    add column last_error_code varchar(80) null comment '最近错误分类' after retry_count,
    add column last_error_message varchar(500) null comment '脱敏最近错误信息' after last_error_code,
    add column next_retry_at datetime null comment '下次重试时间' after last_error_message,
    add column failed_at datetime null comment '最终失败时间' after next_retry_at;

alter table workflow_step_run
    add column retry_count int default 0 not null comment '步骤重试证据' after attempt,
    add column last_error_code varchar(80) null comment '步骤最近错误分类' after retry_count;
