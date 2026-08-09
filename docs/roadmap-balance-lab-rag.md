# B 路线：玩法平衡实验台与可验证 RAG 升级路线图

> 状态：SUPERSEDED / 历史路线
> 产品边界：继续深耕单一 `arcade_collect` Runtime，不以增加多个半成品游戏模板作为近期目标  
> 后继路线：[V5 Agentic Mini-Game Factory](requirements/v5/README.md)
> 保留原因：记录 V3 之后对平衡闭环和 RAG 的取舍；其中 RAG 原则继续有效，但不再是当前产品主线。

## 1. 目标定位

项目下一阶段定位为：

> **LLM 驱动的可玩原型与平衡实验平台**：把受限玩法 Brief 编译为可验证 GameConfig，通过真实玩家或模拟会话采集版本化数据，再生成有依据、可采纳、可比较的平衡候选。

它不承诺：

- 从任意创意生成任意游戏代码；
- 自主多 Agent 协商、工具调用或长期记忆；
- 当前 fake embedding/vector 已具备语义检索能力；
- 单机 Compose 的测试结果等价于生产 SLA。

## 2. 为什么选择单玩法做深

当前真正具有差异化的资产不是 Runtime 数量，而是以下事实链：

```text
Brief
→ 冻结 Workflow/Prompt/RAG 快照
→ 受契约约束的 GameConfig
→ 不可变 PrototypeVersion
→ 版本绑定的 PlaytestSession/Event
→ 服务端复算指标
→ 有依据的平衡建议
→ 候选子版本
→ A/B 对比
→ 确定性导出
```

增加第二、第三种 Runtime 会同时放大 Schema、运行时、遥测、调参和测试矩阵，却不能自动证明现有闭环有用户价值。近期投入应优先用于让单玩法实验链路可分享、可测量、可比较。

## 3. RAG 在产品中的职责

RAG 不是独立展示项，也不是为了满足技术栈关键词。它只承担以下可验证职责：

1. 检索项目级设计约束，例如目标玩家、难度原则、禁用机制和视觉规范。
2. 检索历史版本的实验结论，避免建议反复提出已被数据否定的调整。
3. 检索结构化策划知识，为平衡建议提供来源和适用条件。
4. 为每次生成冻结 query、文档版本、chunk、rank、score 和实际引用，支持复盘。

RAG 不负责：

- 替代 GameConfig Schema 和业务规则校验；
- 把不可信文档提升为系统指令；
- 自动证明建议正确；
- 在没有对照实验时宣称提升模型效果。

## 4. 当前基线

### 已具备

- 项目级 KnowledgeDocument/KnowledgeChunk 生命周期；
- RAG-on/off、empty、unavailable、mock 等显式状态；
- `used_references` 回传和 RetrievalRecord provenance；
- project/document/version 双重过滤；
- PromptVersion、AgentRun、Metric 和 Evaluation 数据模型；
- RAG-on/off 对照聚合接口骨架。

### 必须公开承认的缺口

- `FakeEmbeddingProvider` 仅生成 8 维字符哈希；
- `InMemoryVectorStore` 不计算语义相似度，结果统一为 `1.0`，重启即丢失；
- PDF 尚未形成受限、正确的纯文本提取；
- context budget 需要按实际注入文本/token 计算；
- 缺少持久化索引任务、重试、恢复和一致性修复；
- 缺少固定评测集，不能证明 RAG-on 优于 RAG-off；
- 前端主产品路径尚未暴露知识管理和引用证据。

## 5. 升级工作包

### P0：文档与可信度门禁

- 统一 README、架构、简历和面试材料的产品定位。
- 所有能力表区分 `Implemented`、`Test Stub`、`Proposed` 和 `Verified`。
- 禁止使用“生产级 RAG”“自主多 Agent”“通用游戏生成”等超出证据的措辞。
- 修复源码乱码、单行压缩类和失效文档链接。

完成标准：招聘者只读 README，也不会形成超出代码事实的预期。

### P1：真实、持久化的检索路径

