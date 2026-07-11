# R1 Workflow 数据与领域地基设计

> 状态：`DRAFT_READY_FOR_IMPLEMENTATION`
>
> 来源任务：`R1-00-workflow-domain-rfc.md`
>
> 阶段边界：R1 只建立可迁移、可追踪、可版本化的数据与领域地基；不改完整执行模型。

## 背景

R0 已经提供可运行 Baseline：

- Quick Harness 可验证 Java、Python、Vue build 和 Docker Compose config。
- Demo Workflow 的 Redis 锁已修复并有 owner-aware release 测试保护。
- `WorkflowServiceImpl` 的同步三步行为已有状态测试保护。
- GameConfig 提取、校验、归一化已有契约测试。
- 基础安全配置已清理：弱默认 secret、重复 Redis Starter、HTTP Basic 旁路等问题已被测试和报告覆盖。

当前 Workflow 仍然是同步串行实现：

```text
WorkflowServiceImpl.run
-> 校验 user/project
-> 插入 RUNNING workflow_run
-> GAME_CONCEPT
-> CORE_LOOP_DESIGN
-> TASK_BREAKDOWN
-> 更新 SUCCESS / FAILED
```

这个实现可以作为 R1 兼容对象，但不足以支撑后续 R2/R3：

- 没有 StepRun，无法追踪每一步状态、输入、输出、attempt、错误分类。
- WorkflowRun 没有 definition/prompt/input snapshot，历史运行不可复现。
- PromptTemplate 当前可变，ACTIVE 切换会让历史语义变得模糊。
- 现有 SQL 是初始化脚本和零散 alter，缺少 Flyway 增量迁移顺序。
- `seed_game_config_prompt_template.sql` 仍提示 `collectibles` / `winCondition`，与 R0 GameConfig 契约中的 `items` / `rules` 不一致，R1 迁移 PromptVersion 时必须处理。

## 目标

R1 冻结下面这些契约，供后续子任务按顺序实现：

1. 新增表、字段、索引和唯一约束草案。
2. 旧表兼容策略和历史数据升级路径。
3. `WorkflowRun` 与 `WorkflowStepRun` 状态机。
4. `PromptVersion` 不可变规则。
5. WorkflowRun 创建时的 definition、prompt、input、schema snapshot 规则。
6. Flyway baseline 与迁移顺序。
7. R1 子任务依赖图与每个子任务验证命令。

## 范围

R1 允许：

- 引入 Flyway 并建立现有库 baseline。
- 新增 `workflow_definition_version`、`workflow_step_run`、`prompt_version` 等领域表。
- 扩展 `workflow_run`、`agent_run`、`agent_artifact`，但先保持可空和兼容。
- 新增状态枚举与状态转换策略。
- 为现有 PromptTemplate 生成不可变 version 1。
- 为已有 WorkflowRun/AgentRun/Artifact 提供兼容查询和渐进迁移路径。

R1 不允许：

- 不抽取或替换 `WorkflowRunner`。
- 不接 RabbitMQ、Outbox、DLQ。
- 不让提交接口异步返回 `202`。
- 不删除旧表、旧字段、旧 Entity、旧 API。
- 不改前端页面。
- 不实现 RAG、评测引擎、Dashboard 或模型指标完整闭环。

## 现有行为

### WorkflowRun

当前实体字段：

```text
id
workflowRunUuid
projectId
userId
workflowType
status
inputContent
summary
errorMessage
timeTakenMs
createdAt
updatedAt
deleted
```

当前状态来自 `AgentRunStatus`，R0 测试固定了：

| 场景 | 初始 | 最终 | 异常语义 |
| --- | --- | --- | --- |
| 三步成功 | `RUNNING` | `SUCCESS` | 返回 `WorkflowRunVO` |
| BusinessException | `RUNNING` | `FAILED` | 原异常继续向上抛出 |
| 未知异常 | `RUNNING` | `FAILED` | 对外转换为 `SYSTEM_ERROR` |
| 未认证/无项目权限 | 不创建 WorkflowRun | 拒绝 | 抛出业务异常 |

### AgentRun 与 Artifact

