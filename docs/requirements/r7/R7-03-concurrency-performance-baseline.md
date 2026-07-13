# R7-03: 并发、吞吐与性能基线报告

> 状态：`TODO`
>
> 前置任务：`R7-01`、`R7-02`
>
> 推荐模型：`gpt-5.5`
>
> 任务类型：并发压测 / 性能分析与报告

## 背景

R3 已验证幂等、重复消费和恢复语义，但最终作品需要在固定环境下量化接口延迟、队列积压、执行吞吐和资源瓶颈，并证明并发不会产生重复 WorkflowRun、模型调用或 Artifact。

## 目标

建立分层性能基线：

```text
submit API load
-> idempotent same-key and unique-key cohorts
-> Outbox publish / MQ queue / Consumer throughput
-> fake Agent latency profiles
-> workflow completion and SSE query load
-> report P50/P95/P99, throughput, errors, backlog, duplicates, resources
```

## 范围

允许：

- 新增 k6/JMeter/自有受控脚本或现有工具的最小压测配置、数据生成和报告模板。
- 测试同 key 并发、不同用户/项目并发、重复 MQ 消息、两个 Consumer 抢占、查询/SSE 读负载。
- 使用固定 fake Agent 延迟/错误率，分别测系统开销与外部 Provider 约束。
- 记录请求数、成功/拒绝/失败、有效 Run、Agent 调用、重复 Artifact、P50/P95/P99、队列峰值、CPU/内存/连接池。
- 根据证据做有限配置调优，并保留调优前后对比和回归测试。

## 非目标

- 不追求生产级百万并发、分布式压测或营销数字。
- 不用真实付费模型作为高并发负载目标。
- 不通过关闭幂等、评测、审计或可靠投递来提升数字。
- 不进行无证据的大规模重构。
- 不将开发笔记冒充可重复报告。

## 约束

- 报告必须记录硬件、Docker 资源、commit、配置、数据规模、并发模型、持续时间和 fake/real Provider。
- 同 key 并发必须断言只创建一个有效 WorkflowRun；重复消费必须断言一次有效 Agent/Artifact。
- 压测分离 API 接收性能、消息/Runner 性能和外部 Agent 延迟，避免把 Provider 慢误判为 Java 慢。
- 所有压测有安全上限、超时和停止条件，不能耗尽本机/外部账号资源。
- 调优后必须运行 quick/integration/e2e 回归，不接受只提升性能却破坏语义。

## 验收标准

- [ ] 固定环境下产生可重复的提交、消费、完成、查询性能基线与资源数据。
- [ ] 10 个及更高并发同 Idempotency-Key 仍只有一个有效 Run/执行/Artifact。
- [ ] 重复消息和并发 Consumer 不产生重复模型调用或计费记录。
- [ ] 报告包含 P50/P95/P99、吞吐、错误率、队列峰值、资源和瓶颈分析。
- [ ] fake Agent 与可选真实 Provider 结果分开，不作无效横向比较。
- [ ] 所有调优有前后证据，功能/可靠性 Harness 继续通过。

## 验证命令

```powershell
.\tools\verify.ps1 -Profile integration
.\tools\verify.ps1 -Profile e2e
.\tools\run-performance-baseline.ps1
```

## 审查清单

- 是否报告缺少环境/commit/配置，无法复现。
- 是否用真实模型压测造成成本风险。
- 是否只报告平均值，遗漏 P95/P99、错误、积压和重复业务事实。
- 是否通过绕过可靠性/评测提高性能。
- 是否混淆 Provider 延迟与应用瓶颈。

## 完成定义

- 项目有可信、可复现的性能数字与并发正确性证据。
- 你能解释系统瓶颈、容量边界和下一步扩展方向。
