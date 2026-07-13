# R6-00: RAG 知识库与检索证据契约冻结

> 状态：`TODO`
>
> 前置任务：`R5-验收`
>
> 推荐模型：`gpt-5.5`
>
> 任务类型：架构契约 / 只写文档

## 背景

R5 已使 Prompt、模型调用、Artifact 和评测可追溯，但 Agent 仍主要依赖用户输入与固定 Prompt，无法稳定使用项目规则、Phaser Runtime 限制、GameConfig Schema 和已有设计文档。R6 要引入受控的项目级 RAG，并记录每次检索如何影响生成。

## 目标

新增 `docs/requirements/r6/R6-rag-knowledge-design.md`，冻结 R6 的证据链：

```text
upload Markdown/TXT/PDF
-> KnowledgeDocument version + extraction
-> KnowledgeChunk + embedding/vector index
-> project-isolated RetrievalService
-> Python Agent prompt context
-> RetrievalRecord per AgentRun
-> RAG-on / RAG-off evaluation comparison
```

文档必须定义数据模型、文件安全、切块/Embedding 版本、检索过滤、Prompt 预算、引用证据、删除失效和对照评测口径。

## 范围

允许：

- 阅读 R5 Prompt/Metric/Evaluation 数据模型、Python Agent、R4 运行中心、现有文档和 GameConfig 契约。
- 新增设计文档、ER 图、导入/检索时序图、向量提供者边界、数据保留策略与任务依赖图。
- 明确每个 R6 子任务的允许目录、数据迁移顺序、验证命令、回退方式和成本控制策略。

## 非目标

- 不修改 Java、Python、Vue 的业务代码。
- 不接入全网搜索、浏览器爬虫、实时互联网内容或第三方知识市场。
- 不执行文档中的代码、命令、HTML 或脚本。
- 不实现复杂 GraphRAG、Agent 自主规划/写入知识库或多模态 OCR 平台。
- 不改变 R5 的 Prompt/Metric/Evaluation 事实来源。

## 约束

- 检索必须按 projectId 做强隔离，数据库查询与 vector metadata filter 都是必要条件。
- 文档、chunk、embedding、retrieval 都需版本/哈希/状态，历史 AgentRun 必须可追溯当时引用的来源。
- RAG 可按 WorkflowRun/Step 关闭，关闭时不得偷偷注入检索文本；对照评测需保留开关事实。
- 检索文本只作为不可信上下文，不能被当作系统指令、SQL、代码或可执行命令。
- 所有上传、解析、Embedding 和索引操作均异步/可恢复，不能持有 HTTP 长请求或数据库长事务。
- R6 不以“找到了相似文本”作为质量结论，必须结合 R5 的确定性评测对照数据。

## 验收标准

- [ ] 文档定义 KnowledgeDocument、KnowledgeChunk、Embedding、RetrievalRecord 和 AgentRun 的关系与版本策略。
- [ ] 文档明确 Markdown/TXT/PDF 上传、解析、大小/类型限制、删除/失效和项目授权策略。
- [ ] 文档明确切块、Embedding、topK、score、metadata filter、token/context budget 与 fallback 语义。
- [ ] 文档明确 RAG-on/RAG-off 对照的样本、指标、mock 过滤和解释边界。
- [ ] 文档明确 R6 不实现网络搜索、代码执行、GraphRAG 和 R7 性能/演示工作。

## 验证命令

```powershell
git diff --check
rg -n "KnowledgeDocument|KnowledgeChunk|Embedding|RetrievalRecord|projectId|topK|RAG-on|RAG-off" docs\requirements\r6\R6-rag-knowledge-design.md
```

## 审查清单

- 是否只在应用层检查项目隔离，遗漏 vector metadata filter。
- 是否没有版本/哈希导致历史引用无法解释。
- 是否把检索内容当可信系统指令或可执行代码。
- 是否遗漏删除、失效、Embedding 失败、索引滞后和 RAG 关闭的行为。
- 是否把 R7 的性能压测或 R6 之外的网络搜索混入范围。

## 完成定义

- R6 的数据、检索、安全、证据和评测语义已经冻结。
- 后续任务可以独立实现而不牺牲项目隔离和历史可解释性。
