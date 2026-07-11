create table workflow_step_run
(
    id                    bigint auto_increment comment '步骤运行主键'
        primary key,
    step_run_uuid         varchar(36)                           not null comment '步骤运行稳定追踪 UUID',
    workflow_run_id       bigint                                not null comment '关联 workflow_run.id',
    workflow_run_uuid     varchar(36)                           not null comment '关联 workflow_run.workflow_run_uuid',
    definition_version_id bigint                                null comment '创建时选用的 workflow_definition_version.id',
    step_key              varchar(80)                           not null comment '稳定步骤标识',
    step_order            int                                   not null comment '步骤顺序',
    agent_type            varchar(50)                           not null comment '对应 AgentType',
    artifact_type         varchar(50)                           not null comment '对应 ArtifactType',
    status                varchar(20)                           not null comment 'PENDING/RUNNING/SUCCESS/FAILED/TIMEOUT/CANCELED/SKIPPED',
    attempt               int         default 1                 not null comment '步骤尝试次数，从 1 开始',
    input_snapshot        longtext                              null comment '步骤输入快照',
    context_snapshot      longtext                              null comment '步骤上下文快照',
    output_snapshot       longtext                              null comment '步骤输出快照',
    error_message         text                                  null comment '脱敏错误信息',
    started_at            datetime                              null comment '开始执行时间',
    finished_at           datetime                              null comment '结束执行时间',
    time_taken_ms         bigint                                null comment '步骤耗时，单位毫秒',
    created_at            datetime    default CURRENT_TIMESTAMP not null comment '创建时间',
    updated_at            datetime    default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    constraint uk_workflow_step_run_uuid
        unique (step_run_uuid),
    constraint uk_workflow_step_run_attempt
        unique (workflow_run_id, step_key, attempt),
    constraint fk_workflow_step_run_workflow
        foreign key (workflow_run_id) references workflow_run (id),
    constraint fk_workflow_step_run_definition_version
        foreign key (definition_version_id) references workflow_definition_version (id)
)
    comment '工作流步骤运行表';

create index idx_workflow_step_run_workflow_uuid
    on workflow_step_run (workflow_run_uuid);

create index idx_workflow_step_run_workflow_status
    on workflow_step_run (workflow_run_id, status);

alter table agent_artifact
    add column step_run_id bigint null comment '来源 workflow_step_run.id' after agent_run_id,
    add constraint fk_agent_artifact_step_run
        foreign key (step_run_id) references workflow_step_run (id);

create index idx_agent_artifact_step_run_id
    on agent_artifact (step_run_id);
