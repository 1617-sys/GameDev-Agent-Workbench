# R6 RAG Knowledge Report

## 环境

- 日期：2026-07-13
- 分支：`codex/rag-pipeline`
- 验收基线 commit：`23e2166`
- Embedding provider：`FakeEmbeddingProvider`（`fake-hash-v1`，8 维，仅用于确定性测试）
- Vector provider：`InMemoryVectorStore`（进程内、非持久化）
- 浏览器：Playwright Chromium，桌面 1440px 与移动端 375px

## 文档与索引

- Upload/parse lifecycle：Markdown/TXT/PDF 的扩展名、声明 MIME、内容特征、空文件、NUL、路径遍历和 10 MiB 上限由 `KnowledgeUploadSecurityService` 检查；上传返回 `UPLOADED`，解析与索引通过 `TaskExecutor` 异步触发。Markdown/TXT 路径可形成 `UPLOADED → PARSING → INDEXING → READY` 证据。
- Chunking/Embedding/index：`KnowledgeChunker.VERSION=v1-400-40`；Chunk 持久化 project/document/ordinal/hash/token/chunking/embedding/vector/text reference，向量 metadata 带 project/document/version/status/model/chunking。索引重复调用有基础幂等检查。
- Delete/invalidation：验收中修复 Document 失效/删除未联动向量与 Chunk 失效的问题；`KnowledgeDocumentServiceImpl` 现调用 `KnowledgeIndexingService.invalidate`，删除向量并将 Chunk 标为 `INVALID`。
- Project isolation：Document/Chunk 查询带 `projectId`；验收中增加 Retrieval 的数据库 READY/版本/Chunk `INDEXED` 检查，与 Vector metadata `projectId/status` 形成双过滤。

## 检索与 Agent

- Retrieval filters/budget：校验 project、query 长度、`topK=1..20`、最小 score 与字符预算；稳定 rank、去重并限制候选数。数据库和向量 metadata 均按项目过滤。
- RAG-on/off protocol：Java 请求携带 RAG 开关、预算和 retrieval/chunking/embedding 版本；Python 仅在 RAG-on 渲染带 `UNTRUSTED REFERENCE MATERIAL` 边界的预算内文本。RAG-off schema 拒绝携带 chunk。
- RetrievalRecord provenance：仅在 Agent 成功响应后记录 Python 返回的实际 `used_references`，持久化 project/run/document/chunk/rank/score/version/budget/mock/query hash；RAG-off 不写记录，历史记录不被重写。
- Prompt safety/mock：检索文本使用普通字符串拼接和 Vue 转义展示，不作为系统指令、模板、SQL 或代码执行；DISABLED、EMPTY、UNAVAILABLE 与 mock 均为显式状态。

## 对照评测与前端

- RAG-on/off experiment：`RagComparisonService` 按 project、输入实验 key、PromptVersion、provider/model、文档快照和 retrieval/chunking/embedding 版本筛选固定 cohort。
- Evaluation/metric impact：聚合样本、Schema/Rule/Runtime 通过率、P50/P95、token、成本缺失、检索覆盖、空检索与失败；默认排除 mock，版本混用和单边样本返回不可比较/样本不足。
- Knowledge/evidence UI：知识列表只返回文档名称、状态、版本、短 hash 与通用失败摘要；运行证据只展示实际 RetrievalRecord。跨项目 API/UI 测试、长文件名折行、1440px/375px 截图与无横向溢出检查通过。

## Harness 结果

