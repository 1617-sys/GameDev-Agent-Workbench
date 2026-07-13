# 简历项目描述：GameDev Agent Workbench

以下内容只使用仓库可验证事实。投递前按自己的实际参与范围删改“主导/负责”等词，不要添加线上用户、收入、生产 QPS、真实模型效果或本报告没有的数据。

## 一行版

设计并审查 Spring Boot + FastAPI + Vue 的 AI 游戏工作流工程，围绕幂等提交、Outbox/RabbitMQ、SSE 恢复、RAG 引用 provenance、分层评测与 Phaser 产物建立可审计链路，并用失败保真的 R7 Harness 明确发布阻断项。

## 三行版

- 将游戏创意生成拆为冻结定义驱动的四步 Workflow，持久化 Run/Step/Agent/Metric/Evaluation/Artifact，并以 MySQL 短事务、Outbox、RabbitMQ Consumer、Redis owner lock 处理异步与重复执行边界。
- 建设项目隔离的知识/RAG 链路，记录 Python 实际使用的 document/chunk/version/rank/score，区分 RAG-off/empty/failure/mock；以 Schema/Rule/Runtime 三层证据控制 GameConfig 可玩资格。
- 采用“契约—AI 辅助实现—自动化 Harness—人工 diff/安全审查”流程；R7-06 运行记录 142 项 Java、8 项 Python 测试和前端依赖审计 0 告警，同时如实保留 `50302` 导致的 E2E/性能/Demo BLOCKED 报告。

## 详细版

**项目背景**

模型调用 Demo 通常缺少权限隔离、可靠消息、失败恢复、引用来源与可验证产物。项目将自然语言游戏创意放进完整工程链路：版本化 Workflow、Agent/RAG、评测、运行中心和 Phaser runtime。

**可按实际选用的职责表述**

- 负责需求契约和领域边界拆解，将 WorkflowDefinitionVersion、WorkflowRun、WorkflowStepRun、PromptVersion 与 Artifact 设计为可追溯快照；仓库包含 26 个顺序 Flyway migration。
- 设计异步提交边界：HTTP 事务只创建幂等 WorkflowRun 与 Outbox intent，模型 I/O 由 RabbitMQ Consumer 驱动；用 durable claim、状态版本、owner lock 和恢复 audit 避免重复成功与手工改终态。
- 设计 Run Center 的服务端 read model、持久化 sequence 与 SSE snapshot/replay；客户端处理重复/乱序/断线并只按服务端 `allowedActions` 发起取消或重试。
- 实现并审查知识上传约束、project/document 双过滤、RAG 预算协议与 RetrievalRecord provenance；只展示实际引用，不把重新检索结果冒充运行证据。
- 将 GameConfig 产物接入 Schema/Rule/Runtime 分层评测和 eligibility；明确 mock 指标与真实 Provider 分离，不以固定 fixture 延迟宣传模型性能。
- 建立 quick/integration/e2e/performance/fault/observability/security/demo 分层 Harness，审查退出码、环境 skip、关联 ID、脱敏证据和 staged diff；发布材料只引用报告结论。
- 使用 AI 辅助代码探索、候选实现、测试与文档生成；人工负责契约、事务/状态机、权限、风险归属、验证结果和最终提交审查。

## 可验证量化点

| 表述 | 证据 | 使用限制 |
| --- | --- | --- |
| 26 个顺序 Flyway migration | [`db/migration`](../backend-java/src/main/resources/db/migration) | 表示演进规模，不表示生产迁移已在所有环境通过 |
| 四步 `DEMO_GAME_CONFIG` Runner | [R2 报告](reports/R2-workflow-runner-report.md) | R2 同步 Runner 通过；R7 异步 E2E 仍阻断 |
| R6 运行记录 131 Java、3 Python、20 前端 unit、6 browser E2E、2 runtime smoke | [R6 报告](reports/R6-rag-knowledge-report.md) | Java/集成含环境 skip，必须保留该限定 |
| R7-05 运行记录 136 Java、6 Python、10 observability 目标测试 | [R7 可观测报告](reports/R7-observability-operations-report.md) | 成功/失败/恢复 Run 关联未完成 |
| R7-06 运行记录 142 Java、8 Python，npm audit 0 | [R7 安全审计](reports/R7-security-release-audit.md) | Docker/image/Maven/Python CVE gate 仍 BLOCKED |
| Demo prepare 44.2 秒到达提交边界、reset 6.7 秒 | [R7 Demo 报告](reports/R7-demo-reproducibility-report.md) | 提交返回 `50302`，不能写成完整 Demo 成功 |

## 不应写入简历的表述

- “支持高并发/达到某 P95/吞吐”：R7 性能 measurement 未开始，没有可发布数字。
- “生产可用/高可用/零丢失”：当前是单机 Compose，故障矩阵大部分被前置阻断。
- “RAG 提升模型效果”：真实同条件三层评测未闭环，fake embedding/vector 不代表语义质量。
- “完整安全合规”：镜像与完整 CVE 扫描、Compose 双用户、Python 生产模式仍缺证据。
- “全部代码独立手写”：项目采用 AI 辅助流程，应说明自己承担的审查和决策责任。

## 面试展开顺序

1. 先讲问题：模型调用如何变成可恢复、可追溯业务链路。
2. 再画边界：Java 状态/MQ，Python Agent/RAG，Vue SSE/Phaser，MySQL/Redis/RabbitMQ。
3. 深挖一个取舍：Outbox + 幂等 Consumer，或实际 RetrievalRecord provenance。
4. 展示验证：报告、命令、失败证据和为什么环境 skip 不算 PASS。
5. 主动说明当前阻断与修复优先级，避免把计划包装为能力。

完整讲解见[项目叙事](project-narrative.md)，追问准备见[面试问答](interview-qa.md)。
