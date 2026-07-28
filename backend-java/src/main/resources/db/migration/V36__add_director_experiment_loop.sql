alter table director_run
    add column trace_id varchar(64) null after error_code,
    add column claim_token varchar(64) null after trace_id,
    add column claim_until datetime(6) null after claim_token,
    add column execution_attempt int not null default 0 after claim_until;
create index idx_director_run_recovery on director_run(status, claim_until, updated_at);

create table director_run_event (
    id bigint primary key auto_increment,
    event_uuid varchar(36) not null,
    director_run_id bigint not null,
    project_id bigint not null,
    event_type varchar(50) not null,
    state_version bigint not null,
    trace_id varchar(64) not null,
    detail_summary varchar(1000) null,
    created_at datetime(6) not null,
    constraint uk_director_run_event_uuid unique (event_uuid),
    constraint fk_director_run_event_run foreign key (director_run_id) references director_run(id),
    constraint fk_director_run_event_project foreign key (project_id) references game_project(id)
);
create index idx_director_run_event_run on director_run_event(director_run_id, id);

alter table prototype_version
    add column lifecycle_status varchar(20) not null default 'APPROVED' after source,
    add column director_run_uuid varchar(36) null after lifecycle_status,
    add column approval_updated_at datetime(6) null after director_run_uuid;
alter table prototype_version
    add constraint chk_prototype_lifecycle check (lifecycle_status in ('DRAFT','APPROVED','REJECTED')),
    add constraint fk_prototype_director_run foreign key (director_run_uuid) references director_run(run_uuid);
create index idx_prototype_lifecycle on prototype_version(project_id, lifecycle_status, version_number);

drop trigger trg_prototype_version_prevent_update;
create trigger trg_prototype_version_prevent_update
before update on prototype_version
for each row
set new.version_uuid = old.version_uuid,
    new.project_id = old.project_id,
    new.version_number = old.version_number,
    new.parent_version_uuid = old.parent_version_uuid,
    new.source = old.source,
    new.game_config_artifact_uuid = old.game_config_artifact_uuid,
    new.config_digest = old.config_digest,
    new.runtime_capability_version = old.runtime_capability_version,
    new.created_by = old.created_by,
    new.operation = old.operation,
    new.idempotency_key = old.idempotency_key,
    new.request_fingerprint = old.request_fingerprint,
    new.created_at = old.created_at;

create table prototype_approval (
    id bigint primary key auto_increment,
    approval_uuid varchar(36) not null,
    project_id bigint not null,
    prototype_version_uuid varchar(36) not null,
    director_run_uuid varchar(36) not null,
    actor_user_id bigint not null,
    actor_type varchar(20) not null,
    decision varchar(20) not null,
    reason varchar(500) not null,
    idempotency_key varchar(128) not null,
    request_fingerprint char(64) not null,
    created_at datetime(6) not null,
    constraint uk_prototype_approval_uuid unique (approval_uuid),
    constraint uk_prototype_approval_version unique (prototype_version_uuid),
    constraint uk_prototype_approval_idempotency unique (actor_user_id, project_id, idempotency_key),
    constraint fk_prototype_approval_project foreign key (project_id) references game_project(id),
    constraint fk_prototype_approval_version foreign key (prototype_version_uuid) references prototype_version(version_uuid),
    constraint fk_prototype_approval_run foreign key (director_run_uuid) references director_run(run_uuid),
    constraint chk_prototype_approval_actor check (actor_type = 'USER'),
    constraint chk_prototype_approval_decision check (decision in ('APPROVED','REJECTED'))
);

alter table experiment_candidate
    add column generator_version varchar(40) null after status,
    add column input_digest char(64) null after generator_version,
    add column tuning_json varchar(2000) null after input_digest;

create table director_experiment_run (
    id bigint primary key auto_increment,
    experiment_uuid varchar(36) not null,
    director_run_id bigint not null,
    project_id bigint not null,
    baseline_version_uuid varchar(36) not null,
    candidate_version_uuid varchar(36) not null,
    baseline_player_run_uuid varchar(36) not null,
    candidate_player_run_uuid varchar(36) not null,
    idempotency_key varchar(128) not null,
    request_fingerprint char(64) not null,
    status varchar(24) not null,
    created_at datetime(6) not null,
    completed_at datetime(6) null,
    constraint uk_director_experiment_uuid unique (experiment_uuid),
    constraint uk_director_experiment_key unique (director_run_id, idempotency_key),
    constraint fk_director_experiment_run foreign key (director_run_id) references director_run(id),
    constraint fk_director_experiment_project foreign key (project_id) references game_project(id),
    constraint fk_director_experiment_baseline foreign key (baseline_version_uuid) references prototype_version(version_uuid),
    constraint fk_director_experiment_candidate foreign key (candidate_version_uuid) references prototype_version(version_uuid),
    constraint fk_director_experiment_baseline_player foreign key (baseline_player_run_uuid) references player_run(run_uuid),
    constraint fk_director_experiment_candidate_player foreign key (candidate_player_run_uuid) references player_run(run_uuid)
);
create index idx_director_experiment_player on director_experiment_run(baseline_player_run_uuid, candidate_player_run_uuid);

create table experiment_comparison (
    id bigint primary key auto_increment,
    comparison_uuid varchar(36) not null,
    director_run_id bigint not null,
    project_id bigint not null,
    baseline_version_uuid varchar(36) not null,
    candidate_version_uuid varchar(36) not null,
    metric_version varchar(50) not null,
    sample_window_json varchar(2000) not null,
    episode_refs_json text not null,
    result_json text not null,
    comparison_digest char(64) not null,
    comparable boolean not null,
    recommended boolean not null,
    created_at datetime(6) not null,
    constraint uk_experiment_comparison_uuid unique (comparison_uuid),
    constraint fk_experiment_comparison_run foreign key (director_run_id) references director_run(id),
    constraint fk_experiment_comparison_project foreign key (project_id) references game_project(id),
    constraint fk_experiment_comparison_baseline foreign key (baseline_version_uuid) references prototype_version(version_uuid),
    constraint fk_experiment_comparison_candidate foreign key (candidate_version_uuid) references prototype_version(version_uuid)
);
