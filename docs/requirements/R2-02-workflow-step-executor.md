# R2-02: WorkflowStepExecutor、ArtifactWriter 与 Evaluation Hook

> 状态：`DONE`
>
> 前置任务：`R2-00`、`R2-01`
>
> 推荐模型：`gpt-5.5`
>
> 任务类型：执行端口 / 状态与产物测试

## 背景

当前 `WorkflowServiceImpl` 和 `DemoStreamServiceImpl` 都在各自的私有方法中完成 Agent 调用、StepRun 写入和 Artifact 读取。R2 需要把“一个步骤如何执行”抽为可替换单元，Runner 只负责调度和状态推进。

## 目标

建立以下最小、可单测的执行边界：

```text
WorkflowStepExecutor
  -> supports(stepPlan)
  -> execute(executionContext, stepPlan)
  -> StepExecutionResult

AgentStepExecutor
  -> AgentRunService
  -> AgentRun / Artifact reference

ArtifactWriter
  -> 将结构化结果写入 StepRun / Artifact 关联

WorkflowEvaluationHook
  -> 可选的后处理钩子，不阻塞非 GameConfig 文本步骤
```

## 范围

允许：

- 新增 Executor 接口、结果值对象、默认 AgentStepExecutor、注册/选择机制。
- 将当前 AgentRunService 调用包装到 Executor 内，保留现有 Agent API 和错误语义。
- 按 R1 数据模型创建或更新对应 WorkflowStepRun，保存输入/上下文/输出摘要、状态和耗时。
- 新增 `ArtifactWriter`，把 Agent 产物引用写回 `StepExecutionResult` 与 StepRun 关联。
- 新增空实现或最小接口化的 Evaluation hook，供 R2-06 承接。
- 增加 Executor 成功、业务失败、未知异常、无匹配 Executor 和重复执行保护测试。

## 非目标

- 不实现新的 Python Agent HTTP 协议、RAG 或模型指标完整记录。
- 不接 RabbitMQ、重试队列、DLQ、Outbox、人工重试或超时恢复。
- 不让 Evaluation hook 变成 R5 评测引擎。
- 不修改 Vue 和 SSE Controller。
- 不删除 `AgentRunService` 或旧私有步骤方法。

## 约束

- 一个 StepExecutor 只处理一个 StepPlan；跨步骤排序、失败短路由 Runner 负责。
- 只允许在 `PENDING -> RUNNING` 成功后调用 Agent；状态更新失败时不得继续执行。
- 已 `SUCCESS` 的同 attempt StepRun 必须返回已有输出或明确跳过，不得再次调用 Agent。
- Agent 调用不得持有长数据库事务；持久化状态更新应在调用前后短事务完成。
- Artifact 的 schema key/version 必须来自步骤快照或明确结果，不能猜测当前 ACTIVE Prompt。

## 验收标准

- [ ] 相同 StepPlan 可由支持它的 Executor 执行，未知类型被明确拒绝。
- [ ] 成功时 StepRun 记录输入、上下文摘要、输出摘要、AgentRun/Artifact 关联和 SUCCESS 状态。
- [ ] Agent 业务失败与未知异常均使 StepRun 进入正确失败状态并保留脱敏原因。
- [ ] 已 SUCCESS 的相同 attempt 不会二次调用 AgentRunService。
- [ ] ArtifactWriter 不会把非 GameConfig 文本错误标成 GameConfig Artifact。
- [ ] 全部新增逻辑有单元或 Mock 交互测试，R0/R1 回归通过。

## 验证命令

```powershell
cd backend-java
mvn -Dtest=*WorkflowStepExecutor*Test,*AgentStepExecutor*Test,*ArtifactWriter*Test test
mvn test

cd ..
.\tools\verify.ps1 -Profile quick
```

## 审查清单

- 是否让 Executor 同时承担流程循环、SSE 推送或 HTTP 响应拼装。
- 是否在 Agent 远程调用期间开启数据库事务。
- 是否只靠内存变量防止重复 Agent 调用。
- 是否吞掉异常后给 Runner 返回伪成功结果。
- 是否遗漏 Artifact 与 StepRun 的真实关联。

## 完成定义

- Step 的执行、Agent 调用、产物引用和可选后处理已有清晰的可替换边界。
- Runner 可仅依赖 `WorkflowStepExecutor` 和结果对象完成流程调度。
