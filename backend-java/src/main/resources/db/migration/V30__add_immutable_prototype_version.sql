alter table agent_artifact
    modify column agent_run_id bigint null;

create table prototype_version_sequence (
    project_id bigint primary key,
    next_version int not null,
    constraint fk_prototype_version_sequence_project foreign key (project_id) references game_project (id)
) comment 'Per-project serialized prototype version allocator';

create table prototype_version (
    id bigint auto_increment primary key,
    version_uuid varchar(36) not null,
    project_id bigint not null,
    version_number int not null,
    parent_version_uuid varchar(36) null,
    source varchar(20) not null,
    game_config_artifact_uuid varchar(36) not null,
    config_digest varchar(64) not null,
    runtime_capability_version varchar(50) not null,
    created_by bigint not null,
    operation varchar(40) not null,
    idempotency_key varchar(128) not null,
    request_fingerprint varchar(64) not null,
    created_at datetime default current_timestamp not null,
    constraint uk_prototype_version_uuid unique (version_uuid),
    constraint uk_prototype_version_number unique (project_id, version_number),
    constraint uk_prototype_version_idempotency unique (created_by, project_id, operation, idempotency_key),
    constraint uk_prototype_version_artifact unique (game_config_artifact_uuid),
    constraint fk_prototype_version_project foreign key (project_id) references game_project (id),
    constraint fk_prototype_version_parent foreign key (parent_version_uuid) references prototype_version (version_uuid),
    constraint fk_prototype_version_artifact foreign key (game_config_artifact_uuid) references agent_artifact (artifact_uuid),
    constraint chk_prototype_version_number check (version_number > 0),
    constraint chk_prototype_version_source check (source in ('AI_GENERATED', 'TUNED'))
) comment 'Immutable playable prototype snapshot';

create index idx_prototype_version_project_created on prototype_version (project_id, version_number desc);
create index idx_prototype_version_parent on prototype_version (parent_version_uuid);

create trigger trg_prototype_version_prevent_update
before update on prototype_version
for each row
signal sqlstate '45000' set message_text = 'prototype_version is immutable';

create trigger trg_prototype_version_prevent_delete
before delete on prototype_version
for each row
signal sqlstate '45000' set message_text = 'prototype_version cannot be deleted';

create trigger trg_versioned_artifact_freeze
before update on agent_artifact
for each row
set new.artifact_uuid = if(exists(select 1 from prototype_version where game_config_artifact_uuid = old.artifact_uuid), old.artifact_uuid, new.artifact_uuid),
    new.content = if(exists(select 1 from prototype_version where game_config_artifact_uuid = old.artifact_uuid), old.content, new.content),
    new.content_digest = if(exists(select 1 from prototype_version where game_config_artifact_uuid = old.artifact_uuid), old.content_digest, new.content_digest),
    new.schema_key = if(exists(select 1 from prototype_version where game_config_artifact_uuid = old.artifact_uuid), old.schema_key, new.schema_key),
    new.schema_version = if(exists(select 1 from prototype_version where game_config_artifact_uuid = old.artifact_uuid), old.schema_version, new.schema_version),
    new.runtime_capability_version = if(exists(select 1 from prototype_version where game_config_artifact_uuid = old.artifact_uuid), old.runtime_capability_version, new.runtime_capability_version),
    new.deleted = if(exists(select 1 from prototype_version where game_config_artifact_uuid = old.artifact_uuid), old.deleted, new.deleted);
