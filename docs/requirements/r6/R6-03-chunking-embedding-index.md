# R6-03: 文本切块、Embedding 与向量索引

> 状态：`BLOCKED`
>
> 前置任务：`R6-01`、`R6-02`
>
> 推荐模型：`gpt-5.5`
>
> 任务类型：数据处理 / 向量提供者适配与集成测试

## 背景

解析后的整篇文档不适合直接放入 Prompt。R6 需要可重复的切块和 Embedding 管线，并让 index 中的 metadata 能强制项目隔离、版本追踪和删除失效。

## 目标

实现异步索引链路：

```text
PARSED KnowledgeDocument version
-> deterministic chunker
-> KnowledgeChunk(textHash, ordinal, chunkingVersion)
-> EmbeddingProvider.embed(batch)
-> VectorStore.upsert(vector, projectId/documentId/chunkId/version/status metadata)
-> INDEXED/FAILED
```

VectorStore 必须通过接口抽象，首版选择一个本地可复现实现；测试可使用 fake/in-memory provider，不调用付费 Embedding API。

## 范围

允许：

- 新增 Python/Java 中合适位置的 Chunker、EmbeddingProvider、VectorStore adapter、IndexingService/Worker 与状态更新。
- 定义 chunk size/overlap/token estimate、chunkingVersion、embedding model/dimension、batch size、retry/timeout、index namespace。
- 实现 deterministic 切块、内容 hash 去重、批量 Embedding、向量 upsert/delete、失败重试/最终失败记录。
- 用 `projectId`、documentUuid/version、chunkUuid、status 写入 vector metadata，并在数据库保留 index reference。
- 添加 unit/integration 测试：边界切块、同内容幂等、provider timeout、部分 batch 失败、删除/失效、metadata 完整性。

## 非目标

- 不实现检索 Query、Prompt 组装或 RAG-on/off 对照。
- 不支持跨项目共享索引、全局公共搜索或用户自定义向量 SQL。
- 不切换到复杂多模型 reranker/GraphRAG。
- 不在测试调用真实 Embedding 付费服务。
- 不将 chunk 正文无限制复制到日志、MQ 或前端。

## 约束

- 对同一 document version + chunkingVersion + embeddingModel 的重复索引必须幂等，不产生重复 chunk/vector。
- 每个 vector metadata 必须带 projectId 和 active/deleted 状态，未来检索必须同时过滤两者。
- 文档更新/删除/失效时，旧 vector 必须可异步撤销/标记不可检索；不能只删数据库保留向量。
- Embedding 调用在数据库事务外执行；状态/引用在短事务中更新。
- provider/向量库不可用时 Document 进入可恢复 FAILED/INDEXING 状态并保留原因，不可假装 READY。
- 记录 embedding model/version/dimension，避免模型切换后把不可比较向量混在同一 namespace。

## 验收标准

- [ ] 相同输入在固定配置下产生稳定 chunk 边界、ordinal、hash 和 chunkingVersion。
- [ ] 成功索引的每个 vector 都包含 project/document/chunk/version/status metadata 与数据库引用。
- [ ] 重复索引不生成重复 chunks/vectors；部分失败、超时、重试有确定状态。
- [ ] DELETED/INVALID 文档的 vectors 被撤销或不可检索，测试可证明。
- [ ] 不同 embedding model/version/dimension 不会无标识混入同一检索空间。
- [ ] fake provider 集成测试、Python compile/test、quick/integration Harness 通过。

## 验证命令

```powershell
cd python-agent
python -m compileall app
python -m pytest

cd ..\backend-java
mvn -Dtest=*KnowledgeIndex*Test,*KnowledgeChunk*Test,*Embedding*Test test
mvn test

cd ..
.\tools\verify.ps1 -Profile quick
.\tools\verify.ps1 -Profile integration
```

## 审查清单

- 是否因为重试/重跑产生重复向量或覆盖不同版本。
- 是否没有 projectId/status metadata，给跨项目检索留下漏洞。
- 是否只删除数据库记录却遗留可命中的向量。
- 是否在 Embedding 网络调用中持有数据库事务。
- 是否用真实付费 Provider 作为唯一测试方式。

## 完成定义

- 文档可被安全、幂等地转为版本化 chunks 与项目隔离的向量索引。
- R6-04 可基于稳定 metadata 执行检索而不猜测来源。
