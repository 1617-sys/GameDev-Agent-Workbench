# R4-04: WorkflowRun 取消与人工重试命令 API

> 状态：`TODO`
>
> 前置任务：`R3-验收`、`R4-00`、`R4-01`、`R4-02`
>
> 推荐模型：`gpt-5.5`
>
> 任务类型：生命周期命令 / 状态机与可靠投递

## 背景

运行中心需要展示可执行动作，而不是在前端猜测状态。R3 已具备状态机、Outbox、执行抢占和恢复能力；R4 只在这些可靠语义之上提供取消与人工重试命令，不重新实现消息可靠性。

## 目标

新增：

```http
POST /api/v1/workflow-runs/{workflowRunUuid}/cancel
POST /api/v1/workflow-runs/{workflowRunUuid}/retry
```

目标语义：

```text
cancel: validate owner + state -> durable cancel request/state -> event -> consumer cooperates before next step
retry: validate owner + terminal/retry policy -> new attempt/eligible StepRun plan + OutboxEvent -> event -> 202-style accepted result
```

## 范围

允许：

- 新增 CommandService、Controller、DTO/VO、状态/权限校验和相关事件。
- 复用 R1 WorkflowStatusPolicy、R3 Outbox、R3 错误分类/重试策略和 R4 事件模型。
- 实现协作式取消：未开始步骤不再执行，正在进行的外部调用只做尽力终止/安全忽略其结果。
- 实现人工重试的 attempt 增长、可执行 StepRun 初始化/复用 SUCCESS 步骤、Outbox 投递意图与审计。
- 添加取消前/中/终态、无权限、重复取消、非法重试、成功重试、重复命令和 Consumer 协作测试。

## 非目标

- 不提供前端按钮/确认弹窗，前端接入在 R4-06 完成。
- 不直接调用 Runner、Python Agent 或 RabbitTemplate 绕过 Outbox。
- 不承诺强杀已发出的第三方 LLM 请求。
- 不实现无限人工重试、批量重试、DLQ 管理 UI 或复杂审批。
- 不改变 R3 的错误分类与最大重试策略，只消费其明确结果。

## 约束

- 只能对有权限的 Run 执行命令；请求本身必须幂等，重复 cancel/retry 不得创建重复 Outbox/attempt。
- cancel/retry 的合法性由后端状态机决定；前端按钮隐藏不是安全边界。
- 取消请求和重试投递必须持久化，并生成有序 R4 event；不能只改浏览器状态。
- 重试不得重新执行已有 SUCCESS StepRun 或重复写其 Artifact；需基于 attempt 和 StepRun 状态判断。
- Consumer 在每个可执行步骤前检查取消请求；取消后的消息/恢复扫描不得重启新的步骤。
- 业务状态和 Outbox/事件写入必须保持短事务一致，不能等待 Agent。

## 验收标准

- [ ] 合法取消会持久化状态/请求、发出事件，并阻止后续未开始步骤。
- [ ] 终态/无权限/重复取消返回稳定、幂等的结果，不改变既有 Artifact。
- [ ] 仅符合策略的 FAILED/TIMEOUT 等 Run 可人工重试，产生一个可追溯的新 attempt 和可靠投递意图。
- [ ] 已 SUCCESS 的 StepRun 在重试中不重复调用 Agent/写 Artifact。
- [ ] 重复 retry 命令不会生成多个有效 attempt 或多个 OutboxEvent。
- [ ] 取消/重试的事件可由 R4 查询和 SSE 订阅观察到。

## 验证命令

```powershell
cd backend-java
mvn -Dtest=*WorkflowCancel*Test,*WorkflowRetry*Test,*WorkflowStatusPolicy*Test,*WorkflowMessageConsumer*Test test
mvn test

cd ..
.\tools\verify.ps1 -Profile quick
.\tools\verify.ps1 -Profile integration
```

## 审查清单

- 是否在 Controller 直接调用 Runner 或跳过 Outbox。
- 是否只靠前端隐藏按钮来限制非法状态转换。
- 是否取消后仍启动未开始步骤，或重试后重复 SUCCESS StepRun。
- 是否重复命令产生多个 attempt/OutboxEvent。
- 是否将取消外部 LLM 调用描述成可强制保证却没有实现。

## 完成定义

- 运行中心可安全展示并调用后端定义的取消/重试动作。
- 所有动作都遵守 R3 可靠性语义并可由事件和审计追溯。
