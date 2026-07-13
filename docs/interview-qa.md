# 面试问答：GameDev Agent Workbench

回答原则：先说业务问题，再说取舍、失败边界和证据。凡是当前报告为 `BLOCKED` 的内容，不回答成“已经在生产验证”。

## 1. 这个项目不是普通模型调用 Demo 的地方在哪里？

它把模型调用放进了用户/项目权限、版本化工作流、异步消息、持久化状态、RAG 来源、评测门禁和可恢复 UI 中。模型输出只是 `AgentRun` 的一部分；最终还要关联 Step、Metric、RetrievalRecord、EvaluationReport 和 Artifact。架构见[系统架构](architecture/system-architecture.md)，领域和 Runner 证据见[R1](reports/R1-workflow-domain-report.md)、[R2](reports/R2-workflow-runner-report.md)。

反面边界：当前 R7 主链路被 `50302` 阻断，所以不能声称整个异步闭环已经通过发布验收。

## 2. 为什么业务状态放 Java，Agent 放 Python？

Java 侧需要稳定处理鉴权、事务、状态机、幂等、Outbox、查询和审计；Python 侧更适合维护模型 SDK、Pydantic 输入输出和 RAG 上下文。跨服务协议让“业务成功”与“模型响应”分开：Python 返回显式 status/mock/RAG provenance，Java 决定是否写 Metric、Evaluation 和 Artifact。

没有把状态也放 Python，是为了避免两个服务同时成为 WorkflowRun 的事实源。代码入口是[`PythonAgentClient.java`](../backend-java/src/main/java/com/example/gameworkbench/client/PythonAgentClient.java)和[`python-agent/app`](../python-agent/app)。

## 3. 一次异步提交如何保证短事务和幂等？

`POST /api/v1/projects/{projectUuid}/workflow-runs` 要求 `Idempotency-Key`。提交事务内创建 WorkflowRun 和 Outbox intent，模型调用不在 HTTP 事务内；重复键应读取同一业务事实。之后 Outbox Publisher 通过 RabbitMQ confirm 更新投递结果。

为什么不用“HTTP 请求里直接跑完”：模型延迟和失败会占用连接，客户端重试还会扩大重复执行窗口。实现入口见[`AsyncWorkflowSubmissionServiceImpl.java`](../backend-java/src/main/java/com/example/gameworkbench/service/impl/AsyncWorkflowSubmissionServiceImpl.java)和[`OutboxPublisher.java`](../backend-java/src/main/java/com/example/gameworkbench/service/impl/OutboxPublisher.java)。当前 R7 的 Redis gate 缺陷发生在创建 durable run 之前，已如实记录在[E2E 报告](reports/R7-main-workflow-e2e-report.md)。

## 4. 为什么既需要数据库幂等，也需要 Redis 锁？

数据库唯一约束/状态版本保护“最终只能有一个有效业务事实”；Redis 锁保护高成本执行窗口，减少并发模型调用。两者不能互相替代：锁会过期或暂时不可用，数据库才是 durable correctness；数据库唯一约束也不能阻止两个 worker 在提交前同时发起昂贵外部调用。

失败策略是 fail-closed、owner token 原子释放，错误 owner 不得删除其他锁。相关验证见[R0 报告](reports/R0-baseline-report.md)和[R7 故障报告](reports/R7-fault-injection-recovery-report.md)。

## 5. RabbitMQ 重复投递时怎样避免重复成功？

Consumer 先取得持久化 execution claim，再执行冻结工作流；成功 Step、AgentRun、Metric 和 Artifact 通过持久化关联判断，不只看消息是否重复。落库后重复投递应读取终态并 ACK；落库前失败则不应提前 ACK。

为什么不用“收到即 ACK”：进程在业务提交前崩溃会静默丢任务。为什么不用“永不 ACK”：会制造无限重投。设计入口见[`WorkflowMessageConsumer.java`](../backend-java/src/main/java/com/example/gameworkbench/messaging/WorkflowMessageConsumer.java)。完整重复投递 gate 当前尚未跑通，不能回答成已通过压力验证。

