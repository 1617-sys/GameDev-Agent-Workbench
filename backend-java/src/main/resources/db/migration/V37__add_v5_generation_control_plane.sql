create table generation_run (
    id bigint primary key auto_increment,
    run_uuid varchar(36) not null,
    user_id bigint not null,
    project_id bigint not null,
    idempotency_key varchar(128) not null,
    request_fingerprint char(64) not null,
    status varchar(24) not null,
    state_version bigint not null default 0,
    canonical_spec_json mediumtext null,
    source_digest char(64) null,
    runtime_ir_json mediumtext null,
    runtime_ir_digest char(64) null,
    build_request_json text null,
    diagnostics_json text not null,
    package_digest char(64) null,
    error_code varchar(80) null,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    completed_at datetime(6) null,
    constraint uk_generation_run_uuid unique (run_uuid),
    constraint uk_generation_run_idempotency unique (user_id, project_id, idempotency_key),
    constraint fk_generation_run_project foreign key (project_id) references game_project(id),
    constraint chk_generation_run_status check (status in ('VALIDATING','BUILDING','PLAYTESTING','AWAITING_APPROVAL','APPROVED','REJECTED','FAILED','CANCELLED'))
);

create index idx_generation_run_project_created on generation_run(project_id, created_at);
