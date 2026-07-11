# R3-08 异步集成与并发 Harness

## 自动化证据

`AsyncWorkflowIntegrationHarnessIT` 使用 Testcontainers 启动 MySQL、Redis 与 RabbitMQ；Docker 不可用时由 Testcontainers 明确跳过，不会降级为 Mock。

| 场景 | 并发方式 | 持久化断言 | 结果 |
| --- | --- | --- | --- |
| 相同幂等提交 | 10 个线程由 CountDownLatch 同时放行 | 一个 Run 与一个 PENDING Outbox intent | 自动化 |
| 重复消息投递 | 同一 payload 发布两次，两个 Consumer 同时轮询 | 一个 durable execution claim，执行计数为一 | 自动化 |

Harness 使用唯一表/队列 namespace，不依赖执行顺序；等待均有上限并在失败时包含队列或业务对象证据。它是集成验证工具，不替代生产 Agent，也不使用真实付费模型或生产凭证。

运行：

```powershell
.\tools\verify.ps1 -Profile integration
```