## 6. MySQL 中哪些事实必须一起写？

提交阶段的 WorkflowRun 与 Outbox intent 必须在同一短事务；执行阶段按 Step 边界持久化状态、Agent 关联、Metric/Evaluation/Artifact，避免跨模型 I/O 的长事务。WorkflowRun 还冻结 definition 和 PromptVersion 标识，保证历史可解释。

为什么不用跨 Java/Python/MQ 的分布式事务：成本和耦合过高，而且外部模型本身无法参与数据库原子提交；这里选择本地事务、Outbox、幂等 Consumer 与恢复扫描。迁移见[`db/migration`](../backend-java/src/main/resources/db/migration)。

## 7. 服务重启后如何恢复？

恢复扫描按持久化状态、heartbeat、attempt 和 audit 判断哪些 Run 可以重新排队，不能把未知状态直接改为成功。Outbox 保留 publish attempt，Consumer 通过 durable claim/终态判断是否继续。运维人员先恢复依赖，再使用 Run/Step/Outbox/Audit 查询，不手工篡改终态。

实现见[`WorkflowRecoveryService.java`](../backend-java/src/main/java/com/example/gameworkbench/service/impl/WorkflowRecoveryService.java)，操作边界见[Operations Runbook](operations-runbook.md)。当前故障报告指出 Python retry 拓扑与 MySQL 瞬断仍缺完整验证。

## 8. SSE 为什么不能代替持久化事件？

SSE 是展示通道，不是事实源。服务端先返回 snapshot，再按持久化 sequence 回放事件；客户端用 `Last-Event-ID` 和本地 sequence 去重，发现缺口则重新拉 snapshot。浏览器断线不改变后端执行。

为什么不用轮询：轮询难以表达顺序和低延迟增量；为什么不只用内存 emitter：刷新或服务重启会丢历史。证据见[R4 报告](reports/R4-run-center-report.md)。

## 9. RAG 引用如何证明是“实际使用”而不是重新检索出来的装饰？

Java 在调用前生成带 project/document/version/rank/score 的有界候选；Python 成功响应返回 `used_references`；Java 只把这组实际引用写入 RetrievalRecord。Run 详情读取持久化记录，不在展示时重新检索。

RAG-off、空候选、检索不可用和 mock 分开持久化。证据见[R6 报告](reports/R6-rag-knowledge-report.md)和[`RagEvidenceController.java`](../backend-java/src/main/java/com/example/gameworkbench/controller/RagEvidenceController.java)。

## 10. 如何避免检索内容注入系统指令？

检索文本被标成不可信参考材料，受 topK、分数和字符预算约束；它只进入普通数据字段，不能覆盖系统约束，也不作为 SQL、模板或代码执行。前端按文本转义显示，GameConfig 只接受 JSON 对象契约。

