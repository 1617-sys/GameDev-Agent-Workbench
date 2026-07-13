# R6 验收: RAG 知识库与检索证据总验收

> 状态：`TODO`
>
> 前置任务：`R6-00`、`R6-01`、`R6-02`、`R6-03`、`R6-04`、`R6-05`、`R6-06`、`R6-07`、`R6-08`
>
> 推荐模型：`gpt-5.5`
>
> 任务类型：阶段验收 / 只验证与记录

## 背景

R6 的完成不是“接上了向量库”，而是 Agent 能在项目隔离、安全受控的前提下使用版本化知识，并能解释每次实际引用、量化 RAG 的收益与代价，同时不破坏 R5 的评测和 R3 的可靠性。

## 目标

新增 `docs/reports/R6-rag-knowledge-report.md`，以可复现证据证明：

```text
safe document lifecycle
+ deterministic chunking / versioned embedding / isolated vector metadata
+ project-filtered retrieval with bounded context
+ RAG-on/off Agent protocol and RetrievalRecord provenance
+ R5 evaluation/metric comparison
+ permission-safe knowledge and evidence UI
= R7 can package, benchmark, and demonstrate the complete system
```

## 范围

允许：

- 运行 R6/R5/R4/R3/R2/R1/R0 的相关 Maven、Python、Vue、quick/integration/e2e Harness 与浏览器检查。
- 审查文档上传/解析、向量 metadata、项目隔离、删除失效、RAG 开关、Prompt 安全、RetrievalRecord、对照指标、权限和敏感信息。
- 新增 R6 验收报告、更新 R6 任务卡状态、记录已知风险与 R7 准入结论。
- 仅修复阻断验收的最小问题并补充回归测试。

## 非目标

- 不做全网搜索、爬虫、GraphRAG、自动执行文档代码或开放式聊天产品。
- 不实现 R7 的 Docker 一键演示、性能压测、故障注入全套报告、录屏和求职材料。
- 不重写 R3 MQ/Outbox/恢复、R4 运行中心、R5 Prompt/评测/指标模型。
- 不删除历史 Document/Chunk/RetrievalRecord 证据或进行破坏性数据回滚。
- 不使用真实生产密钥/付费模型作为验收唯一前提。

## 验收项目

### 知识与隔离

- Markdown/TXT/PDF 受限上传、异步解析、版本/hash/状态可追溯。
- Chunk、Embedding、Vector metadata 同时包含 project/document/version/status，删除失效后不再检索。
- 用户无法通过 UUID、向量搜索、API 或 UI 读取/检索其他项目知识。

### Agent 与证据

- RAG-on 只注入受控、预算内、实际选择的候选；RAG-off 不注入任何检索文本。
- Python 响应、AgentRun 与 RetrievalRecord 可关联实际 chunk/source/rank/score/版本。
- 检索文本不可覆盖系统约束、执行代码或泄露秘密；mock 仍显式可见。

### 价值验证与体验

- RAG-on/RAG-off 使用同一 Prompt/model/project fixture 和 R5 评测口径对比。
- 报告包含样本、通过率、成本、延迟、检索覆盖与版本，明确 mock/空知识库/缺失数据。
- 知识库和来源 UI 权限安全，移动/桌面可用，来源展示不泄露完整敏感正文。

## 验收标准

- [ ] 不同项目不能检索、查看或操作彼此 Document/Chunk/Vector/RetrievalRecord。
- [ ] 每个真实 RAG AgentRun 可查看实际引用的 chunk、score、rank、文档来源和版本。
- [ ] 删除/失效文档后新任务不再引用，历史 Run 仍能解释当时来源。
- [ ] RAG-off 无检索注入；RAG-on 检索失败/空候选/mock 均有明确记录，不伪装成成功。
- [ ] 对照报告可展示 RAG 对 Schema/Rule/Runtime 通过率、延迟、token、成本的影响和样本条件。
- [ ] 上传安全、Prompt injection、隔离、删除、重复索引、预算、RAG 开关均有自动化测试。
- [ ] Maven/Python/Vue、quick/integration/e2e Harness 通过，生成 R6 报告并给出 R7 准入结论。

## 验证命令

```powershell
git status --short
git diff --check
docker compose config

cd backend-java
mvn test

cd ..\python-agent
python -m compileall app
python -m pytest

cd ..\frontend-vue
npm run test:unit
npm run test:e2e
npm run test:runtime-smoke
npm run build

cd ..
.\tools\verify.ps1 -Profile quick
.\tools\verify.ps1 -Profile integration
.\tools\verify.ps1 -Profile e2e

rg -n "KnowledgeDocument|KnowledgeChunk|Embedding|VectorStore|RetrievalRecord|ragEnabled|projectId" backend-java\src\main\java python-agent\app frontend-vue\src
rg -n "161764|password:\s*123456|local-secret-key|change-this-to-a-strong-secret-key|\bsk-[A-Za-z0-9]{20,}" `
  backend-java frontend-vue python-agent .env.example docker-compose.yml
```

## R6 报告模板

```markdown
# R6 RAG Knowledge Report

## 环境
- 日期：
- 分支：
- 基线 commit：
- Embedding/Vector provider：

## 文档与索引
- Upload/parse lifecycle：
- Chunking/Embedding/index：
- Delete/invalidation：
- Project isolation：

## 检索与 Agent
- Retrieval filters/budget：
- RAG-on/off protocol：
- RetrievalRecord provenance：
- Prompt safety/mock：

## 对照评测与前端
- RAG-on/off experiment：
- Evaluation/metric impact：
- Knowledge/evidence UI：

## Harness 结果
| 命令 | 结果 | 证据 |

## 已知风险
- 风险：
- 归属阶段：R7

## R7 准入结论
- PASS / BLOCKED
- 原因：
```

## 审查清单

- 是否只保证 API 层隔离却遗漏 vector metadata filter。
- 是否文档删除后仍可检索，或历史来源不可解释。
- 是否把检索文档作为可信系统指令/可执行内容。
- 是否 RAG-off 仍注入内容，或 mock/空候选混入真实结论。
- 是否对照实验混入不同 Prompt/model/doc versions。
- 是否将 R7 性能/演示工作提前混入 R6。

## 完成定义

- R6 报告和任务状态已更新，RAG 的安全、隔离、可追溯性和价值都有可重复证据。
- R7 可基于完整系统进行一键启动、故障/并发验证、演示与求职材料整理。
