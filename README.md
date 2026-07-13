# GameDev Agent Workbench

一个把“游戏创意”转换为可审计工作流、结构化 GameConfig 与 Phaser 演示产物的 AI 工程工作台。Java 负责业务状态、事务、可靠消息与权限边界；Python 负责 Agent/RAG 协议；Vue 负责提交、SSE 运行中心、知识证据和可玩页面。

```powershell
# Windows PowerShell 5.1+ or PowerShell 7 + Docker Desktop
.\start-docker.ps1
# 浏览器打开 http://127.0.0.1:5173/
```

> 当前发布结论：**BLOCKED，不是可发布版本。** 本地 quick 回归可运行，但 R7 主链路在异步提交门收到业务码 `50302`，因此 E2E、性能、故障恢复与 Demo 的下游结论均未通过。详见[报告索引](docs/reports/README.md)和[已知限制](#已知限制)。本仓库没有生产规模、真实模型质量或容量承诺。

## 它解决什么问题

普通的模型调用 Demo 很难回答状态如何恢复、重复请求会不会重复执行、输出能否追溯、RAG 引用是否真实使用、失败时如何诊断。这个项目把这些问题拆成一条持久化工程链路：

```text
用户 / 项目 / 知识文档
  -> 幂等提交 WorkflowRun + Outbox
  -> RabbitMQ Consumer 执行冻结的四步工作流
  -> AgentRun / Metric / RetrievalRecord / EvaluationReport
  -> Artifact / GameConfig
  -> SSE 运行中心与 Phaser 页面
```

## 架构一览

```mermaid
flowchart LR
    U["Browser · Vue 3"] -->|"JWT HTTP / SSE"| J["Spring Boot · Java 21"]
    J --> M[("MySQL 8.4")]
    J --> R[("Redis")]
    J --> Q[("RabbitMQ 3.13")]
    Q --> W["Workflow Consumer"]
    W --> P["FastAPI Agent · Python 3.13"]
    P --> L["Mock or optional external Provider"]
    J --> U
    U --> G["Phaser runtime"]
```

完整组件边界、提交/执行/SSE/RAG/评测时序和数据关系见[架构与核心链路](docs/architecture/system-architecture.md)。

## 已实现能力与证据边界

| 能力 | 当前实现 | 可核验证据 |
| --- | --- | --- |
| 身份与项目隔离 | JWT、用户/项目归属校验、Artifact/Document/Retrieval 等读写边界 | [R7 安全审计](docs/reports/R7-security-release-audit.md)（单元边界通过，Compose 双用户验证仍阻断） |
| 工作流领域与 Runner | 版本化定义快照、StepRun 状态、四步 `DEMO_GAME_CONFIG`、结构化 Artifact | [R1 领域报告](docs/reports/R1-workflow-domain-report.md)、[R2 Runner 报告](docs/reports/R2-workflow-runner-report.md) |
| 异步可靠性骨架 | Idempotency-Key、短事务提交、Outbox、RabbitMQ Consumer、恢复审计 | [R3 Harness](docs/reports/R3-08-async-integration-concurrency-harness.md)；当前 R7 集成被 `50302` 阻断 |
| 运行中心 | 服务端快照、持久化事件序号、SSE 重放、取消/重试、刷新恢复 | [R4 运行中心报告](docs/reports/R4-run-center-report.md) |
| Prompt/评测/指标 | PromptVersion 快照、Schema/Rule、Metric、mock 分层统计 | [R5 报告](docs/reports/R5-prompt-evaluation-metrics-report.md)（Runtime 持久化与版本生命周期仍阻断） |
| 知识与 RAG 证据 | 上传校验、Chunk/检索隔离、实际引用记录、RAG-on/off 显式状态 | [R6 报告](docs/reports/R6-rag-knowledge-report.md)（PDF/索引恢复等仍阻断） |
| 可玩产物 | GameConfig 契约、Artifact eligibility、Phaser 桌面/移动 smoke | [R2 报告](docs/reports/R2-workflow-runner-report.md)、[R6 报告](docs/reports/R6-rag-knowledge-report.md) |
| 可观测与安全加固 | 关联 ID、低基数指标、health/readiness、生产端点关闭、内部服务鉴权 | [R7 可观测报告](docs/reports/R7-observability-operations-report.md)、[R7 安全审计](docs/reports/R7-security-release-audit.md) |

“已实现”不等于“发布 gate 已通过”。报告中的 `BLOCKED`、环境跳过和 `NOT RUN` 均保留原义。

## 快速启动

### 前置条件

- Windows 10/11、PowerShell 7、Git、Docker Engine/Compose v2
- 至少 4 个逻辑 CPU、8 GiB 内存、20 GiB 可用磁盘
- 默认端口可用：Vue `5173`、Java `8080`、Python `8000`、MySQL `3307`

### 启动、检查、停止

```powershell
.\start-docker.ps1
docker compose ps
.\tools\verify.ps1 -Profile quick
.\tools\stop-docker.ps1
```

首次启动会生成仅供本机使用且被 Git 忽略的 `.env`，默认使用明确标记的 mock fallback，不要求个人模型凭据。详细健康门禁、端口覆盖、已有 volume 升级和安全停止方式见[Docker 一键启动](docs/docker-one-click-start.md)。

## 主链路

异步入口要求 `Idempotency-Key`：

```http
POST /api/v1/projects/{projectUuid}/workflow-runs
GET  /api/v1/workflow-runs/{workflowRunUuid}
GET  /api/v1/workflow-runs/{workflowRunUuid}/events
GET  /api/v1/workflow-runs/{workflowRunUuid}/rag-evidence
POST /api/v1/workflow-runs/{workflowRunUuid}/cancel
POST /api/v1/workflow-runs/{workflowRunUuid}/retry
```

提交只负责鉴权、幂等创建 `WorkflowRun` 和 `OutboxEvent`；Consumer 读取冻结定义执行四步 Agent，持久化 Step/Agent/Metric/Retrieval/Evaluation/Artifact。浏览器依靠快照与 SSE 序号恢复展示，不以页面内存作为事实源。详见[核心时序](docs/architecture/system-architecture.md#核心时序)。

## Demo

默认 Demo 是 **DEMO / MOCK**，不能作为真实模型效果或性能证据：

```powershell
.\tools\prepare-demo.ps1
.\tools\verify-demo.ps1
.\tools\reset-demo.ps1
```

当前候选的 prepare 可在 90 秒门槛内完成基础设施与 seed，但会在同一个 R3 提交门被 `50302` 阻断；reset 已验证只清理 demo namespace 且不删除 volume。不要把这条阻断链路录成成功演示。操作口播、离线切换和录屏脱敏清单见[3–5 分钟 Demo 脚本](docs/demo-script.md)。

## 如何验证

```powershell
# 快速回归：Java tests、Python compile、Vue build、Compose config
.\tools\verify.ps1 -Profile quick

# 依赖 Docker 的集成与浏览器主链路
.\tools\verify.ps1 -Profile integration
.\tools\verify.ps1 -Profile e2e

# R7 专项；结果和环境资格必须与报告一起阅读
.\tools\run-performance-baseline.ps1
.\tools\run-fault-injection.ps1
# 安全审计命令矩阵见对应报告；仓库没有一键安全脚本
```

测试退出码为 0 也不能自动证明所有 Docker 用例执行过；报告会把环境跳过单列。完整命令、结果、证据路径和归属见[报告索引](docs/reports/README.md)。

## 技术栈

- Java 21、Spring Boot 3、Spring Security/JWT、MyBatis-Plus、Flyway、Micrometer
- MySQL 8.4、Redis 7.4、RabbitMQ 3.13、Outbox + Consumer
- Python 3.13、FastAPI、Pydantic、受控 Agent/RAG 协议
- Vue 3、Vite、SSE、Playwright、Phaser
- Docker Compose、Maven、npm、PowerShell Harness

## 已知限制

- R3 Redis rate-limit/Lua 集成在健康 Redis 上仍被归类为 unavailable，异步提交返回 `50302`；这是 E2E、性能、故障和 Demo 的共同阻断项。
- R5 的 Prompt 生命周期、浏览器 Runtime 评测持久化和完整版本对比尚未满足冻结契约。
- R6 的受限 PDF 解析、可恢复索引 job、文档失效写 capability 尚未完成；当前向量实现是进程内 fake，不代表真实语义质量或容量。
- R7 安全 gate 缺少完整 Compose 双用户、镜像和 Maven/Python CVE 扫描；Python 生产模式关闭 mock capability 的证据缺失。
- Testcontainers 在部分历史运行中因 Docker API 兼容性跳过。性能报告没有形成可发布的 P50/P95/P99、吞吐或容量结论。
- 这是单机 Compose 工程样例，不是生产高可用、多租户 SaaS 或线上收入/用户规模证明。

## 文档导航

- [架构与核心链路](docs/architecture/system-architecture.md)
- [报告索引与复现命令](docs/reports/README.md)
- [运维 Runbook](docs/operations-runbook.md)
- [Docker 一键启动](docs/docker-one-click-start.md)
- [Demo 脚本](docs/demo-script.md)
- [面试问答](docs/interview-qa.md)
- [简历描述](docs/resume-project-description.md)
- [3–5 分钟项目讲解](docs/project-narrative.md)
- [AI 协作规范](docs/AI_COLLABORATION.md)与[工程陷阱](docs/PITFALLS.md)

## AI 协作与贡献说明

本项目采用“人定义契约和边界，AI 辅助探索/实现/审查，Harness 与报告裁决结果”的协作方式。AI 产出不能替代代码审查、失败归属、数据边界判断或证据验证；失败报告同样提交，不把未通过项改写成亮点。用于面试或简历时，应按实际参与情况说明自己负责的需求拆解、设计取舍、AI 变更审查、自动化验证和文档沉淀，不应声称全部代码均为未经辅助的个人手写成果。

具体工作流见[AI 协作规范](docs/AI_COLLABORATION.md)，可核验的表述模板见[面试问答](docs/interview-qa.md)和[简历描述](docs/resume-project-description.md)。