当前限制是 PDF 解析、索引 job 恢复和生产向量实现未达发布契约，见[R6 阻断项](reports/R6-rag-knowledge-report.md#阻断项与已知风险)。

## 11. 为什么评测分 Schema、Rule、Runtime 三层？

Schema 回答“结构是否可解析”，Rule 回答“业务语义是否满足约束”，Runtime 回答“浏览器是否真的能启动并达到 readiness”。只做 JSON Schema 会漏掉坐标/规则冲突，只做浏览器 smoke 又难定位结构错误。

Artifact eligibility 不能因为模型返回 JSON 就自动为真。当前 Schema/Rule 已有持久化路径，但浏览器 Runtime 证据回写仍不完整，因此 R5 保持 BLOCKED。见[R5 报告](reports/R5-prompt-evaluation-metrics-report.md)。

## 12. mock 指标为什么不能和真实 Provider 混算？

mock 延迟、token 和成本不代表外部模型，混算会制造错误的质量/容量结论。AgentRun 和 Metric 保存 mock provenance，analytics 默认排除 mock；RAG 对照还要求 Prompt、Provider/model、文档快照和检索版本一致。

当前没有可发布的真实性能 P95 或模型效果数据；[性能报告](reports/R7-concurrency-performance-baseline-report.md)在 preflight 阶段即 BLOCKED。

## 13. 可观测性如何控制高基数与敏感信息？

trace/run/step/agent/message ID 放日志上下文用于关联，不作为 metrics 标签；metrics 只使用 allow-list 的低基数标签。日志记录类型、长度、状态和安全错误码，不记录完整 Prompt、文档正文或 Provider 原始响应。health、readiness 和 Prometheus 暴露分别控制。

Compose drill 的健康、trace 传播、低基数与危险管理端点检查通过，但成功/失败/恢复 Run 关联因 R3 阻断未完成。见[R7 可观测报告](reports/R7-observability-operations-report.md)。

## 14. 安全边界最容易被忽略的地方是什么？

不仅是登录接口，还包括 SSE 订阅、取消/重试、Artifact UUID、Metric、Document、Vector metadata、RetrievalRecord 和上传存储路径。服务间也需要认证，生产 profile 必须关闭 Demo/Swagger 等开发能力。

R7-06 修复了 Redis 反序列化 allow-list、Java-Python 内部认证、前端依赖补丁和 Java 生产端点关闭；但 Docker 双用户集成、完整 CVE/image scan 与 Python 生产 mock 关闭仍 BLOCKED。见[安全审计](reports/R7-security-release-audit.md)。

## 15. 你如何验证，而不是只说“代码看起来正确”？

先固定任务契约和非目标，再分层运行 unit、browser、Compose/Testcontainers、性能、fault、observability、security、demo Harness。每次记录候选 SHA、退出码、环境资格、关联 ID 和脱敏证据；环境 skip 不算通过，失败证据不覆盖。

快速入口是 `.\tools\verify.ps1 -Profile quick`，完整导航见[报告索引](reports/README.md)。最重要的例子是 R7 没有用单元测试通过来掩盖 `50302`：所有依赖该链路的报告仍为 BLOCKED。

## 16. AI 在项目里做了什么，你审查了什么？

可按实际参与说明：人负责定义契约、业务边界、风险接受和最终提交；AI 辅助代码检索、候选实现、测试生成、报告整理和 diff 检查。审查重点是修改范围、状态机/事务、权限隔离、失败路径、敏感信息、测试是否真实执行，以及文案是否超出证据。

为什么不说“全部纯手写”：这会隐藏真实协作过程。为什么也不说“AI 自动完成”：最终责任仍属于提交者。项目规范见[AI 协作开发规范](AI_COLLABORATION.md)。

## 17. 当前最优先修什么，为什么？

先回到 R3 修复 Redis rate-limit/Lua 提交门，并补真实 Docker 集成回归。它发生在 durable WorkflowRun 创建之前，阻断 E2E、性能、RabbitMQ/Python 故障、可观测关联和 Demo。之后按报告顺序补 R5 Runtime/Prompt 生命周期、R6 PDF/索引恢复、R7 安全扫描与全 gate 重跑。

不应该先优化 UI 或包装性能数字，因为那不能解锁核心证据链。

## 18. 如果面试官问“为什么不用现成工作流平台/向量数据库”？

当前目标是展示状态、幂等、证据与边界如何落到业务代码；固定四步工作流和进程内 fake vector 足以做确定性开发验证。引入大型工作流平台或真实向量服务会增加运维变量，不能自动解决项目隔离、引用 provenance 或评测口径。

但这不是否定它们：当需要跨主机调度、长周期补偿、持久化向量容量或真实语义检索时，应以接口和报告为基线替换实现，并重新跑隔离、恢复、质量与成本 gate。
