create table prompt_version
(
    id                   bigint auto_increment comment 'Prompt 版本主键'
        primary key,
    version_uuid         varchar(36)                           not null comment '不可变 Prompt 版本 UUID',
    template_id          bigint                                not null comment '来源 prompt_template.id',
    template_uuid        varchar(36)                           not null comment '来源 PromptTemplate UUID 快照',
    agent_type           varchar(50)                           not null comment 'Agent 类型快照',
    version              int                                   not null comment 'PromptVersion 版本号，从 1 开始',
    name                 varchar(100)                          not null comment 'Prompt 名称快照',
    system_prompt        text                                  not null comment '系统提示词快照',
    user_prompt_template text                                  not null comment '用户提示词模板快照',
    output_schema_key    varchar(80)                           null comment '输出 Schema 标识，当前历史模板可为空',
    output_schema_version varchar(20)                          null comment '输出 Schema 版本，当前历史模板可为空',
    model_parameters_json json                                 null comment '模型参数快照',
    status               varchar(20)                           not null comment 'ACTIVE/INACTIVE/ARCHIVED',
    created_by           bigint                                null comment '创建人，V1 回填时为空',
    created_at           datetime    default CURRENT_TIMESTAMP not null comment '创建时间',
    updated_at           datetime    default CURRENT_TIMESTAMP not null comment '创建时间快照，不作为可变更新时间',
    deleted              tinyint     default 0                 not null comment '逻辑删除标记',
    constraint uk_prompt_version_uuid
        unique (version_uuid),
    constraint uk_prompt_version_template_version
        unique (template_id, version, deleted),
    constraint fk_prompt_version_template
        foreign key (template_id) references prompt_template (id)
)
    comment '不可变 Prompt 版本表';

create index idx_prompt_version_template_status
    on prompt_version (template_id, status);

create index idx_prompt_version_agent_type
    on prompt_version (agent_type);

create index idx_prompt_version_schema
    on prompt_version (output_schema_key, output_schema_version);

insert into prompt_version (
    version_uuid,
    template_id,
    template_uuid,
    agent_type,
    version,
    name,
    system_prompt,
    user_prompt_template,
    status,
    created_by,
    created_at,
    updated_at,
    deleted
)
select
    UUID(),
    template.id,
    template.template_uuid,
    template.agent_type,
    1,
    template.name,
    template.system_prompt,
    template.user_prompt_template,
    'ACTIVE',
    null,
    template.created_at,
    template.created_at,
    0
from prompt_template template
where template.status = 'ACTIVE'
  and template.deleted = 0
  and not exists (
      select 1
      from prompt_version existing_version
      where existing_version.template_id = template.id
        and existing_version.version = 1
        and existing_version.deleted = 0
  );

alter table agent_run
    add column prompt_version_id bigint null comment '实际使用的 prompt_version.id' after template_id,
    add constraint fk_agent_run_prompt_version
        foreign key (prompt_version_id) references prompt_version (id);

create index idx_agent_run_prompt_version_id
    on agent_run (prompt_version_id);

create trigger trg_prompt_version_prevent_update
before update on prompt_version
for each row
signal sqlstate '45000'
    set message_text = 'prompt_version is immutable; create a new version instead';

create trigger trg_prompt_version_prevent_delete
before delete on prompt_version
for each row
signal sqlstate '45000'
    set message_text = 'prompt_version is immutable and cannot be deleted';
