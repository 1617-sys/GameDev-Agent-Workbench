# Messaging 模块

## 职责

该模块负责把 MySQL 中已经提交的工作流投递意图交给 RabbitMQ，并驱动与消息传输无关的 `WorkflowRunner`。

```text
HTTP 提交
  -> 同一事务写 WorkflowRun / StepRun / RunEvent / OutboxEvent
  -> OutboxPublisher 数据库租约抢占
  -> RabbitMQ publisher confirm
  -> WorkflowRun: PENDING -> QUEUED
  -> WorkflowMessageConsumer
  -> Redis 快速防重 + 数据库状态抢占
  -> WorkflowRunner
  -> ACK / Retry / DLQ
```

## 一致性语义

- RabbitMQ 使用 **at-least-once** 投递，不保证 exactly-once。
- `publisher confirm` 只表示 Broker 接受消息，不表示工作流执行成功。
- Redis 锁用于快速阻挡并发重复处理，数据库的 `status + attempt + statusVersion` 条件更新才是最终执行权。
- API 幂等键识别同一次操作，请求指纹防止相同键携带不同请求。
- 工作流步骤必须保持幂等，因为进程可能在外部副作用成功后、成功状态落库前退出。

## 状态迁移

| 当前状态 | 事件 | 目标状态 | 条件 |
|---|---|---|---|
| `PENDING` | Outbox confirm | `QUEUED` | Broker 已接收消息 |
| `QUEUED` | Consumer claim | `RUNNING` | attempt 与 statusVersion 匹配 |
| `RUNNING` | 所有步骤成功 | `SUCCESS` | Runner 完成 |
| `RUNNING` | 可重试异常 | `RETRY_WAIT` | 未超过重试预算 |
| `RETRY_WAIT` | 延迟消息到达 | `QUEUED` | 条件更新成功 |
| `RUNNING` | 不可重试异常 | `FAILED` | 错误分类为终态 |
| 任意非终态 | 用户取消 | `CANCELED` | 用户拥有该运行 |

## 当前限制

1. Redis 锁没有自动续租，执行超过 TTL 时只能依赖数据库抢占和业务幂等继续保护。
2. `SynchronousWorkflowRunner` 当前会先写 `FAILED`，与 Consumer 的 `RUNNING -> RETRY_WAIT` 条件迁移冲突。
3. 延迟重试发送没有使用 Outbox，进程在写 `RETRY_WAIT` 后、发送消息前退出会产生丢唤醒窗口。
4. 恢复扫描目前处理 `PENDING/QUEUED/RUNNING`，不处理 `RETRY_WAIT`。

这些限制是当前实现边界，修改消息或状态代码时不得通过放宽条件更新来掩盖竞争问题。
