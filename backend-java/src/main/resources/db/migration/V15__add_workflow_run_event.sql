alter table workflow_run
    add column event_sequence bigint default 0 not null comment 'Last allocated UI event sequence' after trace_id;

create table workflow_run_event
(
    id                bigint auto_increment primary key,
    event_uuid        varchar(36) not null,
    workflow_run_uuid varchar(36) not null,
    sequence          bigint not null,
    event_type        varchar(80) not null,
    event_key         varchar(160) not null,
    step_key          varchar(100) null,
    status            varchar(20) null,
    attempt           int null,
    artifact_uuid     varchar(36) null,
    payload_json      json not null,
    trace_id          varchar(64) null,
    occurred_at       datetime not null,
    constraint uk_workflow_run_event_uuid unique (event_uuid),
    constraint uk_workflow_run_event_sequence unique (workflow_run_uuid, sequence),
    constraint uk_workflow_run_event_key unique (workflow_run_uuid, event_key),
    index idx_workflow_run_event_replay (workflow_run_uuid, sequence)
) comment 'Durable, redacted workflow progress events; not the workflow state source';
