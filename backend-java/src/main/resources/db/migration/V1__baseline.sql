-- 1. 用户表
create table sys_user
(
    id            bigint auto_increment comment '用户主键 ID，数据库内部使用'
        primary key,
    user_uuid     varchar(36)                           not null comment '用户对外 UUID，接口返回使用',
    username      varchar(50)                           not null comment '用户名，用于登录',
    email         varchar(100)                          null comment '邮箱，MVP 阶段可选',
    password_hash varchar(255)                          not null comment '加密后的密码，禁止存明文密码',
    nickname      varchar(50)                           null comment '用户昵称',
    avatar_url    varchar(500)                          null comment '头像地址',
    status        varchar(20) default 'NORMAL'          not null comment '用户状态：NORMAL 正常，DISABLED 禁用',
    last_login_at datetime                              null comment '最后登录时间',
    created_at    datetime    default CURRENT_TIMESTAMP not null comment '创建时间',
    updated_at    datetime    default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    deleted       tinyint     default 0                 not null comment '逻辑删除：0 未删除，1 已删除',
    constraint uk_sys_user_email
        unique (email),
    constraint uk_sys_user_user_uuid
        unique (user_uuid),
    constraint uk_sys_user_username
        unique (username)
)
    comment '系统用户表';

-- 2. 游戏项目表
create table game_project
(
    id              bigint auto_increment comment '主键ID'
        primary key,
    project_uuid    varchar(36)                           not null comment '项目唯一标识',
    user_id         bigint                                not null comment '所属用户ID',
    name            varchar(100)                          not null comment '项目名称',
    game_type       varchar(50)                           null comment '游戏类型',
    target_platform varchar(50)                           null comment '目标平台',
    description     text                                  null comment '项目描述',
    status          varchar(20) default 'ACTIVE'          not null comment '项目状态：ACTIVE/ARCHIVED',
    created_at      datetime    default CURRENT_TIMESTAMP not null comment '创建时间',
    updated_at      datetime    default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    deleted         tinyint     default 0                 not null comment '逻辑删除：0未删除，1已删除',
    constraint uk_game_project_uuid
        unique (project_uuid),
    constraint fk_game_project_user_id
        foreign key (user_id) references sys_user (id)
)
    comment '游戏项目表';

create index idx_game_project_status
    on game_project (status);

create index idx_game_project_user_id
    on game_project (user_id);

-- 3. Agent 执行记录表
create table agent_run
(
    id               bigint auto_increment comment '执行记录主键 ID，数据库内部使用'
        primary key,
    run_uuid         varchar(36)                           not null comment '执行记录对外 UUID，接口查询使用',
    user_id          bigint                                not null comment '发起执行的用户 ID，关联 sys_user.id',
    project_id       bigint                                null comment '关联项目ID',
    project_uuid     varchar(36)                           null comment '关联项目UUID',
    session_uuid     varchar(36)                           null comment '会话 UUID，MVP 阶段先预留，不强制建 agent_session 表',
    template_id      bigint                                null comment 'Prompt 模板 ID，MVP 阶段先预留',
    agent_type       varchar(50)                           not null comment 'Agent 类型',
    input_content    text                                  not null comment '用户输入内容',
    request_payload  json                                  null comment 'Java 发送给 Python Agent 的完整请求 JSON',
    output_content   json                                  null comment 'Agent 输出内容，保存 Python 返回的结构化 JSON',
    response_payload json                                  null comment 'Python Agent 原始响应 JSON',
    status           varchar(20) default 'RUNNING'         not null comment '执行状态：RUNNING，SUCCESS，FAILED，TIMEOUT',
    error_message    text                                  null comment '错误信息，失败或超时时记录',
    time_taken_ms    bigint                                null comment '执行耗时，单位毫秒',
    duration_ms      int                                   null comment '执行耗时，单位毫秒',
    python_endpoint  varchar(255)                          null comment '实际调用的 Python Agent 接口地址',
    created_at       datetime    default CURRENT_TIMESTAMP not null comment '创建时间',
    updated_at       datetime    default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    deleted          tinyint     default 0                 not null comment '逻辑删除：0 未删除，1 已删除',
    constraint uk_agent_run_run_uuid
        unique (run_uuid),
    constraint fk_agent_run_user_id
        foreign key (user_id) references sys_user (id)
)
    comment 'Agent 执行记录表';