当前 `AgentRun` 与 `AgentArtifact` 只有粗粒度关联：

```text
agent_artifact.agent_run_id -> agent_run.id
agent_run.project_id / project_uuid
```

缺口：

- Artifact 无法直接关联 WorkflowRun 或 StepRun。
- AgentRun 无法冻结 PromptVersion。
- 无 schema version、content hash、attempt、mock 标识和模型使用量。

### PromptTemplate

当前 `prompt_template` 包含：

```text
template_uuid
agent_type
name
system_prompt
user_prompt_template
version
status
```

缺口：

- `version` 是模板表上的可变字段，不是不可变版本记录。
- ACTIVE 切换和历史运行没有解耦。
- 旧 seed 中 GameConfig 提示词字段与当前 Runtime 契约不一致。

## 目标行为

R1 完成后，即使执行逻辑仍由旧 Service 同步触发，也必须能写入或准备写入下面的数据边界：

```text
WorkflowDefinitionVersion
-> WorkflowRun snapshot
-> WorkflowStepRun plan
-> AgentRun / Artifact 关联 StepRun
-> PromptVersion snapshot
```

R1 的目标不是让旧 Service 立刻完全消费这些字段，而是让 R2 Runner 可以在不再改表结构大方向的前提下接入。

## 表结构草案

字段类型以 MySQL 8 / utf8mb4 为目标，实际迁移时允许按项目已有命名微调，但不得改变业务语义。

### workflow_definition_version

用于冻结某一类 Workflow 的步骤拓扑和默认策略。创建后不可变；新增版本只能插入新行。

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| id | BIGINT | PK, AUTO_INCREMENT | 内部主键 |
| definition_uuid | VARCHAR(36) | NOT NULL | 同一 workflow 定义族 UUID |
| workflow_key | VARCHAR(80) | NOT NULL | 如 `GAME_DESIGN`、`GAME_PROTOTYPE_PIPELINE` |
| version | INT | NOT NULL | 定义版本号，从 1 开始 |
| name | VARCHAR(120) | NOT NULL | 展示名称 |
| description | TEXT | NULL | 说明 |
| step_plan_json | JSON | NOT NULL | 步骤 key、顺序、依赖、agentType、schema key |
| default_policy_json | JSON | NULL | 超时、重试、是否允许并行等默认策略 |
| status | VARCHAR(20) | NOT NULL | `DRAFT` / `ACTIVE` / `INACTIVE` |
| created_by | BIGINT | NULL | 创建人 |
| created_at | DATETIME | NOT NULL | 创建时间 |
| updated_at | DATETIME | NOT NULL | 更新时间 |
| deleted | TINYINT | NOT NULL DEFAULT 0 | 逻辑删除 |

索引与约束：

```sql
UNIQUE KEY uk_wdv_key_version (workflow_key, version, deleted)
KEY idx_wdv_definition_uuid (definition_uuid)
KEY idx_wdv_workflow_key_status (workflow_key, status)
```

ACTIVE 规则：

- 每个 `workflow_key` 同一时间只能有一个 ACTIVE 版本。
- MySQL 对带 `deleted` 的条件唯一约束不足以表达“只有 ACTIVE 唯一”，实现时用事务 + 查询锁或单独 `workflow_definition` 指针表兜底。
- ACTIVE 切换只影响未来创建的 WorkflowRun。

`step_plan_json` 示例：

```json
[
  {
    "stepKey": "game_concept",
    "stepOrder": 1,
    "agentType": "GAME_CONCEPT",
    "dependsOn": [],
    "outputSchemaKey": "game-concept-text",
    "required": true
  },
  {
    "stepKey": "core_loop_design",
    "stepOrder": 2,
    "agentType": "CORE_LOOP_DESIGN",
    "dependsOn": ["game_concept"],
    "outputSchemaKey": "core-loop-text",
    "required": true
  },
  {
    "stepKey": "task_breakdown",
    "stepOrder": 3,
    "agentType": "TASK_BREAKDOWN",
    "dependsOn": ["game_concept", "core_loop_design"],
    "outputSchemaKey": "task-breakdown-text",
    "required": true
  }
]
```

### workflow_run 扩展

