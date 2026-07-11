# R3-00: 异步可靠执行设计冻结

> 状态：`TODO`
>
> 前置任务：`R2-验收`
>
> 推荐模型：`gpt-5.5`
>
> 任务类型：架构契约 / 只写文档

## 背景

R2 已将两条旧工作流收敛为同步 `WorkflowRunner`。现在 HTTP 请求仍可能长时间等待 Agent/LLM，且进程在数据库写入与实际执行之间崩溃时，任务投递、重复消费和失败恢复没有可靠边界。

R3 必须把“提交任务”和“执行任务”解耦，但不能重写 R2 Runner：

```text
HTTP submit
-> MySQL: WorkflowRun + StepRun + OutboxEvent
-> Outbox Publisher + RabbitMQ confirm
-> Consumer claim
-> R2 WorkflowRunner
-> durable state / ACK
```

## 目标

新增 `docs/requirements/r3/R3-async-reliability-design.md`，冻结以下契约：

- 提交幂等键、唯一约束、相同与冲突请求的返回语义。
- OutboxEvent 的数据模型、状态、发布重试、publisher confirm 与审计字段。
- RabbitMQ exchange、queue、routing key、retry queue、DLQ 和消息体/headers。
- Consumer 的手动 ACK、WorkflowRun 执行抢占、Redis 短锁与 MySQL 最终状态的协作方式。
- 错误分类、最大重试次数、延迟退避、终态状态和 DLQ 关联方式。
- 用户限流、服务重启恢复扫描、heartbeat 及 R3/R4/R5 边界。

## 范围

允许：

- 阅读 R2 Runner、R1 WorkflowRun/StepRun 数据模型、Docker Compose、Redis 服务和测试。
- 新增设计文档、状态图、时序图、字段表、消息样例和任务依赖图。
- 在文档中明确每个 R3 子任务可修改的目录、验证策略和回退方式。

## 非目标

- 不修改 Java、Python、Vue 的业务代码。
- 不新增前端 SSE 订阅、运行中心、取消页面或 Dashboard。
- 不实现 R5 的评测、模型成本或 R6 RAG。
- 不拆分独立微服务；Consumer 仍位于 Spring Boot 工程。
- 不对历史 WorkflowRun 做破坏性迁移或反向 SQL 回滚。

## 约束

- MySQL 是 WorkflowRun、StepRun、Outbox 的最终事实来源；Redis 和 RabbitMQ 不能替代它。
- 提交 API 的数据库事务中不得调用 Python Agent 或直接等待 RabbitMQ 消费。
- 发布消息只能从已提交 OutboxEvent 派生；不能先发 MQ 再写数据库。
- Consumer 的重复保护必须按 `workflowRunUuid + attempt` 与持久化状态判断，不能依赖 JVM 内存。
- R2 Runner 保持同步、无 RabbitMQ/HTTP/SSE 依赖；R3 只从 Consumer 调用它。
- 所有自动恢复与重试必须可追踪，不能静默修改状态。

## 验收标准

- [ ] 文档定义完整的数据、消息、状态和失败处理链路。
- [ ] 明确 `Idempotency-Key` 的业务维度、唯一约束和重复请求返回规则。
- [ ] 明确 Outbox 的状态机及 confirm 成功/失败/超时后的处理。
- [ ] 明确 Consumer ACK/NACK 时机和重复消息处理。
- [ ] 明确每类错误是否重试、最大次数和最终去向。
- [ ] 明确 R3 不实现的 R4 SSE 订阅、R5 评测、R6 RAG 能力。

## 验证命令

```powershell
git diff --check
rg -n "Idempotency|Outbox|publisher confirm|ACK|DLQ|heartbeat|WorkflowRunner" docs\requirements\r3\R3-async-reliability-design.md
```

## 审查清单

- 是否让 HTTP Controller 或 R2 Runner 直接依赖 RabbitMQ 的 ACK API。
- 是否把 Redis 锁当成提交幂等的最终保证。
- 是否遗漏数据库提交成功但消息未发布的场景。
- 是否允许无限重试或未记录 retryCount。
- 是否遗漏消费进程崩溃、重复投递、锁失效和恢复扫描的边界。

## 完成定义

- R3 的可靠性语义、消息拓扑和实施顺序已经冻结。
- 后续子任务不需要临时猜测 ACK、重试或幂等策略。
