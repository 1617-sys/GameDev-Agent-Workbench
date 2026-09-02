create table prompt_template_audit (
    id bigint auto_increment primary key,
    audit_uuid varchar(36) not null,
    template_uuid varchar(36) not null,
    actor_user_id bigint not null,
    operation varchar(32) not null,
    before_json longtext null,
    after_json longtext not null,
    created_at datetime not null default current_timestamp,
    constraint uk_prompt_template_audit_uuid unique (audit_uuid),
    key idx_prompt_template_audit_template (template_uuid, created_at),
    key idx_prompt_template_audit_actor (actor_user_id, created_at)
) comment 'Immutable prompt template change audit';