- 选择一种向量后端：优先 `pgvector`，也可评估 Qdrant；只保留一个正式实现。
- 接入真实 embedding provider，并记录 provider/model/dimension/version。
- 实现 cosine similarity、稳定 topK、最小分数和确定性 tie-break。
- 用数据库事实驱动索引 job，支持 attempt、退避、失败原因、重建和删除失效。
- 为 Markdown/TXT/PDF 分别实现受限提取；PDF 增加页数、文本量、超时和并发限制。
- context budget 改为按最终注入文本的 tokenizer 或保守 token 估算计算。

完成标准：重启后索引可恢复；给定 query 的排名可复现；跨项目和失效文档测试通过。

### P1：RAG 质量评测

建立 30～100 条固定样本，每条至少包含：

- 项目与文档快照；
- query；
- 期望命中的 document/chunk；
- 允许与禁止引用；
- 期望的 GameConfig/建议约束；
- 人工评分 rubric。

至少报告：

- Recall@K、MRR 或 nDCG；
- 空检索率和错误项目命中数；
- RAG-on/off 的约束满足率；
- Schema/Rule 通过率；
- 人工或 judge 质量分；
- P50/P95 延迟、token 和成本；
- 样本数、provider/model、PromptVersion 和置信区间/不足样本标记。

完成标准：只有对照数据证明增益后，简历才可写“RAG 提升了某项指标”。

### P1：公开试玩与匿名遥测

- 为 PrototypeVersion 生成可撤销、限时、限权的分享 token。
- 公开试玩只允许读取冻结配置和提交受限遥测，不暴露项目管理 API。
- 增加样本进度、有效会话、胜率、失败原因、时长和重试漏斗。
- 明确机器人、项目所有者和外部测试者样本来源，避免混算。

完成标准：外部测试者无需账号即可贡献可归属到具体版本的有效会话。

### P2：建议到实验的闭环

- 建议必须包含证据窗口、样本量、目标指标、建议参数、预期方向和不确定性。
- 支持一键从建议派生不可变候选版本，但不得覆盖原版本。
- 支持 A/B 分享、分流和同口径指标比较。
- 展示建议被接受、拒绝或修改后的结果，形成反馈数据。

完成标准：可以在一次演示中完成“观察问题→生成建议→创建候选→收集数据→比较结果”。

### P2：把已有工程证据暴露给用户

- Run 页面展示每步模型、PromptVersion、mock 状态、耗时、token 和成本。
- 展示实际 RetrievalRecord，而不是重新检索的装饰性结果。
- 展示失败分类、重试和恢复事件。
- 允许从指定步骤以新运行重试，并明确快照是否复用。

## 6. 暂不实施

- 第二或第三种游戏 Runtime；
- 自主 Agent、通用 Tool Calling 和多轮聊天；
- Kubernetes、服务网格或更多消息中间件；
- AI 生成并执行任意 JavaScript/HTML；
- 在没有质量实验前增加更多 RAG UI 包装。

这些方向只有在单玩法实验闭环和真实 RAG 评测完成后重新评估。

## 7. Review 决策点

Review 时需要明确确认：

1. 是否接受“可玩原型与平衡实验平台”作为唯一对外定位？
2. 正式向量后端选择 pgvector 还是 Qdrant？
3. 第一批评测知识是游戏设计规则、项目约束，还是历史平衡实验？
4. 公开试玩采用匿名分享 token 是否可接受？
5. 建议派生版本是否必须人工确认，还是允许创建 `DRAFT` 候选？
6. 哪些旧同步/单 Agent API 可以在下一阶段弃用？

## 8. 求职验收标准

下一阶段完成后，项目应能够诚实证明：

- 可靠执行一条受约束的 LLM 工作流；
- 真实语义检索具有可复现的检索质量指标；
- RAG 对生成约束满足率的影响经过同条件对照；
- 外部试玩数据可以驱动不可变候选版本和 A/B 比较；
- Demo、指标、日志和导出共享同一版本化事实链。

在此之前，简历仍应使用“LLM Workflow + RAG 协议/检索桩”的保守表述。
