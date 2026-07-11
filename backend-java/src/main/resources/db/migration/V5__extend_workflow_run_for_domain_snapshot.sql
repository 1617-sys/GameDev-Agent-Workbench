alter table workflow_run
    add column workflow_definition_version_id bigint null comment '创建时选用的 workflow_definition_version.id' after workflow_type,
    add column workflow_definition_snapshot json null comment '不可变工作流定义快照' after workflow_definition_version_id,
    add column prompt_version_snapshot json null comment '本次运行各步骤使用的 PromptVersion 标识快照' after workflow_definition_snapshot,
    add column schema_version varchar(40) null comment '本次运行使用的输出 Schema 契约版本' after prompt_version_snapshot,
    add column attempt int null comment 'Workflow 尝试次数，新运行从 1 开始' after schema_version,
    add column status_version bigint null comment '状态乐观锁版本，新运行从 0 开始' after attempt,
    add constraint fk_workflow_run_definition_version
        foreign key (workflow_definition_version_id) references workflow_definition_version (id);

create index idx_workflow_run_definition_version_id
    on workflow_run (workflow_definition_version_id);
