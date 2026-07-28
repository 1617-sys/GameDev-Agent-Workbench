create table machine_episode_batch (
    id bigint auto_increment primary key,
    batch_uuid varchar(36) not null,
    user_id bigint not null,
    project_id bigint not null,
    client_batch_key varchar(80) not null,
    idempotency_key varchar(128) not null,
    request_fingerprint varchar(64) not null,
    episode_protocol_version varchar(24) not null,
    status varchar(24) not null,
    total_count int not null,
    completed_count int not null,
    failed_count int not null,
    rejected_count int not null,
    cancelled_count int not null,
    created_at datetime not null default current_timestamp,
    completed_at datetime null,
    constraint uk_machine_episode_batch_uuid unique (batch_uuid),
    constraint uk_machine_episode_batch_idempotency unique (user_id, project_id, idempotency_key),
    constraint fk_machine_episode_batch_user foreign key (user_id) references sys_user (id),
    constraint fk_machine_episode_batch_project foreign key (project_id) references game_project (id),
    constraint chk_machine_episode_batch_status check (status in ('SUCCEEDED','PARTIAL_SUCCESS','FAILED')),
    constraint chk_machine_episode_batch_total check (total_count between 1 and 100)
) comment 'Machine-only Episode batch result index';

create table machine_episode (
    id bigint auto_increment primary key,
    episode_uuid varchar(36) not null,
    batch_id bigint not null,
    project_id bigint not null,
    prototype_version_uuid varchar(36) not null,
    client_episode_key varchar(80) not null,
    sample_source varchar(12) not null default 'MACHINE',
    config_digest varchar(64) not null,
    simulation_protocol_version varchar(24) not null,
    core_version varchar(80) not null,
    seed bigint unsigned not null,
    max_steps int not null,
    observation_policy_json varchar(512) not null,
    policy_id varchar(80) not null,
    policy_version varchar(40) not null,
    policy_digest varchar(64) not null,
    persona_id varchar(80) not null,
    persona_version varchar(40) not null,
    persona_digest varchar(64) not null,
    metric_version varchar(40) not null,
    execution_status varchar(16) not null,
    termination_reason varchar(24) null,
    outcome varchar(16) null,
    step_count int not null,
    accepted_action_count int not null,
    invalid_action_count int not null,
    final_state_hash varchar(64) null,
    final_score int null,
    trajectory_digest varchar(64) null,
    trajectory_ref varchar(255) null,
    wall_duration_ms bigint null,
    created_at datetime not null default current_timestamp,
    completed_at datetime null,
    constraint uk_machine_episode_uuid unique (episode_uuid),
    constraint uk_machine_episode_client_key unique (batch_id, client_episode_key),
    constraint fk_machine_episode_batch foreign key (batch_id) references machine_episode_batch (id),
    constraint fk_machine_episode_project foreign key (project_id) references game_project (id),
    constraint fk_machine_episode_version foreign key (prototype_version_uuid) references prototype_version (version_uuid),
    constraint chk_machine_episode_source check (sample_source = 'MACHINE'),
    constraint chk_machine_episode_status check (execution_status in ('COMPLETED','FAILED','REJECTED','CANCELLED')),
    constraint chk_machine_episode_reason check (termination_reason is null or termination_reason in ('WON','HEALTH_DEPLETED','TIME_EXPIRED','MAX_STEPS','ERROR')),
    constraint chk_machine_episode_steps check (max_steps between 1 and 1000000 and step_count >= 0)
) comment 'Machine Episode result bound to immutable PrototypeVersion';

create index idx_machine_episode_project_version on machine_episode (project_id, prototype_version_uuid, execution_status);
create index idx_machine_episode_batch on machine_episode (batch_id, id);

create table machine_episode_step (
    id bigint auto_increment primary key,
    episode_id bigint not null,
    sequence_number int not null,
    attempt_number int not null,
    simulation_step_before int not null,
    simulation_step_after int not null,
    observation_digest varchar(64) not null,
    requested_action_json varchar(1024) not null,
    transition_json mediumtext not null,
    step_json mediumtext not null,
    reward_value_micros bigint not null,
    constraint uk_machine_episode_step_sequence unique (episode_id, sequence_number),
    constraint fk_machine_episode_step_episode foreign key (episode_id) references machine_episode (id),
    constraint chk_machine_episode_step_sequence check (sequence_number > 0)
) comment 'Immutable raw machine Episode transition evidence';

create trigger trg_machine_episode_step_prevent_update
before update on machine_episode_step
for each row
signal sqlstate '45000' set message_text = 'machine_episode_step is immutable';

create trigger trg_machine_episode_step_prevent_delete
before delete on machine_episode_step
for each row
signal sqlstate '45000' set message_text = 'machine_episode_step cannot be deleted';
