# R6-01: KnowledgeDocument/Chunk 数据模型与项目隔离生命周期

> 状态：`DONE`
>
> 前置任务：`R6-00`
>
> 推荐模型：`gpt-5.5`
>
> 任务类型：数据库迁移 / 授权与生命周期测试

## 背景

RAG 不是把文件丢进向量库就结束。项目必须知道文档属于谁、当前版本是什么、解析/索引是否完成、删除后能否检索、历史 AgentRun 当时引用了什么。这个任务先建立可靠数据地基。

## 目标

引入最小生命周期模型：

```text
KnowledgeDocument
  projectId, documentUuid, name, sourceType, contentHash, version,
  status, storageRef, parsedAt, indexedAt, deletedAt

KnowledgeChunk
  chunkUuid, documentId, projectId, ordinal, textRef/textHash,
  tokenCount, chunkingVersion, embeddingModel, indexStatus
```

支持 `UPLOADED -> PARSING -> INDEXING -> READY -> INVALID/DELETED/FAILED` 等明确状态，并让项目授权贯穿所有查询。

## 范围

允许：

- 新增 Flyway migration、Entity、Mapper/Repository、状态枚举、索引、唯一约束和服务层生命周期方法。
- 为 documentUuid、projectId、contentHash、version、status、chunk ordinal、deleted 设计可查询的索引/约束。
- 实现按用户/项目权限创建、读取、列出、标记失效/删除的最小服务接口。
- 设计/实现历史保留：删除或新版本不重写已有 RetrievalRecord 的引用证据。
- 新增 migration、项目隔离、同 hash 重复上传、并发版本、状态转换、软删除和历史引用测试。

## 非目标

- 不实现文件上传 HTTP、文本提取、切块、Embedding 或向量检索。
- 不构建用户可编辑文档协作、文件夹、标签体系或全文搜索 UI。
- 不物理删除仍被历史 RetrievalRecord 引用的证据。
- 不接入 RAG Prompt 组装或 R5 对照评测。
- 不把文档正文直接存入浏览器 localStorage。

## 约束

- 每次读写均以 userId + projectId 进行授权，不能仅凭 documentUuid 操作。
- 同项目同内容 hash 的去重/版本语义须固定：复用、拒绝或创建新版本只能选一种并有测试。
- 文档/Chunk 状态必须由后端状态机控制，前端状态文字不是安全边界。
- DELETED/INVALID 文档的 chunk 不能被新检索选择，但历史 RetrievalRecord 仍能显示来源摘要与版本。
- projectId 在 Document、Chunk 与未来 vector metadata 中必须一致，不能通过 Document 关联后省略。
- migration 只前进，历史记录兼容可空字段，不做破坏性回滚。

## 验收标准

- [ ] 文档、chunk、版本、hash、索引状态和项目归属可持久化查询。
- [ ] 用户不能读写其他项目的 document/chunk，即使知道 UUID。
- [ ] 同项目重复内容、不同项目相同内容、并发上传版本都遵循固定规则。
- [ ] DELETED/INVALID 文档不再成为新检索候选，历史引用仍可解释。
- [ ] 状态非法跳转、重复 ordinal、跨项目 chunk 关联均被约束/测试拦截。
- [ ] R0-R5 迁移与 quick Harness 不回归。

## 验证命令

```powershell
cd backend-java
mvn -Dtest=*KnowledgeDocument*Test,*KnowledgeChunk*Test,*KnowledgeMigration*Test test
mvn test

cd ..
.\tools\verify.ps1 -Profile quick
.\tools\verify.ps1 -Profile integration
```

## 审查清单

- 是否只在 Controller 验权，Repository 查询仍可跨项目读取。
- 是否删除后丢失历史 RetrievalRecord 证据。
- 是否不记录 contentHash/version，导致重复和历史不可解释。
- 是否把 parsing/indexing 状态当自由文本而无状态转换保护。
- 是否在 R6 初期强行做文档编辑或向量检索。

## 完成定义

- R6 有可授权、可版本化、可删除失效且可追溯的知识文档地基。
- 后续上传、Embedding、检索可以安全地引用这套事实来源。
