# R3 验收: 异步任务、幂等与可靠性总验收

> 状态：`TODO`
>
> 前置任务：`R3-00`、`R3-01`、`R3-02`、`R3-03`、`R3-04`、`R3-05`、`R3-06`、`R3-07`、`R3-08`
>
> 推荐模型：`gpt-5.5`
>
> 任务类型：阶段验收 / 只验证与记录

## 背景

R3 的完成标志不是引入了 RabbitMQ，而是新工作流提交已经脱离 HTTP 长请求，并在数据库、Redis、RabbitMQ、Consumer 崩溃和重复投递等现实条件下保持可解释、可恢复的行为。

## 目标

新增 `docs/reports/R3-async-reliability-report.md`，用可复现证据证明：

```text
202 async submit
+ MySQL idempotency
+ Transactional Outbox + publisher confirm
+ manual-ACK Consumer + execution claim
+ finite retry + DLQ
+ rate limit + recovery scanner
+ Testcontainers concurrency Harness
= R4 可以消费的可靠运行状态
```

## 范围

允许：

- 运行 R3、R2、R1、R0 相关测试、Docker Compose config、quick/integration Harness。
- 审查消息拓扑、数据库迁移、ACK 时机、状态机、锁、限流、恢复和敏感配置。
- 新增 R3 验收报告、更新 R3 任务卡状态、记录风险与 R4 准入结论。
- 仅修复阻断验收的最小缺陷，并同时补回归测试。

## 非目标

- 不实现 R4 前端运行中心、SSE 订阅、页面刷新恢复体验。
- 不实现 R5 评测/指标、R6 RAG、R7 展示材料。
- 不删除旧同步 API 或旧 Demo SSE API。
- 不进行破坏性数据回滚、无关架构重写或生产部署。

## 验收项目

### 异步提交

- 新提交 API 返回 `202`，不等待 Agent/LLM。
- Idempotency-Key 由 MySQL 唯一约束兜底；相同请求复用既有 Run，冲突请求被拒绝。
- 事务内 WorkflowRun、StepRun、OutboxEvent 一起成功或回滚。

### 可靠投递与消费

- Outbox 仅在 confirm 后标记 PUBLISHED，失败可扫描恢复。
- Consumer 手动 ACK 在持久化业务状态后发生。
- 重复消息、终态消息、并发 Consumer 只产生一次有效执行。
- Redis 执行锁与 MySQL 条件抢占共同生效，任一失败不执行高成本步骤。

### 失败与恢复

- 不可重试错误进入 FAILED；可重试错误退避且有最大次数。
- DLQ 消息可关联 workflowRunUuid、eventId/messageId、重试次数和最后错误。
- PENDING/QUEUED/RUNNING 的异常停滞可通过恢复扫描得到可解释处理。
- 限流/背压拒绝不会创建半成品运行或绕过成本保护。

### 回归边界

- R2 WorkflowRunner 仍不依赖 HTTP/SSE/RabbitMQ。
- 旧同步 API 与 Demo SSE 入口保持兼容。
- 前端 GameConfig 契约测试、Vue build 与基础安全检查不回归。

## 验收标准

- [ ] 提交 API 不等待 LLM，返回 `202` 与可查询的 workflowRunUuid。
- [ ] 10 个并发相同幂等请求只创建一个有效 WorkflowRun。
- [ ] 同一消息重复投递或并发消费只产生一次有效 Runner/Agent 执行。
- [ ] Consumer 在步骤落库前中断后，可由重新投递或恢复扫描完成且无重复 SUCCESS 步骤。
- [ ] 不可重试错误进入 FAILED；可重试错误按上限退避并最终进入可关联 DLQ。
- [ ] Redis/MQ/DB 临时异常均有可持久化、可审计的最终行为。
- [ ] quick、integration Harness 与全量 Maven 测试通过，生成 R3 报告并给出 R4 准入结论。

## 验证命令

```powershell
git status --short
git diff --check
docker compose config

cd backend-java
mvn test

cd ..\frontend-vue
npm run test:game-config
npm run build

cd ..
.\tools\verify.ps1 -Profile quick
.\tools\verify.ps1 -Profile integration

rg -n "@RabbitListener|Acknowledge|Outbox|Idempotency-Key|workflow:execute:|DeadLetter|retryCount|heartbeat" backend-java\src\main\java
rg -n "161764|password:\s*123456|local-secret-key|change-this-to-a-strong-secret-key|\bsk-[A-Za-z0-9]{20,}" `
  backend-java frontend-vue python-agent .env.example docker-compose.yml
```

## R3 报告模板

```markdown
# R3 Async Reliability Report

## 环境
- 日期：
- 分支：
- 基线 commit：
- Testcontainers/Docker：

## 异步提交与幂等
- API 202：
- 幂等唯一约束：
- 并发结果：

## Outbox 与消息消费
- publish/confirm：
- duplicate consume/claim：
- ACK 证据：

## Retry、DLQ、恢复
- 错误分类：
- retry/DLQ：
- recovery scanner：

## Harness 结果
| 命令 | 结果 | 证据 |

## 已知风险
- 风险：
- 归属阶段：R4 / R5 / R7

## R4 准入结论
- PASS / BLOCKED
- 原因：
```

## 审查清单

- 是否把“RabbitMQ 可用”误当成“消息可靠”。
- 是否遗漏数据库唯一约束、Outbox confirm、手动 ACK、执行抢占任一层。
- 是否有无限 requeue、重复计费或终态 Run 再执行风险。
- 是否让恢复扫描覆盖活跃任务或绕过 Outbox。
- 是否把 R4 前端订阅/取消体验提前塞进 R3。
- 是否用 Mock 或页面观察替代 Testcontainers 并发证据。

## 完成定义

- R3 验收报告与任务状态已更新，所有关键可靠性承诺有自动化证据。
- R4 可以只围绕持久化 WorkflowRun/StepRun 做查询和 SSE 订阅，不再依赖长连接承载执行。
