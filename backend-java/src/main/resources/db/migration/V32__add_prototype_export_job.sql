create table prototype_export_job (
    id bigint auto_increment primary key,
    job_uuid varchar(36) not null,
    user_id bigint not null,
    project_id bigint not null,
    prototype_version_uuid varchar(36) not null,
    operation varchar(32) not null,
    idempotency_key varchar(128) not null,
    request_fingerprint varchar(64) not null,
    frozen_input_json longtext not null,
    status varchar(16) not null,
    package_name varchar(160) null,
    package_digest varchar(64) null,
    package_size bigint null,
    package_bytes longblob null,
    attempt_count int not null default 0,
    error_code varchar(64) null,
    created_at datetime not null default current_timestamp,
    completed_at datetime null,
    constraint uk_prototype_export_job_uuid unique (job_uuid),
    constraint uk_prototype_export_idempotency unique (user_id,project_id,operation,idempotency_key),
    constraint fk_prototype_export_user foreign key (user_id) references sys_user(id),
    constraint fk_prototype_export_project foreign key (project_id) references game_project(id),
    constraint fk_prototype_export_version foreign key (prototype_version_uuid) references prototype_version(version_uuid),
    constraint chk_prototype_export_status check (status in ('PENDING','COMPLETED','FAILED')),
    constraint chk_prototype_export_attempt check (attempt_count between 0 and 3)
) comment 'Idempotent deterministic prototype package export';

create index idx_prototype_export_project_created on prototype_export_job(project_id,created_at);
