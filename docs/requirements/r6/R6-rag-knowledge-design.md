# R6 RAG 知识库与检索证据契约书

> 状态：`FROZEN`
>
> 依据：`R6-00-rag-knowledge-rfc.md`
>
> 适用对象：所有执行 R6 子任务的 Agent、评审者和验收 Harness。

## 1. 强制执行规则

本文件是 R6 的修改范围契约。每个 Agent 开始工作前必须读取本文件及其任务卡；只能修改其任务卡“允许目录”表中列出的文件或目录。未列出的变更（包括重构、依赖升级、格式化、前端美化、默认配置调整）一律禁止，除非任务卡或用户明确授权。

每次读写数据都必须以 `userId + projectId` 授权。只按 UUID 查询、只在 Controller 验权、或只在向量查询以外的应用层过滤，均不符合契约。

禁止将知识文本视为系统指令、SQL、模板、代码或命令；禁止执行、渲染或自动信任它。R6 不访问公网、不爬取网页、不做 GraphRAG、不让 Agent 自主写入知识库。

提交前必须运行任务卡验证命令、`git diff --check`，并审查暂存 diff。不得提交 `.env`、真实密钥、原始敏感正文、构建输出或其他任务卡之外的改动。

## 2. 证据链与数据契约

```text
Markdown/TXT/PDF（不可信）
  -> KnowledgeDocument（版本化、项目归属、状态）
  -> KnowledgeChunk（项目冗余字段、ordinal、文本 hash）
  -> Embedding / Vector metadata（projectId 必填）
  -> RetrievalService（授权 + metadata filter）
  -> Python Agent 的不可信上下文块
  -> RetrievalRecord（实际 rank/score/source/version）
  -> RAG-on / RAG-off 同条件评测
```

| 对象 | 不可变/版本字段 | 必要隔离字段 | 生命周期 |
| --- | --- | --- | --- |
| KnowledgeDocument | `documentUuid`、`contentHash`、`version`、`sourceType` | `projectId` | `UPLOADED → PARSING → INDEXING → READY`；可至 `FAILED`、`INVALID`、`DELETED` |
| KnowledgeChunk | `chunkUuid`、`textHash`、`chunkingVersion`、`ordinal` | `projectId`、`documentId` | 随文档失效；不得覆盖历史引用的快照 |
| Embedding | `embeddingModel`、模型版本、输入 hash | `projectId`、`documentId`、`chunkId`、状态 | 失败或过期必须可重试并可追踪 |
| RetrievalRecord | run/step、rank、score、候选/选择事实 | `projectId`、chunk/document/version | 对历史 AgentRun 只追加，不重写 |

删除或失效后，新的检索必须排除 Document/Chunk/Vector；历史 `RetrievalRecord` 只保留最小来源摘要、hash、版本、rank 和 score，以解释历史，不暴露完整原文或 Secret。

## 3. 文件、安全与异步契约

- 仅接收 Markdown、TXT、PDF。扩展名、声明 MIME 与内容检测必须同时通过；服务端生成 storage key，用户文件名不能成为路径。
- 限制文件大小、PDF 页数、提取文本长度、解析时间和并发量。拒绝空文件、路径遍历、伪造类型及未授权项目。
- 上传请求只创建 `KnowledgeDocument(UPLOADED)` 和异步任务；解析、Embedding、索引不得占用 HTTP 长请求或数据库长事务。
- 解析仅输出纯文本和有限 metadata。错误与日志必须脱敏，记录安全 metadata 和 hash，不记录完整原文、绝对路径、Token 或密钥。
- 删除与异步任务并发时，worker 必须以状态条件更新；任何任务不得把 `DELETED` 恢复为 `READY`。

## 4. 切块、向量与检索契约

- 切块规则、chunking 版本、token 计数和文本 hash 必须持久化；重切块创建新版本，不原地覆盖可追溯证据。
- 向量 metadata 至少含 `projectId`、document/chunk UUID、document version、status、embedding model/version。每次向量查询必须携带 `projectId` metadata filter，数据库查询也必须带项目过滤。
- `topK`、最小 score、候选上限和 prompt token/context budget 必须是受控配置。超预算时按稳定排序截断，记录截断事实。
- 无候选、检索失败、索引滞后和 mock 均是显式 fallback 状态；不得伪装为“检索成功”。