R1 只新增字段，不删除旧字段。旧字段继续服务旧接口和 R0 测试。

| 新字段 | 类型 | 初始约束 | 说明 |
| --- | --- | --- | --- |
| definition_version_id | BIGINT | NULL | 指向 `workflow_definition_version.id` |
| definition_snapshot_json | JSON | NULL | 本次运行冻结的步骤定义 |
| idempotency_key | VARCHAR(128) | NULL | R3 提交幂等键，R1 只预留 |
| input_snapshot_json | JSON | NULL | 用户输入、项目 UUID、标题、上下文等快照 |
| current_step_key | VARCHAR(80) | NULL | 当前或最后处理步骤 |
| attempt | INT | NOT NULL DEFAULT 1 | Workflow 级 attempt |
| trace_id | VARCHAR(64) | NULL | 日志追踪 |
| version | BIGINT | NOT NULL DEFAULT 0 | 乐观锁版本 |
| queued_at | DATETIME | NULL | R3 使用 |
| started_at | DATETIME | NULL | 开始执行时间 |
| finished_at | DATETIME | NULL | 终止时间 |
| cancel_requested | TINYINT | NOT NULL DEFAULT 0 | 协作式取消请求 |

索引与约束：

```sql
KEY idx_workflow_run_definition_version_id (definition_version_id)
KEY idx_workflow_run_user_project_status (user_id, project_id, status)
KEY idx_workflow_run_trace_id (trace_id)
KEY idx_workflow_run_current_step_key (current_step_key)
UNIQUE KEY uk_workflow_run_idempotency (user_id, project_id, workflow_type, idempotency_key, deleted)
```

兼容规则：

- `definition_version_id` 初期允许 NULL；旧 WorkflowRun 不强制回填。
- `input_content` 保留，`input_snapshot_json` 作为更完整快照。
- `time_taken_ms` 保留，R1 新增 `started_at/finished_at` 后由后续服务统一计算。
- `RUNNING/SUCCESS/FAILED/TIMEOUT` 旧状态继续识别；新增状态的引入必须先更新状态策略和测试。

### workflow_step_run

追踪 Workflow 每一步的独立生命周期。R1 可先建表并在后续子任务接入旧 Service 或 R2 Runner。

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| id | BIGINT | PK, AUTO_INCREMENT | 内部主键 |
| step_run_uuid | VARCHAR(36) | NOT NULL, UNIQUE | 对外 UUID |
| workflow_run_id | BIGINT | NOT NULL | 关联 WorkflowRun |
| workflow_run_uuid | VARCHAR(36) | NOT NULL | 冗余便于日志和查询 |
| step_key | VARCHAR(80) | NOT NULL | 稳定步骤 key |
| step_order | INT | NOT NULL | 展示和默认执行顺序 |
| agent_type | VARCHAR(50) | NOT NULL | 对应 AgentType |
| status | VARCHAR(20) | NOT NULL | Step 状态 |
| attempt | INT | NOT NULL DEFAULT 1 | Step attempt |
| depends_on_json | JSON | NULL | 依赖 stepKey 快照 |
| input_snapshot_json | JSON | NULL | 本步骤输入快照 |
| context_snapshot_json | JSON | NULL | 前置输出/RAG/上下文快照 |
| output_summary | TEXT | NULL | 简短摘要 |
| error_code | VARCHAR(80) | NULL | 业务错误码或分类 |
| error_message | TEXT | NULL | 脱敏错误信息 |
| started_at | DATETIME | NULL | 开始时间 |
| finished_at | DATETIME | NULL | 结束时间 |
| time_taken_ms | BIGINT | NULL | 耗时 |
| version | BIGINT | NOT NULL DEFAULT 0 | 乐观锁版本 |
| created_at | DATETIME | NOT NULL | 创建时间 |
| updated_at | DATETIME | NOT NULL | 更新时间 |
| deleted | TINYINT | NOT NULL DEFAULT 0 | 逻辑删除 |

索引与约束：

