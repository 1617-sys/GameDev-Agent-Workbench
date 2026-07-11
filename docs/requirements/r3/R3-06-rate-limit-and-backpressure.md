# R3-06: 用户限流与系统背压

> 状态：`TODO`
>
> 前置任务：`R3-01`、`R3-02`
>
> 推荐模型：`gpt-5.5`
>
> 任务类型：Redis 并发治理 / 接口测试

## 背景

异步化会让 HTTP 快速返回，但如果用户可以无限提交，队列会积压、模型成本失控，系统最终仍不可用。R3 需要在提交入口建立明确、可观测的限流和背压规则。

## 目标

在新的 Workflow 提交 API 前增加两层保护：

```text
request
-> per-user rate limit (Redis token bucket or sliding window)
-> system backlog / submission gate
-> R3-02 idempotent command
```

限流只负责拒绝或允许新提交，不取代数据库幂等和 Consumer 执行抢占。

## 范围

允许：

- 新增限流配置属性、Redis 原子脚本或成熟库的最小封装、限流结果 DTO。
- 按 `userId` 限制提交频率和/或并发未终态 WorkflowRun 数，并返回明确、可重试的错误码。
- 实现全局提交阈值/队列积压背压的最小策略，配置不可用时记录清晰降级/拒绝语义。
- 在 Controller/CommandService 边界接入限流，不改变 R2 旧同步入口的语义。
- 添加单元、Redis 集成和并发测试，验证不同用户隔离、窗口恢复、超限拒绝、Redis 异常策略。

## 非目标

- 不实现用户配额后台、计费、会员等级或前端限流展示。
- 不把 Redis 限流用作 Idempotency-Key 的唯一防线。
- 不直接修改 RabbitMQ 的消费重试/DLQ 逻辑。
- 不引入复杂 API Gateway 或云服务。
- 不允许因为限流异常而继续执行高成本 Agent 调用。

## 约束

- key 至少包含稳定 userId 和限流策略版本，避免不同环境/策略相互污染。
- 限流计数必须原子，不能用“先读再写”的并发不安全方式。
- Idempotency-Key 命中既有成功提交时，返回既有 Run 的语义优先于额外创建；限流与幂等的顺序必须在实现说明中固定并测试。
- Redis 异常的策略必须显式：对高成本的新提交默认拒绝或按设计降级，不能静默放行。
- 响应包含可用的业务错误码和合理 retry-after 信息，但不得暴露 Redis 内部 key/拓扑。
- 所有阈值可配置，测试使用小阈值，不写死生产数值。

## 验收标准

- [ ] 同一用户超过阈值时被拒绝，未创建 WorkflowRun/StepRun/OutboxEvent。
- [ ] 不同用户不会相互消耗额度。
- [ ] 时间窗口/令牌恢复后允许新的合法提交。
- [ ] 同一幂等键并发请求仍只产生一个 Run，限流不会破坏 R3-02 的返回语义。
- [ ] Redis 限流故障时遵循文档策略且不执行高成本任务。
- [ ] 系统背压触发时有可定位日志/指标字段与稳定错误响应。

## 验证命令

```powershell
cd backend-java
mvn -Dtest=*WorkflowRateLimit*Test,*RateLimiter*Test,*WorkflowSubmit*Test test
mvn test

cd ..
.\tools\verify.ps1 -Profile quick
.\tools\verify.ps1 -Profile integration
```

## 审查清单

- 是否用非原子的 get/set 实现限流。
- 是否限流拒绝后仍然写入 Outbox 或创建 WorkflowRun。
- 是否让 Redis 故障默认放行高成本 Agent 任务。
- 是否让一个用户的 key 与另一个用户冲突。
- 是否将限流当作幂等或 Consumer 防重的替代品。

## 完成定义

- 新异步提交具备用户级成本保护和系统背压边界。
- 限流与数据库幂等、消息执行职责清晰且各有测试保护。