## 5. Agent Prompt 与评测契约

RAG-on 仅注入本次实际选择、已授权、预算内的文本，并用明确边界标记为“不可信参考资料”。系统约束优先于任何检索内容。RAG-off 不得注入任何检索文本、摘要或隐式缓存，且必须记录开关事实。

RAG-on/off 比较必须使用相同项目、fixture、Prompt 版本、模型、文档版本和评测口径；报告分别呈现通过率、延迟、token、成本、覆盖率，并排除或单列 mock、空知识库和失败样本。R6 不以相似度本身作为质量结论。

## 6. 子任务修改范围矩阵

| 任务 | 允许修改目录/文件 | 必须实现/验证 | 明确禁止 |
| --- | --- | --- | --- |
| R6-01 生命周期 | `backend-java/src/main/**/{entity,mapper,service,common/enums}`、`resources/db/migration`、对应后端测试 | Document/Chunk、版本、状态机、授权、软删除 | HTTP 上传、解析、Embedding、检索、Vue |
| R6-02 导入安全 | 后端 Controller/DTO、storage/parse 服务、配置、对应测试 | 白名单、sniffing、限制、异步解析、脱敏 | DOCX/OCR/URL、同步索引、预览执行 |
| R6-03 切块与索引 | 后端 chunk/embedding/index 服务、迁移、测试 | 稳定切块、版本、metadata、重试/失效 | 检索 API、Prompt 注入、第三方知识市场 |
| R6-04 检索服务 | 后端 retrieval service/DTO/测试 | `userId + projectId` 授权、DB 与 vector 双过滤、预算 | Prompt 拼装、前端、跨项目回退 |
| R6-05 Agent 协议 | `python-agent/app`、最小后端契约/测试 | 不可信上下文边界、RAG 开关、预算事实 | 代码执行、修改 R5 事实来源 |
| R6-06 证据记录 | 后端 retrieval record 迁移/service/测试 | run/step 的实际 source/rank/score/version | 删除或改写历史证据 |
| R6-07 对照评测 | 评测服务、fixture、报告、测试 | 同条件 RAG-on/off、mock 分离 | 改写 R5 指标事实、R7 压测 |
| R6-08 UI/Harness | `frontend-vue/src`、Harness、最小后端只读 DTO | 权限安全来源展示、移动端检查 | 展示全文敏感内容、修改检索语义 |
| R6 验收 | `docs/reports/R6-rag-knowledge-report.md`、任务状态 | 全量证据与 R7 准入结论 | 新功能、破坏性数据操作 |

所有任务可读取 R5/R4/R3 相关实现与本目录文档；“可读取”不代表可修改。跨任务文件若确有阻断，Agent 必须停止并请求明确授权。

## 7. 迁移、回退与成本控制

迁移只能前进且兼容历史数据；新增字段应可空或有安全默认值。禁止回滚迁移、物理删除历史证据和批量重写既有记录。回退通过关闭 RAG 开关、停用新索引版本或将文档标记 `INVALID` 完成，而非删除证据。

Embedding 与索引必须通过队列/作业执行，具备幂等键、有限重试、退避、失败状态和成本上限。每次重建必须可按 project/document/version 定位并计费；不得为测试调用付费生产模型作为唯一途径。

## 8. Agent 交付检查单

- [ ] 变更仅位于当前任务卡允许范围，且未夹带未跟踪/用户已有改动。
- [ ] 所有读取、写入、数据库查询和向量查询均携带项目隔离条件。
- [ ] 状态、版本、hash、删除/失效和失败路径有测试；无跨项目 UUID 绕过。
- [ ] RAG 文本未被执行或当作可信指令；RAG-off 零注入。
- [ ] 验证命令通过，`git diff --check` 通过，diff 已审查。
- [ ] 提交不含 `.env`、密钥、完整敏感正文、构建输出或 R7 工作。

