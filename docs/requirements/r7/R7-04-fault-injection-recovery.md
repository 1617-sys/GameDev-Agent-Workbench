# R7-04: Redis、RabbitMQ、Python 故障注入与恢复报告

> 状态：`TODO`
>
> 前置任务：`R7-01`、`R7-02`
>
> 推荐模型：`gpt-5.5`
>
> 任务类型：韧性验证 / 故障时间线报告

## 背景

可靠性不能只在单元测试里成立。最终项目需要在完整环境中主动制造 Redis 不可用、RabbitMQ 中断、Python 超时/错误、Java Consumer 重启等故障，并证明状态不会假成功、消息不会静默丢失、恢复不会重复计费。

## 目标

建立受控故障矩阵：

```text
Redis unavailable/lock expiry/wrong owner
RabbitMQ unavailable/confirm timeout/consumer restart
Python timeout/429/invalid output/service restart
MySQL transient failure where safely reproducible
-> observe durable state, retry/DLQ/recovery/audit
-> restore dependency
-> verify final state and no duplicate successful work
```

## 范围

允许：

- 新增 Docker Compose fault profile、toxiproxy/受控网络脚本、fake Python failure modes和故障执行脚本。
- 验证 Outbox 保留/重发、Publisher confirm、手动 ACK、Redis fail-closed、重试/DLQ、heartbeat/recovery、SSE 快照恢复。
- 注入 Consumer 在关键状态落库前后重启，检查 StepRun/Artifact/Metric 幂等。
- 生成每种故障的时间线、预期、实际、恢复时长、数据状态、日志/trace 和结论。
- 仅修复可复现的阻断缺陷并补自动回归测试。

## 非目标

- 不在生产环境执行故障注入。
- 不做破坏磁盘、删除用户 volume、数据损坏或不可逆网络攻击。
- 不宣称单机 Compose 验证等同多机高可用。
- 不新增复杂混沌工程平台或云供应商依赖。
- 不用故障脚本绕过正常状态机直接修改终态。

## 约束

- 每次故障有明确开始/恢复命令、超时、安全停止和环境检查；默认只作用于本项目 Compose。
- 故障前记录 workflowRunUuid、traceId、消息/Outbox/Step 状态，故障后验证持久化事实而非只看日志。
- Redis 不可用时高成本执行默认拒绝/重试，绝不能无锁继续。
- RabbitMQ 失败时 Outbox 不得错误标 PUBLISHED；Consumer ACK 必须在业务持久化后。
- Python 超时/非法输出遵守有限重试和评测门禁，不产生虚假 SUCCESS。
- 恢复后已 SUCCESS StepRun/Artifact/Metric 不重复写入或重复计费。

## 验收标准

- [ ] Redis 故障/锁过期/错误 owner 不会导致未持锁执行或删除他人锁。
- [ ] RabbitMQ/confirm 故障保留 Outbox，恢复后最终投递且无重复有效执行。
- [ ] Consumer 重启/重复消息可恢复，SUCCESS StepRun/Artifact/Metric 不重复。
- [ ] Python 超时、429、非法输出按分类有限重试并进入正确终态/DLQ/评测失败。
- [ ] 页面/SSE 断开只影响展示，恢复后以持久化 snapshot 为准。
- [ ] 每个故障有可复现脚本和时间线报告，恢复后全量 Harness 通过。

## 验证命令

```powershell
.\tools\verify.ps1 -Profile integration
.\tools\run-fault-injection.ps1
.\tools\verify.ps1 -Profile e2e
```

## 审查清单

- 是否故障脚本可能影响其他 Docker 项目或删除 volume。
- 是否只看日志，没有验证数据库/队列/Artifact 状态。
- 是否恢复后重复 Agent 调用、Metric 或 Artifact。
- 是否 Redis/MQ/Python 失败被误标为 SUCCESS。
- 是否宣称单机验证等于生产高可用。

## 完成定义

- 核心依赖故障和恢复行为拥有真实环境证据，而非只靠设计说明。
- 项目可靠性边界、剩余风险和回滚手段可清楚讲解。