```sql
UNIQUE KEY uk_step_run_uuid (step_run_uuid)
UNIQUE KEY uk_step_run_attempt (workflow_run_id, step_key, attempt, deleted)
KEY idx_step_run_workflow_status (workflow_run_id, status)
KEY idx_step_run_workflow_order (workflow_run_id, step_order)
KEY idx_step_run_agent_type (agent_type)
```

### prompt_version

`PromptVersion` 是不可变 prompt 快照。历史 WorkflowRun/AgentRun 必须能追溯到创建时使用的完整 prompt。

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| id | BIGINT | PK, AUTO_INCREMENT | 内部主键 |
| version_uuid | VARCHAR(36) | NOT NULL, UNIQUE | 版本 UUID |
| template_id | BIGINT | NOT NULL | 关联现有 `prompt_template.id` |
| template_uuid | VARCHAR(36) | NOT NULL | 冗余外部 UUID |
| agent_type | VARCHAR(50) | NOT NULL | Agent 类型 |
| version | INT | NOT NULL | 模板内版本号 |
| name | VARCHAR(100) | NOT NULL | 版本名称 |
| system_prompt | TEXT | NOT NULL | 系统提示词快照 |
| user_prompt_template | TEXT | NOT NULL | 用户提示词模板快照 |
| output_schema_key | VARCHAR(80) | NULL | 如 `game-config` |
| output_schema_version | VARCHAR(20) | NULL | 如 `1.0` |
| model_parameters_json | JSON | NULL | temperature、max tokens 等 |
| status | VARCHAR(20) | NOT NULL | `ACTIVE` / `INACTIVE` / `ARCHIVED` |
| created_by | BIGINT | NULL | 创建人 |
| created_at | DATETIME | NOT NULL | 创建时间 |
| updated_at | DATETIME | NOT NULL | 更新时间，仅状态/指针同步时使用 |
| deleted | TINYINT | NOT NULL DEFAULT 0 | 逻辑删除 |

索引与约束：

```sql
UNIQUE KEY uk_prompt_version_template_version (template_id, version, deleted)
KEY idx_prompt_version_template_status (template_id, status)
KEY idx_prompt_version_agent_type (agent_type)
KEY idx_prompt_version_schema (output_schema_key, output_schema_version)
```

不可变规则：

- `system_prompt`、`user_prompt_template`、`output_schema_key`、`output_schema_version`、`model_parameters_json` 创建后不可更新。
- 修改 Prompt 只能插入新 `prompt_version`。
- ACTIVE 切换只改变未来运行选择；已创建的 WorkflowRun/AgentRun 不受影响。
- 如需强制修复错误 Prompt，只能归档旧版本并创建新版本，不能原地改历史内容。

### agent_run 扩展

| 新字段 | 类型 | 初始约束 | 说明 |
| --- | --- | --- | --- |
| workflow_run_id | BIGINT | NULL | 所属 WorkflowRun |
| step_run_id | BIGINT | NULL | 所属 StepRun |
| prompt_version_id | BIGINT | NULL | 使用的 PromptVersion |
| model_name | VARCHAR(100) | NULL | 模型名 |
| provider | VARCHAR(50) | NULL | LLM Provider |
| mock | TINYINT | NOT NULL DEFAULT 0 | 是否 mock fallback |
| input_tokens | INT | NULL | 输入 token |
| output_tokens | INT | NULL | 输出 token |
| estimated_cost | DECIMAL(12,6) | NULL | 估算成本 |
| trace_id | VARCHAR(64) | NULL | 追踪 ID |

索引：

```sql
KEY idx_agent_run_workflow_run_id (workflow_run_id)
KEY idx_agent_run_step_run_id (step_run_id)
KEY idx_agent_run_prompt_version_id (prompt_version_id)
KEY idx_agent_run_trace_id (trace_id)
```

### agent_artifact 扩展

| 新字段 | 类型 | 初始约束 | 说明 |
| --- | --- | --- | --- |
| workflow_run_id | BIGINT | NULL | 冗余方便查询 |
| step_run_id | BIGINT | NULL | 产物来源步骤 |
| schema_key | VARCHAR(80) | NULL | 如 `game-config` |
| schema_version | VARCHAR(20) | NULL | 如 `1.0` |
| content_hash | VARCHAR(64) | NULL | 内容 SHA-256 |
| version | INT | NOT NULL DEFAULT 1 | 同一逻辑产物版本 |
| parent_artifact_id | BIGINT | NULL | 派生产物来源 |

