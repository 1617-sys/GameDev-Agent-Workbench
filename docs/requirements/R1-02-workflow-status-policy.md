# R1-02：Workflow 状态枚举与合法转换策略

> 状态：`DONE`
>
> 前置任务：`R1-00`
>
> 推荐模型：`gpt-5.5`
>
> 任务类型：领域规则 / 状态机

## 背景

R0 已经用测试固定了当前 Workflow 成功、失败、异常和无权限行为。但状态合法性仍散落在 Service 代码中，后续 Runner、MQ、恢复扫描都会依赖统一状态机。

本任务先建立领域层状态策略，不重写执行流程。

## 目标

新增可测试的状态策略：

```text
WorkflowRunStatus
+ WorkflowStepRunStatus
+ WorkflowStatusPolicy
+ 合法转换测试
```

## 范围

允许：

- 新增或整理 Workflow 状态枚举。
- 新增领域策略类，例如 `WorkflowStatusPolicy`。
- 增加状态转换单元测试。
- 在不改变现有 API 行为的前提下，让现有 Service 使用或暂时旁路该策略。
- 更新 R1 设计文档中的状态表。

## 非目标

- 不新增数据库表。
- 不接 Flyway migration，除非 R1-01 已完成且本卡明确需要。
- 不抽取 WorkflowRunner。
- 不接 MQ。
- 不实现取消、重试、恢复扫描。
- 不修改前端状态展示。

## 状态范围

WorkflowRun 建议状态：

```text
PENDING
QUEUED
RUNNING
SUCCESS
FAILED
TIMEOUT
CANCELED
```

WorkflowStepRun 建议状态：

```text
PENDING
RUNNING
SUCCESS
FAILED
TIMEOUT
CANCELED
SKIPPED
```

## 验收标准

- [ ] 合法转换有测试覆盖。
- [ ] 非法转换被拒绝且错误信息可理解。
- [ ] `SUCCESS`、`FAILED`、`TIMEOUT`、`CANCELED` 等终态不能继续进入 `RUNNING`。
- [ ] StepRun 只有依赖满足时才允许从 `PENDING` 进入 `RUNNING` 的规则有表达或明确推迟。
- [ ] 不改变 R0 Workflow 测试结果。
- [ ] quick Harness 通过。

## 验证命令

```powershell
cd backend-java
mvn -Dtest=*Workflow*Test test

cd ..
.\tools\verify.ps1 -Profile quick
```

## 审查清单

- 是否把状态策略写成 Controller 或 Service 私有逻辑。
- 是否存在无条件覆盖状态的代码。
- 是否遗漏终态不可逆规则。
- 是否让前端或 Python 决定后端状态是否合法。
- 是否为了当前测试通过而降低状态机约束。

## 完成定义

- 状态策略有独立测试。
- 现有 Workflow 测试仍通过。
- 本任务没有引入数据库迁移和 Runner 重构。
