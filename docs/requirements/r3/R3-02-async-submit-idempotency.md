# R3-02: 异步提交 API、数据库幂等与初始 Outbox

> 状态：`TODO`
>
> 前置任务：`R3-00`、`R3-01`
>
> 推荐模型：`gpt-5.5`
>
> 任务类型：提交命令 / 数据库并发控制

## 背景

当前旧入口会在 HTTP 请求中执行整个工作流。R3 的新提交入口必须立即持久化一个可恢复的 WorkflowRun，再异步投递执行意图；同一用户误点或网络重试不能创建重复运行。

## 目标

新增兼容的新提交链路：

```http
POST /api/v1/projects/{projectUuid}/workflow-runs
Idempotency-Key: client-generated-key
```

在同一个短数据库事务中完成：

```text
权限/参数校验
-> 选择定义与 Prompt 快照
-> insert WorkflowRun(status=PENDING or QUEUED)
-> insert initial WorkflowStepRun plan
-> insert OutboxEvent(WORKFLOW_RUN_REQUESTED)
-> 202 + workflowRunUuid
```

## 范围

允许：

- 新增请求/响应 DTO、Controller、CommandService 和查询必要的 VO。
- 新增或扩展 Flyway migration：`idempotency_key`、必要唯一索引、OutboxEvent 表及其初始状态字段。
- 复用 R1 Snapshot 和 R2 StepPlan，为新 Run 持久化冻结的定义、Prompt、输入和初始 StepRun。
- 实现 MySQL 唯一约束兜底、重复请求读取第一次创建的 WorkflowRun、并发测试。
- 新增 `WorkflowRunSubmitted` 领域/Outbox 事件记录，但不在本任务发布到 MQ。
- 保留旧同步 API，不修改其请求与响应。

## 非目标

- 不在 Controller 或 CommandService 直接调用 WorkflowRunner、Python Agent 或 RabbitMQ。
- 不实现 Outbox publisher confirm 或 Consumer。
- 不接用户 Redis 限流、执行锁、重试/DLQ 或恢复扫描。
- 不改前端提交页面；R4 才迁移 UI。
- 不删除旧同步 API。

## 约束

- 业务幂等键固定为 `userId + projectId + workflowKey + Idempotency-Key`；必须由数据库唯一约束兜底。
- `Idempotency-Key` 缺失、空白、过长或非法时在创建任何 WorkflowRun 前拒绝。
- 相同幂等键、相同规范化请求返回第一次创建的 Run；相同键但请求语义冲突必须返回明确冲突，不可悄悄复用。
- 新接口在事务成功后返回 `202 Accepted`，不得等待 Agent/LLM、RabbitMQ confirm 或 SSE。
- MySQL 唯一冲突必须被转换为“读取既有 Run”的正常并发路径，不得向用户暴露重复键异常。
- 插入 WorkflowRun、StepRun、OutboxEvent 必须原子成功或一起回滚。

## 验收标准

- [ ] 新 API 在不调用 Agent/Runner 的情况下返回 `202` 与 `workflowRunUuid`。
- [ ] 同一幂等键的串行和 10 个并发请求只创建一个 WorkflowRun、一组初始 StepRun、一个有效 OutboxEvent。
- [ ] 不同用户或不同项目使用相同幂等键互不冲突。
- [ ] 同一键但请求体/WorkflowKey 不一致被明确拒绝。
- [ ] 数据库事务失败时不存在半成品 WorkflowRun、StepRun 或 OutboxEvent。
- [ ] 旧同步 API 与 R2 测试保持通过。

## 验证命令

```powershell
cd backend-java
mvn -Dtest=*WorkflowSubmit*Test,*Idempotency*Test,*OutboxEvent*Test test
mvn test

cd ..
.\tools\verify.ps1 -Profile quick
.\tools\verify.ps1 -Profile integration
```

## 审查清单

- 是否只使用 Redis 或 JVM Map 进行幂等，而没有数据库唯一约束。
- 是否先查后插而未处理并发唯一冲突。
- 是否在提交请求里执行 Runner、Agent 或等待消息被消费。
- 是否将相同 key 的不同请求错误地视为同一次提交。
- 是否出现 WorkflowRun 已创建但 StepRun/Outbox 未写入的事务裂缝。

## 完成定义

- 新提交 API 能可靠、幂等地创建可异步执行的工作流意图。
- 即使尚未接入 Publisher，数据库中也有可恢复的执行记录和 Outbox 事实。
