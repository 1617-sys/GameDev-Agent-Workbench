create table playtest_session (
    id bigint auto_increment primary key,
    session_uuid varchar(36) not null,
    user_id bigint not null,
    project_id bigint not null,
    prototype_version_uuid varchar(36) not null,
    status varchar(10) not null default 'ACTIVE',
    started_at datetime not null default current_timestamp,
    ended_at datetime null,
    close_after datetime null,
    last_sequence int not null default 0,
    event_count int not null default 0,
    outcome varchar(10) not null default 'NONE',
    failure_reason varchar(24) null,
    score int not null default 0,
    duration_ms int not null default 0,
    hit_count int not null default 0,
    collected_count int not null default 0,
    restart_count int not null default 0,
    constraint uk_playtest_session_uuid unique (session_uuid),
    constraint fk_playtest_session_user foreign key (user_id) references sys_user (id),
    constraint fk_playtest_session_project foreign key (project_id) references game_project (id),
    constraint fk_playtest_session_version foreign key (prototype_version_uuid) references prototype_version (version_uuid),
    constraint chk_playtest_session_status check (status in ('ACTIVE','ENDED')),
    constraint chk_playtest_session_outcome check (outcome in ('WON','LOST','ABANDONED','NONE'))
) comment 'Authenticated playtest session bound to one immutable prototype';

create index idx_playtest_session_version on playtest_session (prototype_version_uuid, status, ended_at);
create index idx_playtest_session_user_started on playtest_session (user_id, started_at);

create table playtest_event_batch (
    id bigint auto_increment primary key,
    session_id bigint not null,
    batch_uuid varchar(36) not null,
    batch_digest varchar(64) not null,
    accepted_count int not null,
    created_at datetime not null default current_timestamp,
    constraint uk_playtest_batch unique (session_id, batch_uuid),
    constraint fk_playtest_batch_session foreign key (session_id) references playtest_session (id)
) comment 'Idempotent telemetry ingestion batch';

create table playtest_event (
    id bigint auto_increment primary key,
    event_uuid varchar(36) not null,
    session_id bigint not null,
    sequence_number int not null,
    event_type varchar(24) not null,
    client_elapsed_ms int not null,
    payload_json varchar(256) not null,
    event_digest varchar(64) not null,
    received_at datetime not null default current_timestamp,
    constraint uk_playtest_event_uuid unique (event_uuid),
    constraint uk_playtest_event_sequence unique (session_id, sequence_number),
    constraint fk_playtest_event_session foreign key (session_id) references playtest_session (id),
    constraint chk_playtest_event_sequence check (sequence_number between 1 and 1000)
) comment 'Restricted immutable telemetry fact';

create index idx_playtest_event_session_order on playtest_event (session_id, sequence_number);

create table prototype_playtest_aggregate (
    id bigint auto_increment primary key,
    prototype_version_uuid varchar(36) not null,
    ended_session_count int not null,
    won_count int not null,
    lost_count int not null,
    abandoned_count int not null,
    total_duration_ms bigint not null,
    total_score bigint not null,
    total_hit_count bigint not null,
    total_collected_count bigint not null,
    total_restart_count bigint not null,
    health_depleted_count int not null,
    time_expired_count int not null,
    snapshot_at datetime not null,
    constraint uk_playtest_aggregate_version unique (prototype_version_uuid),
    constraint fk_playtest_aggregate_version foreign key (prototype_version_uuid) references prototype_version (version_uuid)
) comment 'Server-derived per-version playtest aggregate';

create table balance_suggestion_request (
    id bigint auto_increment primary key,
    user_id bigint not null,
    project_id bigint not null,
    prototype_version_uuid varchar(36) not null,
    idempotency_key varchar(128) not null,
    request_fingerprint varchar(64) not null,
    artifact_uuid varchar(36) not null,
    created_at datetime not null default current_timestamp,
    constraint uk_balance_suggestion_idempotency unique (user_id,project_id,idempotency_key),
    constraint fk_balance_suggestion_version foreign key (prototype_version_uuid) references prototype_version(version_uuid),
    constraint fk_balance_suggestion_artifact foreign key (artifact_uuid) references agent_artifact(artifact_uuid)
) comment 'Traceable idempotent AI balance evaluation request';
