alter table workflow_run
    add column recovery_attempt int default 0 not null comment 'Recovery scanner attempts' after retry_count;

create table workflow_recovery_audit_event
(
    id                bigint auto_increment primary key,
    workflow_run_uuid varchar(36) not null,
    previous_status   varchar(20) not null,
    new_status        varchar(20) not null,
    reason            varchar(120) not null,
    recovery_attempt  int not null,
    event_id          varchar(36) null,
    trace_id          varchar(64) not null,
    created_at        datetime default CURRENT_TIMESTAMP not null,
    index idx_workflow_recovery_audit_run (workflow_run_uuid, created_at)
) comment 'Durable evidence for workflow recovery scanner decisions';
