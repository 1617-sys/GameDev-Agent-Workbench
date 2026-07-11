create table workflow_definition_version
(
    id              bigint auto_increment comment '工作流定义版本主键'
        primary key,
    workflow_key    varchar(80)                           not null comment '稳定工作流标识，例如 GAME_DESIGN',
    version         int                                   not null comment '同一工作流的定义版本号，从 1 开始',
    name            varchar(120)                          not null comment '工作流定义展示名称',
    status          varchar(20) default 'ACTIVE'          not null comment '定义状态：ACTIVE/INACTIVE',
    definition_json json                                  not null comment '步骤拓扑和依赖的不可变快照',
    created_at      datetime    default CURRENT_TIMESTAMP not null comment '创建时间',
    created_by      bigint                                null comment '创建人，系统初始化时为空',
    constraint uk_workflow_definition_version_key_version
        unique (workflow_key, version)
)
    comment '工作流定义版本表';

create index idx_workflow_definition_version_key_status
    on workflow_definition_version (workflow_key, status);

create table workflow_step_definition
(
    id                    bigint auto_increment comment '工作流步骤定义主键'
        primary key,
    definition_version_id bigint                                not null comment '关联 workflow_definition_version.id',
    step_key              varchar(80)                           not null comment '稳定步骤标识',
    step_order            int                                   not null comment '步骤执行和展示顺序',
    agent_type            varchar(50)                           not null comment '与 Java AgentType 一致的 Agent 类型',
    artifact_type         varchar(50)                           not null comment '与 Java ArtifactType 一致的产物类型',
    depends_on_step_key   varchar(80)                           null comment '直接依赖的步骤标识；完整依赖快照见 definition_json',
    prompt_template_key   varchar(80)                           null comment '当前 PromptTemplate 的稳定选择键',
    created_at            datetime    default CURRENT_TIMESTAMP not null comment '创建时间',
    constraint uk_workflow_step_definition_key
        unique (definition_version_id, step_key),
    constraint uk_workflow_step_definition_order
        unique (definition_version_id, step_order),
    constraint fk_workflow_step_definition_version
        foreign key (definition_version_id) references workflow_definition_version (id)
)
    comment '工作流步骤定义表';

create index idx_workflow_step_definition_version_id
    on workflow_step_definition (definition_version_id);

insert into workflow_definition_version (
    workflow_key,
    version,
    name,
    status,
    definition_json,
    created_by
)
values (
    'GAME_DESIGN',
    1,
    'Game Design default workflow',
    'ACTIVE',
    JSON_OBJECT(
        'workflowKey', 'GAME_DESIGN',
        'version', 1,
        'steps', JSON_ARRAY(
            JSON_OBJECT('stepKey', 'game_concept', 'stepOrder', 1, 'agentType', 'GAME_CONCEPT',
                'artifactType', 'GAME_CONCEPT_RESULT', 'dependsOn', JSON_ARRAY()),
            JSON_OBJECT('stepKey', 'core_loop_design', 'stepOrder', 2, 'agentType', 'CORE_LOOP_DESIGN',
                'artifactType', 'CORE_LOOP_DESIGN_RESULT', 'dependsOn', JSON_ARRAY('game_concept')),
            JSON_OBJECT('stepKey', 'task_breakdown', 'stepOrder', 3, 'agentType', 'TASK_BREAKDOWN',
                'artifactType', 'TASK_BREAKDOWN_RESULT', 'dependsOn', JSON_ARRAY('game_concept', 'core_loop_design'))
        )
    ),
    null
), (
    'DEMO_GAME_CONFIG',
    1,
    'Demo game configuration default workflow',
    'ACTIVE',
    JSON_OBJECT(
        'workflowKey', 'DEMO_GAME_CONFIG',
        'version', 1,
        'steps', JSON_ARRAY(
            JSON_OBJECT('stepKey', 'game_config_generate', 'stepOrder', 1, 'agentType', 'GAME_CONFIG_GENERATE',
                'artifactType', 'GAME_CONFIG', 'dependsOn', JSON_ARRAY())
        )
    ),
    null
);

insert into workflow_step_definition (
    definition_version_id,
    step_key,
    step_order,
    agent_type,
    artifact_type,
    depends_on_step_key,
    prompt_template_key
)
select id, 'game_concept', 1, 'GAME_CONCEPT', 'GAME_CONCEPT_RESULT', null, 'GAME_CONCEPT'
from workflow_definition_version
where workflow_key = 'GAME_DESIGN' and version = 1;

insert into workflow_step_definition (
    definition_version_id,
    step_key,
    step_order,
    agent_type,
    artifact_type,
    depends_on_step_key,
    prompt_template_key
)
select id, 'core_loop_design', 2, 'CORE_LOOP_DESIGN', 'CORE_LOOP_DESIGN_RESULT', 'game_concept', 'CORE_LOOP_DESIGN'
from workflow_definition_version
where workflow_key = 'GAME_DESIGN' and version = 1;

insert into workflow_step_definition (
    definition_version_id,
    step_key,
    step_order,
    agent_type,
    artifact_type,
    depends_on_step_key,
    prompt_template_key
)
select id, 'task_breakdown', 3, 'TASK_BREAKDOWN', 'TASK_BREAKDOWN_RESULT', 'core_loop_design', 'TASK_BREAKDOWN'
from workflow_definition_version
where workflow_key = 'GAME_DESIGN' and version = 1;

insert into workflow_step_definition (
    definition_version_id,
    step_key,
    step_order,
    agent_type,
    artifact_type,
    depends_on_step_key,
    prompt_template_key
)
select id, 'game_config_generate', 1, 'GAME_CONFIG_GENERATE', 'GAME_CONFIG', null, 'GAME_CONFIG_GENERATE'
from workflow_definition_version
where workflow_key = 'DEMO_GAME_CONFIG' and version = 1;
