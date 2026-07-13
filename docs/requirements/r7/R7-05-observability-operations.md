# R7-05: 日志、指标、Trace 与运维诊断闭环

> 状态：`TODO`
>
> 前置任务：`R7-02`、`R7-03`、`R7-04`
>
> 推荐模型：`gpt-5.5`
>
> 任务类型：可观测性 / 故障定位验证

## 背景

系统跨越 HTTP、MySQL、Redis、RabbitMQ、Java Worker、Python Agent、Evaluation 和 SSE。只有业务正确但无法定位故障，仍然不具备工程交付价值。R7 需要让一次 WorkflowRun 可以从入口追踪到最终 Artifact。

## 目标

建立统一诊断能力：

```text
traceId + workflowRunUuid + stepRunUuid + agentRunUuid + messageId
-> structured logs across Java/Python
-> workflow/message/provider/evaluation/retrieval metrics
-> health/readiness and safe diagnostics
-> example troubleshooting queries/runbook
```

## 范围

允许：

- 统一 Java/Python 结构化日志字段、MDC/上下文传播、错误分类和脱敏规则。
- 完善 Actuator/Micrometer 或现有指标入口：提交、排队、执行、重试、DLQ、评测、RAG、Provider 延迟/错误、SSE 连接。
- 增加 health/readiness、队列/依赖诊断的安全端点与本地可视化/采集配置。
- 编写 `docs/operations-runbook.md`：常见告警、查询、Run 时间线、恢复/回滚和禁止操作。
- 添加 trace 传播、日志脱敏、metric 标签基数、健康状态和故障定位测试。

## 非目标

- 不搭建企业级 ELK/Jaeger/Prometheus 高可用集群。
- 不记录完整 Prompt、文档正文、Authorization、Token、密码、API Key 或用户隐私。
- 不把高基数 UUID 作为所有指标标签。
- 不通过日志修改业务状态或用日志替代审计表。
- 不新增与项目无关的运维平台。

## 约束

- traceId 在 HTTP、Outbox、MQ、Consumer、Runner、Python 和评测链路传播；缺失时在边界生成并记录。
- WorkflowRun UUID 等高基数数据进入日志/trace，不默认作为 Prometheus 标签。
- 错误日志包含安全分类和关联 ID，不输出完整请求/Prompt/模型原文/Secret。
- health/readiness 必须反映关键依赖与 migration 状态，但公开端点不得暴露配置、拓扑和凭证。
- 指标定义包含单位、标签、计数时机和分母，避免重复消息导致重复计数。
- 运维文档中的恢复命令默认非破坏性，破坏性操作醒目标记。

## 验收标准

- [ ] 给定 workflowRunUuid/traceId 可从提交追踪到 Outbox、消息、Step、Agent、评测、RAG 和 Artifact。
- [ ] Redis/MQ/Python 故障报告中的关键状态能通过日志与指标解释。
- [ ] 提交量、队列/执行耗时、重试/DLQ、Provider/评测/RAG 核心指标可查询且口径明确。
- [ ] 日志、metrics、health endpoint 不泄露 Secret/Prompt/文档正文或产生失控高基数。
- [ ] runbook 能指导定位卡住任务、重复消息、评测失败、RAG 空检索和 Provider 超时。
- [ ] 可观测性测试与 quick/integration/e2e Harness 通过。

## 验证命令

```powershell
cd backend-java
mvn -Dtest=*Observability*Test,*Trace*Test,*Health*Test,*Security*Test test
mvn test

cd ..\python-agent
python -m pytest

cd ..
.\tools\verify.ps1 -Profile integration
```

## 审查清单

- 是否高基数 run UUID 被作为指标标签导致爆炸。
- 是否日志/诊断端点泄露 Prompt、文档或 Secret。
- 是否 trace 在 MQ/Python 边界丢失。
- 是否指标在重复消息/重试时重复计数。
- 是否 runbook 只有架构描述，没有可执行定位步骤。

## 完成定义

- 一次跨服务 WorkflowRun 的成功和失败都能用关联 ID、指标和 runbook 定位。
- 可观测性成为 R7 报告和面试讲解的真实工程证据。