| 命令 | 结果 | 证据 |
| --- | --- | --- |
| `git status --short` / `git diff --check` | PASS | 验收开始工作区干净；最终 diff 检查在提交前再次执行 |
| `docker compose config --quiet` | PASS | Compose 配置可解析，无需启动付费或公网服务 |
| `backend-java/mvn test` | PASS WITH SKIP | 131 项通过，1 项 Testcontainers 基础设施测试因本机无 Docker 跳过 |
| `python -m compileall app` | PASS | Python Agent 源码编译通过 |
| `python -m pytest` | PASS | 3 项通过，包括 RAG-off 与不可信预算上下文 |
| `npm run test:unit` | PASS | 20 项通过，包括 multipart 与 RAG 状态语义 |
| `npm run test:e2e` | PASS | 6 项通过，包括跨项目知识 UI、实际引用和桌面/移动端截图 |
| `npm run test:runtime-smoke` | PASS | 2 项通过，Phaser 1280px/375px readiness |
| `npm run build` | PASS | Vue production build 通过；仅有既有大 chunk 警告 |
| `verify.ps1 -Profile quick` | PASS | Java、Python compile、Vue build、Compose config 通过 |
| `verify.ps1 -Profile integration` | PASS WITH SKIP | Profile 成功，但 3 项 Testcontainers/RabbitMQ 用例因无 Docker 全部跳过 |
| `verify.ps1 -Profile e2e` | PASS | 6 项 Playwright 测试通过 |
| R6 代码证据与弱密钥扫描 | PASS | 项目隔离字段存在；验收卡列出的敏感模式无匹配 |
| 失效与 Retrieval 双过滤回归 | PASS | `KnowledgeDocumentServiceImplTest`、`KnowledgeIndexingServiceTest`、`RetrievalServiceTest`、`RetrievalRecordServiceTest` 共 14 项通过 |

## 阻断项与已知风险

### 阻断 R6 验收

1. R6-02 PDF 解析未满足契约：当前仅验证 `%PDF-` 后将 PDF 原始字节按 UTF-8 读取，没有受限 PDF 纯文本提取、页数上限、提取文本上限、解析超时和并发控制证据。
2. R6-03 索引恢复未满足契约：缺少明确的 Indexing job/worker 状态、有限重试、退避、成本/attempt 事实；部分 Chunk 成功后失败时，当前粗粒度 `selectCount > 0` 幂等判断可能掩盖不完整索引。
3. R5 前置验收仍为 `BLOCKED`：Runtime 浏览器结果尚未持久化为真实 `RUNTIME EvaluationReport`，Prompt 生命周期和两版本对照口径仍有未完成项，因此 R6-07 无法证明完整三层真实价值基线。
4. R6-08 写 capability 未完成：后端未开放文档失效/删除 API，UI 只能诚实禁用对应按钮，未达到“用户可在 UI 失效文档”的验收项。
5. 当前环境无可用 Docker，integration profile 的 Testcontainers/RabbitMQ 三项测试实际跳过；需在具备 Docker 的 CI/R7 环境重跑。

### R7 风险

- `FakeEmbeddingProvider` 与 `InMemoryVectorStore` 只适合确定性开发测试，不提供持久化、真实语义质量、容量、并发或故障恢复保证。
- 前端 production bundle 有大于 500 KiB 的既有警告；归属 R7 性能与打包阶段，不阻断当前功能测试。

## 验收结论

| 验收项 | 结论 |
| --- | --- |
| 跨项目 Document/Chunk/Vector/RetrievalRecord 隔离 | PASS（自动化与代码审查） |
| 实际引用 chunk/source/rank/score/version | PASS（持久化、只读 DTO 与 UI Harness） |
| 失效后新检索排除、历史来源保留 | PASS（验收修复及回归；历史 RetrievalRecord 未改写） |
| RAG-off/empty/failure/mock 语义 | PASS |
| 同条件 Schema/Rule/Runtime 价值证明 | BLOCKED（R5 Runtime 持久化前置缺失） |
| 上传与 PDF 安全解析完整性 | BLOCKED（PDF/资源限制未完成） |
| Maven/Python/Vue/Harness | PASS WITH ENVIRONMENT SKIP |

## R7 准入结论

- `BLOCKED`
- 原因：虽然项目隔离、RAG 协议、实际 RetrievalRecord、对照聚合和权限安全 UI 已形成可重复证据，R6-02 PDF 安全解析、R6-03 可恢复索引、R5 三层真实评测前置和 R6-08 失效写 capability 尚未满足冻结契约；此外 Docker 集成用例未在本机实际执行。完成上述阻断项并在 Docker 环境重跑 integration 后，方可进入 R7 的打包、压测和演示阶段。
