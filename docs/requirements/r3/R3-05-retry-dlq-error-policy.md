# R3-05: 错误分类、延迟重试与 Dead Letter Queue

> 状态：`TODO`
>
> 前置任务：`R3-03`、`R3-04`
>
> 推荐模型：`gpt-5.5`
>
> 任务类型：失败治理 / 消息可靠性

## 背景

Consumer 不能将所有异常简单 requeue：参数错误会无限循环，网络超时可能需要退避，输出解析错误可有限修复，最终失败必须能定位到对应 WorkflowRun。R3 要把失败处理从异常分支变成可测试的策略。

## 目标

建立如下失败链路：

```text
exception / failure result
-> WorkflowErrorClassifier
-> retryable? retryCount < max?
-> persist StepRun/WorkflowRun + error evidence
-> delayed retry queue OR final FAILED + DLQ
-> manual ACK original message after durable handoff
```

## 范围

允许：

- 新增错误分类枚举/策略，将参数、权限、Prompt、Provider 限流、网络超时、输出解析、GameConfig 校验、MQ/DB 基础设施等映射为明确策略。
- 声明主队列、按退避级别的 retry queue、DLX/DLQ 与 routing key。
- 持久化 retryCount、lastErrorCode、lastErrorMessage、nextRetryAt、failedAt 和可追踪消息证据。
- 实现有限次数延迟重试、最终 FAILED、DLQ 消息头与 WorkflowRun 关联。
- 为重试消息保留原 workflowRunUuid、attempt、traceId、eventId/messageId 关联。
- 新增 RabbitMQ/Testcontainers 测试，覆盖不可重试、可重试、超过上限、重复重试消息、DLQ 查询。

## 非目标

- 不提供前端 DLQ 管理、人工重放按钮或可视化告警。
- 不实现 R5 的模型质量评测或自动 Prompt 修复调用。
- 不把业务错误全部视为 RabbitMQ 传输错误。
- 不修改已有 GameConfig 结构化契约。
- 不实现完整运维告警平台。

## 约束

- 不可重试错误必须可靠标记 FAILED 并 ACK 原消息，不能无限 requeue。
- 可重试错误只能在配置的最大次数内按退避重投；每次重试都要有持久化证据。
- 重试不得使已经 SUCCESS 的 StepRun 再次调用 Agent；只能从可执行/失败 attempt 的明确状态恢复。
- 同一失败消息进入 DLQ 后，必须通过 headers 或持久化字段关联到 workflowRunUuid 和错误原因。
- 发送到 retry/DLQ 前，业务状态必须已可靠写入；消息转移失败应保留可恢复 Outbox/审计证据。
- 错误信息应脱敏，不能把 Secret、Authorization 或完整 Prompt 私密内容写入队列/日志。

## 验收标准

- [ ] 参数/权限/Prompt 配置错误不重试，WorkflowRun/StepRun 明确 FAILED。
- [ ] Provider 429、网络超时等可重试错误按退避次数重试，且 retryCount 可查询。
- [ ] 超过上限后消息进入 DLQ，能够关联到原 WorkflowRun、eventId/messageId 和最后错误。
- [ ] 重复到达的 retry 消息不重复执行已 SUCCESS 步骤或重复产物。
- [ ] 队列和持久化状态对失败路径有一致、可解释的证据。
- [ ] 集成测试真实断言路由/延迟/最终状态，而不只 Mock classifier。

## 验证命令

```powershell
cd backend-java
mvn -Dtest=*WorkflowErrorClassifier*Test,*Retry*Test,*DeadLetter*Test,*WorkflowMessageConsumer*Test test
mvn test

cd ..
.\tools\verify.ps1 -Profile quick
.\tools\verify.ps1 -Profile integration
```

## 审查清单

- 是否任何异常都无限 requeue。
- 是否在发送 retry/DLQ 前没有持久化失败状态。
- 是否 retryCount 仅存在 RabbitMQ header、服务重启后丢失。
- 是否将已 SUCCESS 的 StepRun 重新交给 Runner。
- 是否让 DLQ 消息无法追溯到 WorkflowRun 或泄露敏感内容。

## 完成定义

- 每类失败都有可解释、有限、可验证的最终去向。
- Consumer 的失败不会沉默丢失，也不会无限重复计费。
