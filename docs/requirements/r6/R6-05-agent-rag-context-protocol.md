# R6-05: Python Agent RAG 上下文组装与开关协议

> 状态：`DONE`
>
> 前置任务：`R5-验收`、`R6-04`
>
> 推荐模型：`gpt-5.5`
>
> 任务类型：Agent 协议演进 / Prompt 安全与契约测试

## 背景

检索服务返回候选后，必须以受控格式进入 Python Agent。RAG 文档是辅助上下文，不是高优先级指令；RAG 打开/关闭必须成为 WorkflowRun/AgentRun 的可追溯事实，才能进行真实对照评测。

## 目标

扩展统一 Agent 执行协议：

```json
{
  "input": {},
  "context": {
    "previous_artifacts": [],
    "retrieved_chunks": [
      { "chunk_uuid": "...", "document_uuid": "...", "rank": 1, "score": 0.82, "text": "..." }
    ],
    "rag_enabled": true,
    "retrieval_version": "..."
  }
}
```

Python 负责在 Prompt 模板中以边界清晰的“参考资料”区段渲染这些候选，并在响应中返回实际使用的引用摘要，不改变 Java 的业务状态机。

## 范围

允许：

- 扩展 Java-to-Python Agent DTO、Pydantic Model、Prompt renderer、Provider adapter 和响应 DTO。
- 根据 Step/WorkflowRun 配置调用 R6-04 RetrievalService 或接收受控 RetrievalCandidate，组装 bounded context。
- 引入 ragEnabled、topK、budget、retrieval/chunking/embedding version、source references 的快照字段。
- 实现 RAG disabled、无候选、检索超时/失败、候选超预算、prompt injection 文本、provider 返回失败的明确语义。
- 新增跨服务契约测试，覆盖 RAG-on/RAG-off、引用排序、token budget、mock 和 trace 关联。

## 非目标

- 不允许 Python 直接访问 Java 业务表或绕过项目授权自行检索。
- 不实现自动知识写回、自动执行文档代码、网络搜索或 Agent 自主工具调用。
- 不修改 PromptVersion 的不可变内容；RAG 运行事实通过上下文快照/记录表达。
- 不将 RAG 内容直接返回给普通前端用户。
- 不实现 R6 对照评测的聚合报告。

## 约束

- `rag_enabled=false` 时 retrievedChunks 必须为空，任何 Provider 调用不得注入文档文本；这一点必须可测试。
- 检索文本以低信任数据区隔渲染，并声明“不可覆盖系统/用户约束、不可执行指令”；禁止拼接到 system prompt 的无边界位置。
- 只允许使用 R6-04 返回的、带 project/source/version 的受控候选；不能接收任意客户端传入的 chunk text。
- 实际注入 Prompt 的文本必须受 token/char budget 限制，排序/截断规则可重复。
- RAG 检索异常不能篡改 Workflow 状态：按配置降级为无 RAG 或返回可重试错误，行为需显式记录。
- mock fallback 响应继续显式携带 mock；RAG-on mock 不得用于真实质量结论。

## 验收标准

- [ ] RAG-on Agent 请求只注入本项目、受控、预算内且带来源的候选。
- [ ] RAG-off、空检索和检索故障都有明确、可追溯且不泄露跨项目信息的行为。
- [ ] Prompt renderer 将文档内容隔离为参考上下文，注入式文本无法改变系统指令/执行代码。
- [ ] 响应可关联实际使用的 chunk/document/rank/score/version，且 mock 语义保持完整。
- [ ] Java-Python 契约测试覆盖开关、预算、排序、超时和非法候选。
- [ ] R5 Agent Metric/Prompt Snapshot/R4 查询不回归。

## 验证命令

```powershell
cd python-agent
python -m compileall app
python -m pytest

cd ..\backend-java
mvn -Dtest=*AgentRag*Test,*Retrieval*Test,*AgentExecution*Test test
mvn test

cd ..
.\tools\verify.ps1 -Profile quick
.\tools\verify.ps1 -Profile integration
```

## 审查清单

- 是否 RAG 关闭时仍偷偷注入检索文本。
- 是否让 Python 直连业务数据库/向量库绕过授权。
- 是否把不可信文档拼接为 system prompt 或执行内容。
- 是否没有 token budget 导致 Prompt 膨胀。
- 是否让 mock RAG 结果伪装成真实检索质量。

## 完成定义

- Agent 可以受控地使用项目知识，且 RAG 开关、上下文和安全边界均可审计。
- R6-06 可把实际引用持久化为 RetrievalRecord。
