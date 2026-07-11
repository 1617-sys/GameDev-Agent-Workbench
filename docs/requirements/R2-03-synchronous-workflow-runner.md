# R2-03: 同步 WorkflowRunner 与持久化状态推进

> 状态：`TODO`
>
> 前置任务：`R2-01`、`R2-02`
>
> 推荐模型：`gpt-5.5`
>
> 任务类型：核心编排 / 高风险回归

## 背景

R1 已建立 WorkflowRun 和 StepRun 的状态模型，R2-01/02 已提供执行上下文和单步骤执行端口。本任务实现真正的同步 Runner，使它成为两条旧入口共同依赖的唯一步骤编排内核。

## 目标

实现 `WorkflowRunner`，以单次已创建的 WorkflowRun 为输入：

```text
load frozen run snapshot
-> load or initialize StepRun plan
-> validate dependencies
-> claim PENDING StepRun
-> execute StepExecutor
-> persist result and notify listener
-> advance next runnable step
-> summarize WorkflowRun terminal status
```

Runner 完成后必须能在不依赖 HTTP、SSE、MQ 的情况下直接运行和测试。

## 范围

允许：

- 新增 `WorkflowRunner`、应用命令/结果对象、最小查询/持久化端口和同步实现。
- 复用 `WorkflowStatusPolicy` 推进 WorkflowRun 与 WorkflowStepRun 状态。
- 从 R1 定义快照初始化缺失的 StepRun，并读取已有 SUCCESS StepRun。
- 注入可选 `WorkflowExecutionListener`，发布 started/step started/step succeeded/step failed/completed 领域事件。
- 新增 Runner 单元与服务层测试，覆盖成功、第一步失败、中间失败、依赖未满足、已有 SUCCESS、终态重复调用。

## 非目标

- 不接 RabbitMQ Consumer、Outbox、手动 ACK、重试队列或 DLQ。
- 不实现多 Worker 并发抢占、Redis 锁续期或服务重启恢复。
- 不修改提交 API 为异步 `202`。
- 不直接改造 Demo SSE Controller 或前端事件格式，该工作留给 R2-05。
- 不支持 DAG 并行；本阶段按定义顺序串行执行。

## 约束

- Runner 只接受已持久化的 WorkflowRun UUID/领域命令，不能自行创建匿名运行记录。
- Runner 必须使用该 Run 的冻结 definition/prompt/input snapshot，不能重新查询 ACTIVE 版本覆盖历史。
- 终态 WorkflowRun 不可执行；已 SUCCESS StepRun 不可重复调用 Agent。
- 一步失败后，后续未运行步骤不得执行，并应留下可解释状态或原因。
- 状态推进必须受旧状态/版本条件保护；受影响行数为 0 时当前流程停止，不得假设自己仍拥有执行权。
- 数据库事务不能覆盖整个 Runner 或外部 Agent 调用。

## 验收标准

- [ ] 三步 GAME_DESIGN 的成功执行由 Runner 按定义快照顺序完成。
- [ ] 任一步骤失败后，WorkflowRun 为 FAILED，失败 StepRun 有错误信息，后续步骤未调用 Agent。
- [ ] 已成功步骤在 Runner 再次调用、查询或 listener 重放时不重复执行。
- [ ] 终态 WorkflowRun 的重复运行请求被拒绝或返回明确的不可执行结果。
- [ ] Listener 异常不应把已可靠持久化的业务状态回滚为错误状态；其语义在测试中固定。
- [ ] Runner 不依赖 SseEmitter、Controller、RabbitMQ 类型，R0/R1 测试通过。

## 验证命令

```powershell
cd backend-java
mvn -Dtest=*WorkflowRunner*Test,*WorkflowStatusPolicy*Test,*WorkflowStepRun*Test test
mvn test

cd ..
.\tools\verify.ps1 -Profile quick
```

## 审查清单

- 是否重新查询 ACTIVE 定义或 Prompt，破坏运行可复现性。
- 是否在失败后继续执行依赖步骤，或把失败覆盖成 SUCCESS。
- 是否在 listener/SSE 回调异常时重复 Agent 调用。
- 是否将整个流程包在一个数据库事务中。
- 是否遗漏初始 StepRun、已有 StepRun 和终态 Run 三类恢复边界。

## 完成定义

- 统一同步 Runner 已可通过独立测试证明其调度和状态语义。
- 两条旧入口具备迁移到同一 Runner 的稳定目标。
