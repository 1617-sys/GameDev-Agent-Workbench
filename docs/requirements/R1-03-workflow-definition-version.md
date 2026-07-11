# R1-03：WorkflowDefinitionVersion 表与默认定义快照

> 状态：`READY_AFTER_R1-01`
>
> 前置任务：`R1-00`、`R1-01`
>
> 推荐模型：`gpt-5.5`
>
> 任务类型：数据库迁移 / Workflow 定义版本化

## 背景

当前 Workflow 步骤写死在 Java Service 中，历史运行无法知道当时使用的是哪一版工作流定义。R1 需要先引入版本化定义，为 R2 Runner 提供数据基础。

## 目标

通过 migration 和最小领域模型支持：

```text
workflow_definition_version
+ workflow_step_definition
+ 默认 GAME_DESIGN / DEMO_GAME_CONFIG 定义
+ 可查询当前 ACTIVE 定义
```

## 范围

允许：

- 新增 Flyway migration。
- 新增 Entity、Mapper、Repository 或 Service 查询方法。
- 写入当前已存在工作流的默认定义版本。
- 增加数据库字段和唯一约束。
- 增加 mapper/unit/integration smoke test。

## 非目标

- 不让现有 WorkflowService 完全改为从定义执行。
- 不实现可视化工作流编辑器。
- 不支持用户自定义工作流。
- 不实现 DAG 并行执行。
- 不接 MQ。
- 不删除旧硬编码步骤。

## 建议数据

WorkflowDefinitionVersion 至少考虑：

```text
id
workflow_key
version
name
status
definition_json
created_at
created_by
```

WorkflowStepDefinition 至少考虑：

```text
id
definition_version_id
step_key
step_order
agent_type
artifact_type
depends_on_step_key
prompt_template_key
created_at
```

## 验收标准

- [ ] migration 可创建定义表。
- [ ] 默认工作流定义被 seed 或 migration 初始化。
- [ ] 同一 `workflow_key + version` 有唯一约束。
- [ ] ACTIVE 定义查询有测试。
- [ ] 旧 Workflow API 行为不变。
- [ ] quick Harness 通过。

## 验证命令

```powershell
cd backend-java
mvn test

cd ..
.\tools\verify.ps1 -Profile quick
```

## 审查清单

- 是否把 Java 枚举和数据库定义做成两个互相冲突的来源。
- 是否缺少唯一约束，导致同一版本重复。
- 是否在 R1 就删除旧硬编码流程。
- 是否把 PromptVersion、Runner、MQ 一起塞入本任务。

## 完成定义

- 默认定义版本可落库并查询。
- 数据迁移可重复验证。
- 旧接口仍通过 R0 测试。
