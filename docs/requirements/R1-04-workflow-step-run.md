# R1-04：WorkflowStepRun 表与步骤运行快照

> 状态：`DONE`
>
> 前置任务：`R1-02`、`R1-03`
>
> 推荐模型：`gpt-5.5`
>
> 任务类型：数据库迁移 / 运行追踪

## 背景

当前 `WorkflowRun` 只能表达整体结果，`AgentRun` 和 `Artifact` 缺少明确的 StepRun 关联。后续 Runner、重试、幂等、评测和指标都需要步骤级运行记录。

## 目标

新增步骤运行数据基础：

```text
workflow_step_run
+ step_run_uuid
+ workflow_run_uuid
+ definition_version
+ step_key
+ attempt
+ status
+ started_at / finished_at
+ input_snapshot / output_snapshot / error
```

## 范围

允许：

- 新增 Flyway migration。
- 新增 Entity、Mapper、VO 或 Repository。
- 在现有 Workflow 执行中最小写入 StepRun 记录。
- 让 AgentRun 或 Artifact 可以关联 StepRun。
- 增加成功、失败、异常路径测试。

## 非目标

- 不抽取完整 WorkflowRunner。
- 不实现重试。
- 不实现并发消费幂等。
- 不接 RabbitMQ。
- 不做前端 StepRun 详情页。
- 不实现取消和恢复扫描。

## 约束

- 不持有数据库事务等待 LLM。
- StepRun 写入失败不能被静默吞掉。
- 已成功步骤未来不得重复执行的最终规则可以推迟到 R2/R3，但字段必须支持。
- `attempt` 从 1 开始。
- `step_run_uuid` 必须稳定可追踪。

## 验收标准

- [ ] migration 创建 `workflow_step_run`。
- [ ] 成功 Workflow 至少写入 3 个设计步骤或当前流程对应步骤。
- [ ] 步骤失败时，失败步骤状态和错误信息被记录。
- [ ] 未执行步骤有明确状态或明确不创建策略。
- [ ] WorkflowRun 与 StepRun 可通过 uuid 关联查询。
- [ ] R0 Workflow 测试仍通过。
- [ ] quick Harness 通过。

## 验证命令

```powershell
cd backend-java
mvn -Dtest=WorkflowServiceImplTest,*StepRun*Test test

cd ..
.\tools\verify.ps1 -Profile quick
```

## 审查清单

- 是否把 StepRun 当日志随便写，而不是业务状态。
- 是否没有 attempt 字段，导致未来无法表达重试。
- 是否 Artifact 与 StepRun 仍无法关联。
- 是否失败路径只更新 WorkflowRun，不更新 StepRun。
- 是否修改了前端或 Python。

## 完成定义

- StepRun 数据在成功和失败路径都有证据。
- 查询关系清楚。
- 未实现的重试/MQ 行为明确推迟。