create index idx_agent_run_agent_type
    on agent_run (agent_type);

create index idx_agent_run_project_id
    on agent_run (project_id);

create index idx_agent_run_project_uuid
    on agent_run (project_uuid);

create index idx_agent_run_status
    on agent_run (status);

create index idx_agent_run_user_created_at
    on agent_run (user_id, created_at);

-- 4. Agent 产物表
create table agent_artifact
(
    id            bigint auto_increment comment '产物主键ID'
        primary key,
    artifact_uuid varchar(36)                        not null comment '产物UUID',
    project_id    bigint                             not null comment '关联项目ID',
    agent_run_id  bigint                             not null comment '关联执行记录ID',
    artifact_type varchar(50)                        not null comment '产物类型',
    title         varchar(200)                       not null comment '产物标题',
    content       longtext                           not null comment '产物内容',
    created_at    datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updated_at    datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    deleted       tinyint  default 0                 not null comment '逻辑删除：0未删除，1已删除',
    constraint uk_agent_artifact_uuid
        unique (artifact_uuid)
)
    comment 'Agent产物表';

create index idx_agent_artifact_agent_run_id
    on agent_artifact (agent_run_id);

create index idx_agent_artifact_artifact_type
    on agent_artifact (artifact_type);

create index idx_agent_artifact_project_id
    on agent_artifact (project_id);

-- 5. Workflow 执行记录表
create table workflow_run
(
    id                bigint auto_increment comment '主键ID'
        primary key,
    workflow_run_uuid varchar(36)                           not null comment '工作流执行唯一标识',
    project_id        bigint                                not null comment '关联项目ID',
    user_id           bigint                                not null comment '发起用户ID',
    workflow_type     varchar(50)                           not null comment '工作流类型',
    status            varchar(20) default 'RUNNING'         not null comment '执行状态：RUNNING/SUCCESS/FAILED/TIMEOUT',
    input_content     text                                  not null comment '输入内容',
    summary           text                                  null comment '工作流结果摘要',
    error_message     text                                  null comment '错误信息',
    time_taken_ms     bigint                                null comment '执行耗时，单位毫秒',
    created_at        datetime    default CURRENT_TIMESTAMP not null comment '创建时间',
    updated_at        datetime    default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    deleted           tinyint     default 0                 not null comment '逻辑删除：0未删除，1已删除',
    constraint uk_workflow_run_uuid
        unique (workflow_run_uuid)
)
    comment '工作流执行记录表';

create index idx_workflow_run_project_id
    on workflow_run (project_id);

create index idx_workflow_run_status
    on workflow_run (status);

create index idx_workflow_run_user_id
    on workflow_run (user_id);

create index idx_workflow_run_workflow_type
    on workflow_run (workflow_type);

-- 6. Prompt 模板表
create table prompt_template
(
    id                   bigint auto_increment comment 'Prompt模板主键ID'
        primary key,
    template_uuid        varchar(36)                           not null comment 'Prompt模板唯一标识',
    agent_type           varchar(50)                           not null comment 'Agent类型',
    name                 varchar(100)                          not null comment '模板名称',
    system_prompt        text                                  not null comment '系统提示词',
    user_prompt_template text                                  not null comment '用户提示词模板',
    version              int         default 1                 not null comment '模板版本号',
    status               varchar(20) default 'ACTIVE'          not null comment '模板状态：ACTIVE/INACTIVE',
    created_at           datetime    default CURRENT_TIMESTAMP not null comment '创建时间',
    updated_at           datetime    default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    deleted              tinyint     default 0                 not null comment '逻辑删除：0未删除，1已删除',
    constraint uk_prompt_template_uuid
        unique (template_uuid)
)
    comment 'Prompt模板表';

create index idx_prompt_template_agent_status
    on prompt_template (agent_type, status);

create index idx_prompt_template_agent_type
    on prompt_template (agent_type);

create index idx_prompt_template_status
    on prompt_template (status);