索引与约束：

```sql
KEY idx_artifact_workflow_run_id (workflow_run_id)
KEY idx_artifact_step_run_id (step_run_id)
KEY idx_artifact_schema (schema_key, schema_version)
KEY idx_artifact_content_hash (content_hash)
UNIQUE KEY uk_artifact_step_type_version (step_run_id, artifact_type, version, deleted)
```

### evaluation_report（R1 预留，R5 实现）

R1 只冻结关联语义，不要求建表；若提前建表，也只能为空表，不接执行逻辑。

最小字段草案：

```text
workflow_run_id
step_run_id
artifact_id
evaluator_type
status
score
violations_json
schema_key
schema_version
prompt_version_id
created_at
```

GameConfig schema version 关联规则：

- `agent_artifact.schema_key = 'game-config'`
- `agent_artifact.schema_version = '1.0'`
- 后续 `evaluation_report` 通过 `artifact_id + schema_key + schema_version` 记录本次评测依据。
- PromptVersion 也记录 `output_schema_key/output_schema_version`，用于回答“哪个 Prompt 生成了哪个 Schema 版本的产物”。

### outbox_event（R1 仅预留到后续）

`outbox_event` 是 R3 MQ 可靠投递范围。R1 可以在设计中保留位置，但不得要求 R1 业务使用。

## 字段兼容策略

### 旧表只新增，不破坏

- 所有新增字段第一阶段允许 NULL 或提供安全默认值。
- 旧 API、旧 Entity 和旧 SQL 初始化路径不在 R1 删除。
- 旧查询仍可只读旧字段。
- 新字段由 R1/R2 逐步写入，读取方必须兼容 NULL。

### 历史数据升级

1. Flyway baseline 先记录当前真实数据库结构。
2. 新增表和字段以兼容方式上线。
3. 为 ACTIVE PromptTemplate 生成 `prompt_version` version 1。
4. 为当前 `GAME_DESIGN` 生成 `workflow_definition_version` version 1。
5. 历史 `workflow_run` 可按 `workflow_type` 回填 `definition_version_id`，但不是 R1 第一迁移的强制项。
6. 历史 `agent_artifact` 没有 StepRun 时，`step_run_id` 保持 NULL；后续只对新运行强制写入。

### PromptTemplate 兼容

- `prompt_template` 暂时保留。
- `prompt_template.status = ACTIVE` 仍作为旧 AgentRunService 查询入口。
- 新逻辑以 `prompt_version` 为准。
- 迁移时发现 GameConfig seed 仍使用旧字段名时，version 1 可以保留历史原文用于追溯；同时创建修正后的 version 2 并设为 ACTIVE，或在 R1-05 子任务中显式决定。

## 状态机

### WorkflowRun 状态

R1-02 已实现的领域状态集：

```text
PENDING
QUEUED
RUNNING
SUCCESS
FAILED
TIMEOUT
CANCELED
```

R0 兼容状态：

- 旧同步路径可继续直接创建 `RUNNING`。
- 旧路径可继续结束为 `SUCCESS` / `FAILED`。
- R3 前 `PENDING` / `QUEUED` 可只存在于新表/测试中，不要求旧接口使用。

合法转换表：

| From | To | 触发 | 说明 |
| --- | --- | --- | --- |
| `PENDING` | `QUEUED` | Outbox/消息准备完成 | R3 使用 |
| `PENDING` | `RUNNING` | 旧同步 Service 直接执行 | R0 兼容路径 |
| `PENDING` | `CANCELED` | 执行前取消 | 未开始可取消 |
| `QUEUED` | `RUNNING` | Worker 抢占成功 | 条件更新校验旧状态 |
| `QUEUED` | `CANCELED` | 执行前取消 | 不再投递执行 |
| `RUNNING` | `SUCCESS` | 所有必需 Step 成功 | 终态 |
| `RUNNING` | `FAILED` | 不可重试错误或重试耗尽 | 终态或人工重试入口 |
| `RUNNING` | `TIMEOUT` | Workflow 超时 | 可按策略重试 |
| `RUNNING` | `CANCELED` | 取消完成 | 终态 |
| `FAILED` | `QUEUED` | 人工重试 | attempt + 1 |
| `TIMEOUT` | `QUEUED` | 策略允许重试 | attempt + 1 |

