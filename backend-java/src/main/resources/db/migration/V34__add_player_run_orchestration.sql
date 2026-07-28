create table player_run (
    id bigint primary key auto_increment,
    run_uuid varchar(36) not null,
    user_id bigint not null,
    project_id bigint not null,
    project_uuid varchar(64) not null,
    prototype_version_uuid varchar(36) not null,
    idempotency_key varchar(128) not null,
    request_fingerprint char(64) not null,
    client_batch_key varchar(80) not null,
    status varchar(24) not null,
    request_json longtext not null,
    response_json longtext null,
    persisted_batch_uuid varchar(36) null,
    trace_id varchar(64) not null,
    attempt int not null default 0,
    error_code varchar(80) null,
    error_message varchar(255) null,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    completed_at datetime(6) null,
    constraint uk_player_run_uuid unique (run_uuid),
    constraint uk_player_run_idempotency unique (user_id, project_id, idempotency_key),
    constraint fk_player_run_project foreign key (project_id) references game_project(id),
    constraint fk_player_run_version foreign key (prototype_version_uuid) references prototype_version(version_uuid),
    constraint chk_player_run_status check (status in ('PENDING','RUNNING','PERSISTING','SUCCEEDED','PARTIAL_SUCCESS','FAILED'))
);
create index idx_player_run_project_version on player_run(project_id, prototype_version_uuid, created_at);
create index idx_player_run_runnable on player_run(status, updated_at);

alter table machine_episode
    add column model_json text null after persona_digest,
    add column usage_json text null after model_json,
    add column audit_json text null after usage_json,
    add column timing_json text null after audit_json,
    add column error_json text null after timing_json;
