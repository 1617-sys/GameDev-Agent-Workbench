# R6-06: RetrievalRecord 持久化与 AgentRun 引用证据

> 状态：`TODO`
>
> 前置任务：`R5-01`、`R6-05`
>
> 推荐模型：`gpt-5.5`
>
> 任务类型：检索溯源 / 数据一致性与查询测试

## 背景

即使 Agent 使用了 RAG，上下文也会随文档、切块、Embedding 和检索策略演进。没有持久化 RetrievalRecord，就无法回答“这次输出具体引用了哪几个 chunk、分数多少、当时文档是什么版本”，也无法支持对照评测和前端解释。

## 目标

为每次实际使用的检索候选创建不可变记录：

```text
AgentRun / StepRun
-> RetrievalRecord
   retrievalUuid, workflowRunUuid, stepRunUuid, agentRunId,
   documentUuid/version, chunkUuid, rank, score,
   chunkingVersion, embeddingModel, queryHash,
   ragEnabled, contextBudget, selectedAt
```

并提供权限安全的查询 Read Model，向 R4 运行页输出来源摘要而非全部敏感文本。

## 范围

允许：

- 新增 Flyway migration、RetrievalRecord Entity/Mapper/Repository、关联约束/索引和写入服务。
- 在 Agent 调用完成后持久化“实际注入”的候选（不是仅搜索到但未选择的候选）。
- 记录 document/chunk/version/hash、rank、score、embedding/chunking/retrieval version、query hash、预算、RAG 开关和 traceId。
- 新增按 WorkflowRun/StepRun/AgentRun 查询来源摘要的权限安全 API/VO，供 R4/R6 前端使用。
- 处理 Agent 调用失败、写入失败、重复消费/重试、删除/失效文档后的历史显示和 mock 标记。
- 添加持久化、权限、重复、排序、历史来源、敏感内容脱敏和查询性能测试。

## 非目标

- 不存储/返回完整用户 query、完整原文或完整 Prompt，除非受控受限引用机制另有定义。
- 不重新执行检索来伪造历史引用。
- 不实现知识库管理 UI 或 RAG 对照实验聚合。
- 不让 RetrievalRecord 成为向量库的替代索引。
- 不删除历史记录来迎合文档删除。

## 约束

- 仅记录真正进入 Prompt 的候选；检索候选与实际使用集合必须可区分。
- 使用 query hash/安全摘要，不持久化可泄露用户输入或 Prompt 的原文。
- AgentRun 与 RetrievalRecord 的写入必须有明确一致性：调用成功但记录失败不得悄悄报告“RAG 成功”；应按策略标记、重试或降级。
- 重复消息/attempt 重试时记录必须关联正确 AgentRun/attempt，不能覆盖或重复无界增长。
- 文档删除后历史 Record 仍展示 document/chunk/version/source 摘要，但新检索不命中。
- 查询 API 必须检查 AgentRun 所属项目/用户，不得通过 retrievalUuid 跨项目读取。

## 验收标准

- [ ] 每个 RAG-on 的真实 AgentRun 可查询实际引用 chunk 的 rank、score、document/version 和技术版本。
- [ ] RAG-off/无候选 Run 有明确空记录/开关事实，不被误表示为检索成功。
- [ ] 记录不会暴露完整 query、Prompt、Secret 或跨项目文档正文。
- [ ] 删除/失效文档后历史引用仍可解释，新运行不可再选择该 chunk。
- [ ] 重复消费、重试和 Agent 失败的记录语义有测试，避免覆盖/无限重复。
- [ ] R4 查询和 R5 Metric/评测关联仍通过。

## 验证命令

```powershell
cd backend-java
mvn -Dtest=*RetrievalRecord*Test,*AgentRun*Test,*KnowledgeIsolation*Test test
mvn test

cd ..\python-agent
python -m pytest

cd ..
.\tools\verify.ps1 -Profile quick
.\tools\verify.ps1 -Profile integration
```

## 审查清单

- 是否记录了所有搜索候选而非实际注入候选。
- 是否存储完整 query/Prompt/文档正文造成敏感泄露。
- 是否文档删除后历史引用消失或新检索仍能命中。
- 是否不按 AgentRun attempt 关联，导致重试覆盖证据。
- 是否 retrieval 查询缺少 project/user 授权。

## 完成定义

- 每次 RAG 使用都有持久、权限安全、历史可解释的引用证据。
- R6 对照评测与前端展示可基于事实记录而非重新检索猜测。
