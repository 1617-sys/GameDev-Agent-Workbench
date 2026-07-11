# R3-03: Transactional Outbox 与 Publisher Confirm

> 状态：`TODO`
>
> 前置任务：`R3-01`、`R3-02`
>
> 推荐模型：`gpt-5.5`
>
> 任务类型：可靠投递 / 集成测试

## 背景

R3-02 已在提交事务中写入 OutboxEvent，但尚未发送消息。如果直接在事务内发 MQ，会出现“数据库提交成功、消息丢失”或“消息已发送、事务回滚”的裂缝。

## 目标

实现独立的 Outbox Publisher：

```text
committed OutboxEvent(PENDING)
-> claim publishing ownership
-> publish WorkflowRunMessage to exchange
-> wait publisher confirm
-> PUBLISHED or retryable publish failure
```

最终保证是“至少一次投递 + Consumer 幂等”，不是宣称 MQ 和数据库的分布式 exactly-once。

## 范围

允许：

- 新增 OutboxEvent Entity、Mapper/Repository、Publisher、调度触发器和 RabbitMQ 拓扑声明。
- 定义 `WorkflowRunMessage` 的稳定字段：至少 `messageId`、`eventId`、`workflowRunUuid`、`attempt`、`traceId`、创建时间和 schema version。
- 实现 Outbox 状态、发布 attempt、next retry time、confirmed/published time、失败原因和 owner token。
- 使用 publisher confirm/return callback 将确认成功与不可路由/超时/异常区分持久化。
- 新增集成测试，模拟 confirm 成功、失败、重复发布、Publisher 重启/扫描恢复。
- 在日志中统一记录 traceId、workflowRunUuid、outboxEventId、messageId。

## 非目标

- 不实现 Consumer、手动 ACK、Runner 调用或业务重试。
- 不在本任务中实现用户限流或前端 SSE。
- 不把 OutboxEvent 当作通用事件总线或引入复杂事件溯源。
- 不删除 PUBLISHED 记录；清理策略仅设计/保留接口，不在本任务做归档。

## 约束

- 只发布已提交、处于可发布状态且到达 `next_attempt_at` 的 OutboxEvent。
- 多个 Publisher 实例必须通过数据库条件更新/租约只允许一个有效发布者；不能依赖单进程 `synchronized`。
- 只有 broker confirm 成功后才能标记 PUBLISHED；超时或异常必须保留为可重试证据。
- 消息必须携带稳定 messageId；同一 OutboxEvent 重发使用相同业务 eventId，并允许新的发布 attempt 记录。
- 发布与确认处理失败时，不得删除 OutboxEvent 或误标为成功。
- Publisher 不能持有数据库事务等待网络 confirm；状态更新应是短事务。

## 验收标准

- [ ] 成功提交的 OutboxEvent 最终发布到配置的 workflow exchange，且确认后标记 PUBLISHED。
- [ ] RabbitMQ 不可用、confirm 超时或不可路由时，OutboxEvent 保持可恢复，不被错误标记成功。
- [ ] 同一 Event 被扫描两次不会造成两个并发有效 publisher claim。
- [ ] Publisher 重启后能继续处理遗留的 PENDING/失败事件。
- [ ] 发布日志和持久化记录可从 workflowRunUuid 追溯到 eventId/messageId。
- [ ] 相关 Testcontainers 集成测试通过，quick Harness 不回归。

## 验证命令

```powershell
cd backend-java
mvn -Dtest=*OutboxPublisher*Test,*PublisherConfirm*Test,*RabbitMq*IT test
mvn test

cd ..
.\tools\verify.ps1 -Profile quick
.\tools\verify.ps1 -Profile integration
```

## 审查清单

- 是否先发 MQ 后写数据库，或未等 confirm 就标 PUBLISHED。
- 是否多实例扫描时存在读后写竞态。
- 是否发送失败后删除事件或无限紧密重试。
- 是否把 RabbitMQ confirm 当作 Consumer 已完成业务执行。
- 是否缺少消息版本、eventId、traceId 或 workflowRunUuid。

## 完成定义

- 数据库提交与消息投递之间的失败窗口可被 Outbox 记录和恢复。
- 下一步 Consumer 可以安全接收至少一次投递的 WorkflowRunMessage。
