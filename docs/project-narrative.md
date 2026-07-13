# 3–5 分钟项目讲解提纲

## 0:00–0:35：问题与目标

“这是一个 AI 游戏工作流工程，不是单次模型调用页面。我要解决的是：重复提交怎样不重复执行、模型输出怎样形成可追溯产物、RAG 引用怎样证明实际使用、浏览器断线怎样恢复、失败怎样留下证据。”

打开根 [README](../README.md) 第一屏，同时主动说明当前 release 为 BLOCKED，不展示为生产成品。

## 0:35–1:20：架构与主链路

打开[架构图](architecture/system-architecture.md)：

- Vue 负责提交、Run Center、SSE、知识证据和 Phaser。
- Java 是事实源，负责 JWT/项目归属、短事务、Outbox、Consumer、Runner、评测与查询。
- Python 只承接 Agent/RAG 协议和显式 mock/Provider 调用。
- MySQL 保存 durable facts，Redis 保护高成本窗口，RabbitMQ 解耦提交与执行。

强调提交事务不跨模型 I/O，WorkflowRun 冻结定义/Prompt 版本，重复消息按持久化事实去重。

## 1:20–2:10：两个工程亮点

亮点一：**可靠执行边界**。

“HTTP 只创建 Run + Outbox；publish confirm 后投递；Consumer claim 后执行；Step、Agent、Metric、Artifact 关联持久化。Redis 锁是成本保护，数据库幂等才是正确性底线。”

亮点二：**RAG 与评测证据**。

“展示层不重新检索。Python 返回实际 used references，Java 写 RetrievalRecord；RAG-off/empty/failure/mock 分开。GameConfig 要经过 Schema、Rule、Runtime 证据才能成为可玩 Artifact。”

## 2:10–3:00：验证方式

打开[报告索引](reports/README.md)：

- quick 验证 Java、Python compile、Vue build 和 Compose config。
- integration/e2e 检查真实依赖、UI、DB 与 SSE 关联。
- performance/fault/observability/security/demo 各有独立 Harness、超时、清理和脱敏证据。
- 环境 skip 不算发布 PASS，失败 evidence 不覆盖。

可引用已通过的单元/浏览器数量，但必须附报告和环境限定；不引用不存在的性能 P95。

## 3:00–3:40：失败边界与诚实结论

“R7 暴露了一个共同阻断：健康 Redis 下，rate-limit/Lua 提交门仍返回 `50302`，发生在 durable Run 创建之前。因此 E2E、性能、RabbitMQ/Python 故障和 Demo 不能继续。我没有绕过 gate 或直接改数据库，而是把 R3 归属、退出码和零下游事实写进报告。”

补充 R5 Runtime/Prompt 生命周期、R6 PDF/索引恢复、R7 安全扫描仍有缺口。下一步先修 R3 并重跑所有受影响 gate，而不是先包装 UI 或数字。

## 3:40–4:20：AI 协作与个人责任

“开发采用人定义契约、AI 辅助探索/实现/测试、Harness 和人工审查裁决的方式。我负责按实际参与范围说明：需求拆解、架构取舍、AI diff 审查、事务/状态机/权限边界、失败归属、验证和文档沉淀。AI 生成不等于自动验收，最终 commit 责任仍在人。”

## 可选追问入口

- 为什么不用分布式事务？见[面试问答第 6 题](interview-qa.md#6-mysql-中哪些事实必须一起写)。
- 重复消息如何处理？见[第 5 题](interview-qa.md#5-rabbitmq-重复投递时怎样避免重复成功)。
- RAG 引用如何可信？见[第 9 题](interview-qa.md#9-rag-引用如何证明是实际使用而不是重新检索出来的装饰)。
- AI 做了什么？见[第 16 题](interview-qa.md#16-ai-在项目里做了什么你审查了什么)。
