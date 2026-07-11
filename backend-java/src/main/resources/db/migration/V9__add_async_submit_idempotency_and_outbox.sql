alter table workflow_run
    add column idempotency_key varchar(128) null comment '异步提交幂等键' after status_version,
    add column request_fingerprint char(64) null comment '规范化异步请求 SHA-256 摘要' after idempotency_key;

create unique index uk_workflow_run_async_idempotency
    on workflow_run (user_id, project_id, workflow_type, idempotency_key);

create table outbox_event
(
    id                bigint auto_increment comment '主键ID' primary key,
    event_uuid        varchar(36)                           not null comment '稳定事件 UUID',
    aggregate_type    varchar(50)                           not null comment '聚合类型',
    aggregate_uuid    varchar(36)                           not null comment '聚合 UUID',
    workflow_run_id   bigint                                not null comment '关联 workflow_run.id',
    workflow_run_uuid varchar(36)                           not null comment '关联 workflow_run UUID',
    event_type        varchar(80)                           not null comment '事件类型',
    payload_json      json                                  not null comment '版本化消息载荷',
    schema_version    varchar(40)                           not null comment '消息契约版本',
    status            varchar(20) default 'PENDING'         not null comment 'PENDING/PUBLISHING/PUBLISHED',
    publish_attempt   int         default 0                 not null comment '发布尝试次数',
    next_attempt_at   datetime                              null comment '下次可发布时间',
    trace_id          varchar(64)                           not null comment '链路追踪标识',
    created_at        datetime    default CURRENT_TIMESTAMP not null comment '创建时间',
    updated_at        datetime    default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    constraint uk_outbox_event_uuid unique (event_uuid),
    constraint fk_outbox_event_workflow_run foreign key (workflow_run_id) references workflow_run (id)
)
    comment '事务 Outbox 事件；本任务仅创建，不发布';

create index idx_outbox_event_publishable on outbox_event (status, next_attempt_at);
create index idx_outbox_event_workflow_run_uuid on outbox_event (workflow_run_uuid);
