# R3-04: Workflow Consumer、执行抢占与手动 ACK

> 状态：`TODO`
>
> 前置任务：`R3-03`
>
> 推荐模型：`gpt-5.5`
>
> 任务类型：异步消费 / 并发正确性

## 背景

RabbitMQ 至少一次投递意味着同一工作流消息可重复到达，也可能有多个 Consumer 同时收到同一个 Run 的执行请求。R3 必须让 Consumer 把消息安全地交给 R2 Runner，而不重复调用 Agent 或重复生成 Artifact。

## 目标

实现以下消费链路：

```text
WorkflowRunMessage
-> validate message / load WorkflowRun
-> Redis owner-aware execution lock (fast exclusion)
-> MySQL conditional claim (final authority)
-> WorkflowRunner.run(workflowRunUuid)
-> durable StepRun/WorkflowRun terminal state
-> manual ACK
```

## 范围

允许：

- 新增 WorkflowMessageConsumer、消息反序列化/校验、Consumer 配置和手动 ACK。
- 实现 WorkflowRun 的条件状态抢占、attempt/version 校验、heartbeat 初始化和结果持久化。
- 复用或抽取 Redis owner token lock，key 为 `workflow:execute:{workflowRunUuid}`，保留 Lua compare-and-delete 释放。
- 调用 R2 `WorkflowRunner`，并将 Consumer/锁/ACK 保持在 Runner 外层。
- 增加重复消息、两个 Consumer 并发、已 SUCCESS/FAILED/CANCELED Run、Redis 不可用、Runner 失败、ACK 时机测试。

## 非目标

- 不实现延迟重试/DLQ 具体路由，该工作由 R3-05 完成。
- 不处理提交幂等或用户限流。
- 不实现前端订阅、取消 API、人工重试界面。
- 不修改 R2 Runner 的同步核心接口以适应 RabbitMQ 类型。

## 约束

- 手动 ACK 只能发生在业务状态已可靠持久化之后；不得收到消息立即 ACK。
- `SUCCESS`、`FAILED`、`CANCELED` 等终态 Run 收到重复消息时不得调用 Runner，应记录原因并 ACK。
- Redis 锁是快速互斥层；MySQL 条件更新/状态版本是最终执行抢占依据。
- Redis 获取/释放异常时默认拒绝执行高成本 Agent 调用，并按错误分类交给 R3-05；绝不能继续执行。
- 锁 owner 必须唯一，只有 owner 能原子释放；锁获得失败不能释放他人的锁。
- 若 MySQL claim 受影响行数为 0，当前 Consumer 不得执行 Runner；应 ACK 或按文档规则短暂重投。
- Consumer 不得开启覆盖整个 Runner/Agent 调用的数据库事务。

## 验收标准

- [ ] 同一消息重复投递两次，只发生一次有效 Runner/Agent 执行。
- [ ] 两个 Consumer 同时消费同一 Run，只有一个获得最终执行权。
- [ ] 终态 Run、无效 Run、attempt 不匹配消息均不会调用 Runner。
- [ ] Runner 成功后先持久化最终状态再 ACK。
- [ ] Runner 异常、Redis 不可用和进程模拟中断均不会产生错误 ACK 或无主锁释放。
- [ ] 测试可证明 Redis 锁与 MySQL claim 任何一个失败时都不执行高成本步骤。

## 验证命令

```powershell
cd backend-java
mvn -Dtest=*WorkflowMessageConsumer*Test,*WorkflowExecutionClaim*Test,*WorkflowRunner*Test,*RedisService*Test test
mvn test

cd ..
.\tools\verify.ps1 -Profile quick
.\tools\verify.ps1 -Profile integration
```

## 审查清单

- 是否收到消息立即 ACK，或 Runner 失败时 ACK 了未落库的状态。
- 是否只依赖 Redis 锁而忽略 MySQL 条件状态更新。
- 是否因重复消息/终态消息再次调用 Runner。
- 是否在 `finally` 无条件删除 Redis 锁。
- 是否把 RabbitMQ `Channel`、ACK 或 DTO 注入 R2 Runner。

## 完成定义

- Consumer 在至少一次投递、并发 Consumer 和 Redis 故障下仍能保护一次有效执行。
- R2 Runner 可原样作为消息消费后的同步执行内核。
