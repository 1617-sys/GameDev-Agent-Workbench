# R6-04: 项目隔离 RetrievalService 与引用选择

> 状态：`TODO`
>
> 前置任务：`R6-03`
>
> 推荐模型：`gpt-5.5`
>
> 任务类型：检索服务 / 隔离与质量测试

## 背景

向量已存在不等于可以安全使用。检索服务必须以 projectId、文档/chunk 状态、版本与 token 预算进行强过滤，并返回可解释的候选引用，而不是把“相似文本”直接塞进模型上下文。

## 目标

实现统一 RetrievalService：

```text
retrieval request(projectId, query, topK, filters, budget)
-> embed query
-> VectorStore.search(metadata projectId + READY/active filters)
-> optional deterministic filtering/dedup
-> rank, score, chunk/document source
-> RetrievalCandidate list within context budget
```

## 范围

允许：

- 新增 RetrievalRequest/Candidate、RetrievalService、query embedding adapter、metadata filters、score/rank/预算逻辑。
- 支持按 projectId、sourceType、document version/status、agentType/schema tag 的可控过滤。
- 实现 topK 上限、最小 score、重复 chunk 去重、文本截断和 context token/char budget。
- 返回 chunkUuid、documentUuid/name/version、score、rank、text/reference、embedding/chunking version。
- 添加跨项目、已删除/失效、空索引、低分、topK、预算、重复候选、provider 异常测试。

## 非目标

- 不在此任务把候选注入 Python Prompt 或保存 RetrievalRecord。
- 不支持跨项目检索、公共知识共享或网络搜索。
- 不实现 LLM reranking、semantic cache、查询改写或多跳检索。
- 不允许调用方绕过 service 直接访问 VectorStore。
- 不用检索 score 直接声明模型输出质量。

## 约束

- projectId filter 必须由 VectorStore query 和应用层结果校验双重执行；任一缺失视为安全缺陷。
- READY 且未删除的 document/chunk 才可返回；历史 vector/Chunk 绝不应成为新候选。
- query 输入不可信，长度/空白/敏感字段需校验与脱敏；不得把 query 拼接为向量库底层语句。
- 结果必须遵守 context budget，超预算时按稳定策略截断/减少候选并保留选择证据。
- score、rank、模型/索引版本要与 Candidate 一起返回，不能只返回文本。
- VectorStore/Embedding 故障返回明确受控错误或“RAG 不可用”，不能悄悄回退到跨项目/无过滤查询。

## 验收标准

- [ ] 同一查询只能返回请求 projectId 下 READY、active 文档的 chunk。
- [ ] 已删除/失效、低分、重复或超预算候选不会被注入返回列表。
- [ ] 返回候选有稳定 rank、score、chunk/document/version/source 信息。
- [ ] 空索引和 RAG provider 故障有明确行为，不影响非 RAG 运行的可用性。
- [ ] 跨项目攻击测试和 metadata filter 缺失测试能捕获泄露风险。
- [ ] topK、预算、阈值、去重和错误路径的单元/集成测试通过。

## 验证命令

```powershell
cd python-agent
python -m pytest

cd ..\backend-java
mvn -Dtest=*RetrievalService*Test,*KnowledgeIsolation*Test,*VectorStore*Test test
mvn test

cd ..
.\tools\verify.ps1 -Profile integration
.\tools\verify.ps1 -Profile quick
```

## 审查清单

- 是否只靠数据库 documentId 过滤，向量查询未过滤 projectId。
- 是否删除/失效文档仍能从旧 vector 命中。
- 是否返回纯文本而没有来源、rank、score、版本。
- 是否在 provider 失败时悄悄进行无过滤/跨项目搜索。
- 是否忽略 context budget 导致 Prompt 膨胀。

## 完成定义

- RAG 检索拥有项目级隔离、预算控制和来源可解释性。
- Python Agent 可安全消费候选，而无需直接接触向量库。
