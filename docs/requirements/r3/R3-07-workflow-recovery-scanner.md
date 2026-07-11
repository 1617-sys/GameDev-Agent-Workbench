# R3-07: Workflow 恢复扫描、Heartbeat 与审计

> 状态：`TODO`
>
> 前置任务：`R3-03`、`R3-04`、`R3-05`
>
> 推荐模型：`gpt-5.5`
>
> 任务类型：服务重启恢复 / 状态机安全

## 背景

即使有 Outbox 和 ACK，服务仍可能在发布前、消费抢占后或外部 Agent 调用期间崩溃。没有恢复扫描，任务会永远停在 PENDING、QUEUED 或 RUNNING，无法解释也无法继续。

## 目标

实现可重复执行的恢复机制：

```text
PENDING + stale/unpublished Outbox
-> create/re-enable Outbox publish intent

QUEUED + overdue delivery
-> durable re-dispatch intent

RUNNING + stale heartbeat
-> conditional mark interrupted attempt
-> retry policy / new outbox event / final FAILED

every action
-> RecoveryAuditEvent + traceable log
```

## 范围

允许：

- 新增/扩展 heartbeat、lastActivityAt、recoveryAttempt、恢复审计字段或独立审计表，并提供 Flyway migration。
- 在 Consumer/Runner 外层和步骤边界更新 heartbeat，不把心跳更新绑在长事务中。
- 新增定时扫描器、可测试的 RecoveryService、恢复策略配置和 Outbox 再投递。
- 通过 WorkflowStatusPolicy 与版本条件更新保证扫描器多实例安全。
- 增加 PENDING、QUEUED、RUNNING 超时、已终态、重复扫描、并发扫描、取消请求等测试。
- 记录 workflowRunUuid、旧状态、新状态、原因、recoveryAttempt、eventId、traceId。

## 非目标

- 不实现人工恢复 UI、完整运维告警平台或管理员重放页面。
- 不强行终止第三方 LLM 请求；只能做协作式状态恢复。
- 不引入 R4 的 SSE 实时状态订阅。
- 不修改 R2 Runner 的核心业务编排。
- 不将任何 RUNNING 状态无条件重置为 QUEUED。

## 约束

- 扫描只处理超过配置阈值且满足恢复策略的记录；阈值必须可配置。
- 每次恢复必须先用状态/version 条件更新抢占，受影响行数为 0 时停止，不得覆盖活跃 Worker 的状态。
- RUNNING 恢复必须考虑已 SUCCESS StepRun；重新执行只能从未完成/可重试步骤继续，不能重复成功 Agent 调用。
- 恢复的再次投递仍通过 Outbox，而不是扫描器直接调用 Runner 或 `RabbitTemplate` 后不留记录。
- 已终态 WorkflowRun、正常 heartbeat、cancel requested 的 Run 不得被自动重排队。
- 任何恢复失败均应留下审计证据并由下一轮安全重试，不得静默吞掉。

## 验收标准

- [ ] 停留在 PENDING/QUEUED 的可恢复 Run 能产生新的可靠投递意图。
- [ ] heartbeat 超时的 RUNNING Run 只被一个扫描器恢复，并遵守 retry/失败策略。
- [ ] 终态、活跃 heartbeat、已取消的 Run 不会被扫描器错误处理。
- [ ] 恢复后已 SUCCESS StepRun 不会再次调用 Agent 或重复写 Artifact。
- [ ] 连续/并发扫描不会产生无限 OutboxEvent 或覆盖状态。
- [ ] 每次恢复动作可通过 WorkflowRun 关联到审计记录和日志。

## 验证命令

```powershell
cd backend-java
mvn -Dtest=*WorkflowRecovery*Test,*WorkflowHeartbeat*Test,*Outbox*Test,*WorkflowRunner*Test test
mvn test

cd ..
.\tools\verify.ps1 -Profile quick
.\tools\verify.ps1 -Profile integration
```

## 审查清单

- 是否在没有条件更新的情况下批量重置 RUNNING 状态。
- 是否恢复时绕过 Outbox 直接调用 Runner。
- 是否把活跃任务、终态任务或取消任务错误重排队。
- 是否遗漏 SUCCESS StepRun 导致恢复后重复调用 Agent。
- 是否没有审计证据或恢复次数上限。

## 完成定义

- 服务重启和消息丢失窗口不再让 Workflow 永久卡死。
- 恢复路径与正常消费共享同一 Runner、Outbox 和状态机语义。
