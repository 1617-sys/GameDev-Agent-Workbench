alter table generation_run drop check chk_generation_run_status;

alter table generation_run
    add column build_claim_token varchar(36) null after state_version,
    add column build_claim_expires_at datetime(6) null after build_claim_token,
    add column build_attempt int not null default 0 after build_claim_expires_at;

update generation_run
set status = 'READY_TO_BUILD'
where status = 'BUILDING' and package_digest is null;

alter table generation_run
    add constraint chk_generation_run_status check (status in (
        'VALIDATING','READY_TO_BUILD','BUILDING','PLAYTESTING','AWAITING_APPROVAL',
        'APPROVED','RELEASED','REJECTED','FAILED','CANCELLED'
    ));

create table generation_run_approval (
    id bigint primary key auto_increment,
    approval_uuid varchar(36) not null,
    generation_run_id bigint not null,
    generation_run_uuid varchar(36) not null,
    user_id bigint not null,
    project_id bigint not null,
    actor_user_id bigint not null,
    decision varchar(16) not null,
    reason varchar(500) not null,
    idempotency_key varchar(128) not null,
    request_fingerprint char(64) not null,
    created_at datetime(6) not null,
    constraint uk_generation_approval_uuid unique (approval_uuid),
    constraint uk_generation_approval_run unique (generation_run_id),
    constraint uk_generation_approval_idempotency unique (user_id, project_id, idempotency_key),
    constraint fk_generation_approval_run foreign key (generation_run_id) references generation_run(id),
    constraint fk_generation_approval_project foreign key (project_id) references game_project(id),
    constraint chk_generation_approval_decision check (decision in ('APPROVED','REJECTED'))
);

create index idx_generation_approval_project_created
    on generation_run_approval(project_id, created_at);
