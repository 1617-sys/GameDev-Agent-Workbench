# 面试问答：GameDev Agent Workbench

回答原则：先说真实业务问题，再说设计取舍、失败边界和证据。不要用“多 Agent、RAG、分布式”等名词替代具体实现。

## 1. 这个项目到底做什么？

它是面向单一 `arcade_collect` 玩法的 LLM 可玩原型与平衡实验平台。用户提交 Brief，系统通过固定四步 LLM Workflow 生成概念、核心循环、任务和 GameConfig；配置通过契约门禁后进入固定 Phaser Runtime，并形成不可变版本、试玩遥测、平衡建议、版本比较和离线导出。

它不是通用游戏代码生成器。当前可变的是受白名单约束的地图、实体、数值和表现参数，不是任意游戏机制。

## 2. 为什么它不只是一个模型调用 Demo？

模型输出只是一段中间事实。系统还处理用户/项目权限、幂等提交、Outbox、消息消费、Step 状态、Prompt/输入快照、Schema/Rule 门禁、Artifact、不可变版本、遥测复算、SSE 恢复和确定性导出。

真正亮点是把不确定模型调用纳入可恢复、可追溯、可验证的业务链路，而不是调用了多少个模型。

## 3. 为什么 Java 管状态，Python 管模型？

Java 负责鉴权、事务、状态机、幂等、消息、查询和审计，作为 WorkflowRun 的唯一事实源；Python 负责 Prompt 渲染、模型 Provider、RAG 上下文和输出解析。这样模型响应和业务成功不会混为一谈。

跨服务协议仍需改进：Java→Python 客户端应补连接池、connect/read timeout、有限重试与熔断；Python 需要 structured output、JSON repair/retry、token/cost 回传和真实 provider contract test。

## 4. RabbitMQ + Outbox 解决了什么？

提交事务只写 WorkflowRun 和 Outbox intent，不跨越耗时模型 I/O。Publisher confirm 后投递，Consumer 通过持久化 claim、attempt 和终态判断处理重复消息；成功事实落库后才 ACK。

这提供的是 at-least-once 投递下的业务幂等，不是 exactly-once。Redis 锁减少昂贵重复调用，数据库约束和状态版本才是正确性底线。需要能解释 publish confirm 成功但 DB 更新失败、Consumer 重入和 Artifact 去重等失败窗口。

## 5. SSE 为什么不能作为事实源？

SSE 只负责低延迟通知。服务端快照、持久化 sequence 和 allowedActions 才是事实；客户端忽略重复/旧事件，发现序号缺口就重新拉快照。浏览器断线不会取消后台工作流。

## 6. 为什么模型输出不能直接运行？

模型只能生成 GameConfig 数据，不能生成并执行 JavaScript、HTML 或远程资源。配置先经过结构、字段白名单、数值边界、资源 manifest、业务规则和 Runtime capability 校验，成功后才创建可玩 Artifact/PrototypeVersion。

这样牺牲通用性，换取安全、确定性和可复现性。

## 7. RAG 当前到底实现到了什么程度？

已实现：项目级文档/Chunk 生命周期、项目隔离、RAG-on/off/empty/unavailable/mock 状态、候选上下文协议、`used_references` 回传，以及 RetrievalRecord 对 document/chunk/version/rank/score 的持久化 provenance。

未实现：真实语义质量。当前 `FakeEmbeddingProvider` 只是 8 维字符哈希；`InMemoryVectorStore` 不计算余弦相似度，命中 score 统一为 1，且进程重启丢失。因此它只能称 RAG 协议和检索证据桩，不能称生产级向量检索。

## 8. 准备如何证明 RAG 确实有价值？

先替换为真实 embedding 和持久向量后端，再建立 30～100 条固定样本，标注期望 chunk 和生成约束。检索侧报告 Recall@K、MRR/nDCG、空检索率和跨项目错误命中；生成侧同条件比较 RAG-on/off 的约束满足率、Schema/Rule 通过率、人工或 judge 分数、延迟、token 和成本。

所有 cohort 必须固定 PromptVersion、provider/model、文档快照和检索版本。没有这个实验，不宣称 RAG 提升质量。

## 9. 如何防止知识文档 Prompt Injection？

检索内容必须明确标记为不可信参考，不能覆盖系统约束，也不能进入 SQL、模板或代码执行路径。GameConfig 最终仍经过 Java 权威门禁。升级真实 RAG 时还需加入文档来源策略、内容扫描、引用允许列表和针对指令注入的固定攻击样本。

## 10. 为什么不继续增加多个 Runtime？

每增加一个玩法都要扩展 Schema、生成 Prompt、Runtime、遥测语义、调参白名单和测试矩阵。当前用户价值最薄弱的地方不是模板数量，而是外部试玩、数据解释和建议验证。

因此下一阶段优先把单玩法做深：公开分享 token、匿名遥测、样本进度、建议依据、一键派生候选和 A/B 对比。只有这条链路稳定后，第二 Runtime 才能证明扩展架构，而不是复制半成品。

## 11. 当前最严重的工程债是什么？

按优先级：

1. fake/in-memory RAG 容易造成能力名实不符；
2. 源码乱码和单行压缩类损害可维护性；
3. Java→Python 外部调用缺少完整 timeout/连接池/熔断边界；
4. 缺少统一 CI、lint、coverage 和依赖锁；
5. 新旧同步/异步 API 双轨增加认知和维护成本；
6. 公开 Demo 无法让外部测试者回传匿名遥测；
7. Phaser chunk 体积较大，完整 Compose E2E 不在默认测试中持续证明。

继续增加表或中间件不能修复这些问题。

## 12. AI 在项目里做了什么，你承担什么责任？

AI 可以辅助代码检索、候选设计、实现、测试和文档整理；个人责任应按真实参与说明，包括需求拆解、边界决策、状态机/事务/权限审查、diff 审查、验证执行、失败归属和最终提交。

“AI 辅助”不等于项目没有个人价值；真正需要证明的是你能否解释设计、发现错误、拒绝超范围实现并用测试验证结论。

## 13. 当前测试能证明什么，不能证明什么？

本地验证中 Java 182 项测试通过、Python 21 项通过、前端 32 项单测与生产构建通过。它们能证明大量合同和组件逻辑具有回归保护。

它们不能自动证明真实 LLM 质量、完整 Docker 依赖链、生产吞吐或线上 SLA。本次 Review 环境 Docker daemon 未运行，因此没有重跑 Compose E2E；V3 历史发布验收必须附当时环境限定阅读。

## 14. 下一阶段的完成定义是什么？

用户能把某个冻结版本分享给外部测试者；匿名会话安全回传；系统展示样本量和关键漏斗；建议说明证据与不确定性；用户一键派生 DRAFT 候选并进行 A/B 对比。同时 RAG 使用持久化真实向量检索，并有固定数据集证明检索质量和 RAG-on/off 影响。

详细工作包见[B 路线与 RAG 升级路线图](roadmap-balance-lab-rag.md)。