禁止：

- `SUCCESS -> RUNNING/FAILED/QUEUED`
- `CANCELED -> RUNNING`
- 无条件覆盖状态
- 不校验 `version` 或旧状态的并发更新

状态更新必须类似：

```sql
UPDATE workflow_run
SET status = 'RUNNING',
    started_at = NOW(),
    version = version + 1
WHERE workflow_run_uuid = ?
  AND status = 'QUEUED'
  AND version = ?;
```

### WorkflowStepRun 状态

目标状态集：

```text
PENDING
RUNNING
SUCCESS
FAILED
TIMEOUT
CANCELED
SKIPPED
```

合法转换表：

| From | To | 触发 |
| --- | --- | --- |
| `PENDING` | `RUNNING` | Runner 抢占步骤，且全部依赖为 `SUCCESS` |
| `RUNNING` | `SUCCESS` | Agent/评测成功 |
| `RUNNING` | `FAILED` | 不可重试错误 |
| `RUNNING` | `TIMEOUT` | 步骤超时 |
| `RUNNING` | `CANCELED` | 停止完成 |
| `PENDING` | `SKIPPED` | 可选步骤因条件不满足跳过 |
| `FAILED` | `PENDING` | 新 attempt 重试 |
| `TIMEOUT` | `PENDING` | 新 attempt 重试 |

规则：

- 只有依赖步骤全部 `SUCCESS`，当前步骤才能进入 `RUNNING`。
- 同一 `workflow_run_id + step_key + attempt` 只能有一条记录。
- 已 `SUCCESS` 的 step attempt 不得重复调用 LLM。
- 重试创建新 attempt 或推进 attempt 字段时，必须保留失败证据。

## PromptVersion 规则

1. PromptVersion 创建后不可变。
2. ACTIVE 切换只影响未来 WorkflowRun。
3. WorkflowRun 创建时冻结每个 Step 使用的 `prompt_version_id`。
4. AgentRun 保存实际使用的 `prompt_version_id`。
5. PromptVersion 必须记录输出 schema key/version。
6. GameConfig Prompt 必须和 `docs/game-config-schema.md` 对齐：当前 schema 是 `game-config` / `1.0`，Runtime 字段是 `items` 与 `rules`。
7. 历史 PromptTemplate 的旧字段名不得静默覆盖，应在迁移报告中记录。

## Flyway 迁移顺序

R1 推荐迁移顺序：

```text
V1__baseline_existing_schema.sql
V2__add_workflow_definition_version.sql
V3__add_workflow_step_run.sql (包括 agent_artifact.step_run_id 关联)
V4__add_prompt_version.sql (包括 agent_run.prompt_version_id 预留)
V5__extend_workflow_run_for_domain_snapshot.sql
V6__extend_agent_run_for_workflow_domain.sql（其余运行元数据）
V7__extend_agent_artifact_for_schema.sql（其余 Schema 元数据）
```

说明：

- `V1` 必须来自当前真实数据库结构，而不只是 `backend-java/src/main/resources/db/` 的零散 SQL；当前目录缺少 `agent_run` 建表脚本，baseline 子任务必须先核对。
- 新环境用 Flyway 从 V1 初始化。
- 已有本地库使用 Flyway baseline-on-migrate 或手工 baseline，具体方式在 R1-01 冻结。
- 新增 NOT NULL 字段必须遵循：允许 NULL -> 回填 -> 验证 -> 加约束。
- 生产式数据不做破坏性回滚；回滚策略是备份恢复或禁用新读写路径。

## R1 子任务依赖顺序

```text
R1-00 RFC
-> R1-01 Flyway Baseline
-> R1-02 Workflow Status Policy
-> R1-03 WorkflowDefinitionVersion
-> R1-04 WorkflowStepRun
-> R1-05 PromptVersion
-> R1-06 WorkflowRun Snapshot
-> R1-ACCEPTANCE
```

