# R3-08: 异步链路集成与并发 Harness

> 状态：`TODO`
>
> 前置任务：`R3-01`、`R3-02`、`R3-03`、`R3-04`、`R3-05`、`R3-06`、`R3-07`
>
> 推荐模型：`gpt-5.5`
>
> 任务类型：Testcontainers 集成测试 / 并发回归

## 背景

R3 的价值不在于“能连上 RabbitMQ”，而在于可以用自动化证据证明异步提交、至少一次消息、并发消费、重试与恢复不会重复执行或丢失任务。本任务建立阶段级 Harness，而不是继续增加业务功能。

## 目标

以 Testcontainers MySQL + Redis + RabbitMQ 为真实依赖，建立可重复的验证矩阵：

```text
async submit
-> Outbox publish
-> duplicate message / concurrent consumer
-> R2 Runner
-> durable terminal state / retry / DLQ / recovery
```

并将可稳定自动化的验证收敛到 `tools/verify.ps1 -Profile integration`。

## 范围

允许：

- 新增端到端服务集成测试、并发测试工具、消息探针/测试 listener 和测试数据清理机制。
- 扩展 integration Harness，输出清晰的通过/失败信息和测试范围。
- 增加可控的 Fake/Stub AgentStepExecutor，统计调用次数并模拟成功、可重试失败、不可重试失败和中断。
- 编写 R3 并发测试报告草稿或测试说明，记录并发数、创建数、执行数、ACK/重试/DLQ 结果。
- 仅为测试可观测性增加最小 hooks，不改变生产业务语义。

## 非目标

- 不用真实付费 LLM、真实云 RabbitMQ 或真实生产凭证运行测试。
- 不改前端页面，也不做浏览器 E2E。
- 不添加与 R3 无关的性能压测平台。
- 不以 `Thread.sleep` 偶然通过作为并发测试证明。
- 不因测试方便而绕过真实 Outbox、Consumer 或数据库唯一约束。

## 约束

- 测试必须使用 CountDownLatch/Barrier 等方式真实并发启动请求或 Consumer，不得只循环调用。
- 每个测试隔离数据库、Redis key、queue 或使用唯一 namespace，不能依赖执行顺序。
- 断言业务事实：WorkflowRun/StepRun/Outbox/Artifact 状态和 Agent 调用次数，而不只断言 Mock 返回值。
- 对异步等待使用有上限的轮询/await 条件，并在失败时打印 workflowRunUuid、消息/Outbox 状态和关键日志证据。
- Fake Agent 必须明确标识为测试替身，不得伪装成真实模型成功。

## 验收标准

- [ ] 10 个相同幂等请求真实并发，只创建一个 WorkflowRun、StepRun 计划和有效 OutboxEvent。
- [ ] 同一 WorkflowRunMessage 重复投递或两个 Consumer 并发时，Runner/Agent 的有效执行次数为一次。
- [ ] Consumer 在最终状态落库前模拟中断后，可由重复投递或恢复扫描完成，且不重复 SUCCESS StepRun。
- [ ] 不可重试错误最终 FAILED；可重试错误按上限后进入可关联的 DLQ。
- [ ] Redis 锁拒绝、Outbox confirm 失败、MQ 临时不可用都有确定的持久化行为测试。
- [ ] `verify.ps1 -Profile integration` 可本地重复运行，不依赖个人服务或人工观察。

## 验证命令

```powershell
cd backend-java
mvn -Dtest=*AsyncWorkflow*IT,*WorkflowConcurrency*Test,*Outbox*IT,*DeadLetter*IT,*WorkflowRecovery*IT test

cd ..
.\tools\verify.ps1 -Profile integration
.\tools\verify.ps1 -Profile quick
```

## 审查清单

- 是否所谓“并发测试”实际串行运行。
- 是否通过 sleep 掩盖事件尚未完成的竞态。
- 是否 Mock 掉 Outbox、Redis 或 RabbitMQ 却声称完成集成测试。
- 是否只检查 HTTP 202，没有检查实际 Run/StepRun/消息结果。
- 是否遗漏重复消费、崩溃恢复、DLQ 关联和 Redis 异常任一关键场景。

## 完成定义

- R3 的并发和可靠性承诺拥有真实、可重复的自动化证据。
- integration Harness 可以阻止相同类别的消息可靠性回归进入主分支。