| 子任务 | 目标 | 依赖 | 验证命令 |
| --- | --- | --- | --- |
| R1-01 | 接入 Flyway，建立 baseline | R1-00 | `mvn test`、空库/已有库迁移验证、`.\tools\verify.ps1 -Profile quick` |
| R1-02 | 状态枚举与合法转换策略 | R1-01 | `mvn -Dtest=*Workflow*Status* test` |
| R1-03 | 新增 WorkflowDefinitionVersion 表/实体/Mapper | R1-01, R1-02 | `mvn -Dtest=WorkflowDefinitionVersion* test` |
| R1-04 | 新增 WorkflowStepRun 表/实体/Mapper | R1-02, R1-03 | `mvn -Dtest=WorkflowStepRun* test` |
| R1-05 | PromptVersion 表、迁移与不可变规则 | R1-01 | `mvn -Dtest=PromptVersion* test` |
| R1-06 | WorkflowRun snapshot 字段与兼容写入 | R1-03, R1-04, R1-05 | `mvn -Dtest=WorkflowServiceImplTest,*WorkflowRunSnapshot* test` |
| R1-ACCEPTANCE | 汇总迁移、状态、兼容与 quick Harness | R1-01..R1-06 | `.\tools\verify.ps1 -Profile quick` |

## 推迟到后续阶段

| 能力 | 推迟阶段 | 原因 |
| --- | --- | --- |
| 抽取统一 WorkflowRunner | R2 | 必须先有 StepRun/状态策略地基 |
| RabbitMQ、Outbox、DLQ、ACK | R3 | 需要稳定状态机和幂等字段 |
| SSE 订阅模型和运行中心页面 | R4 | 依赖持久化状态查询 API |
| EvaluationReport 完整写入与 Dashboard | R5 | 依赖 PromptVersion、Artifact schema、StepRun |
| RAG 文档、Chunk、RetrievalRecord | R6 | 依赖 Prompt/Evaluation 追踪闭环 |
| E2E、压测和故障注入报告 | R7 | 需要 R3-R6 能力落地 |

## 风险与回滚

### 风险：baseline 与真实库不一致

现有 `db/` 目录没有完整 `agent_run` 建表脚本。R1-01 必须从真实可运行数据库或当前初始化链路生成 baseline，并用空库与已有库分别验证。

回滚：

- R1-01 不通过时不继续新增业务表。
- 保留旧 SQL，不删除旧启动路径。

### 风险：状态机与旧同步路径冲突

旧 `WorkflowServiceImpl` 直接创建 `RUNNING`，目标状态机引入 `PENDING/QUEUED`。

控制：

- R1 状态策略允许 legacy entry：旧路径 `CREATE -> RUNNING` 仍合法。
- R2/R3 新路径再强制 `PENDING -> QUEUED -> RUNNING`。

### 风险：PromptVersion 迁移污染历史

GameConfig seed prompt 与当前 schema 字段不同。

控制：

- 迁移保留历史 version 1 原文。
- 如要修正，只创建新 version，不原地覆盖。
- 关联 `output_schema_key/version`，让评测能解释失败原因。

### 风险：过早建 Evaluation/Outbox 表扩大范围

控制：

- R1 文档只冻结关联字段。
- `outbox_event`、完整 `evaluation_report` 可在 R3/R5 建表；若提前建，不能接业务执行。

## 验证命令

R1-00 本任务只验证文档 diff：

```powershell
git status --short
git diff -- docs/requirements/R1-workflow-domain-design.md
```

后续实现任务的基础验证：

```powershell
cd backend-java
mvn test

cd ..
.\tools\verify.ps1 -Profile quick
```

## 审查结论

- R1 不是“先改执行器”，而是先让运行事实可被数据库表达。
- 只有先冻结 definition、step、prompt、snapshot 和状态转换，R2 Runner 才能做到行为可验证而不是大重写。
- R1 不接 MQ、不改 SSE、不做 RAG，是为了把数据一致性、迁移和兼容风险隔离在最小阶段内。
