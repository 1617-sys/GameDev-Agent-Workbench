# GameDev Agent Workbench 项目深度分析与面试准备

> 分析日期：2026-08-22（按当前工作树复核，包含 Flyway V38）
> 目标岗位：Java 后端开发实习生（中小厂）
> 证据口径：本文只陈述代码、配置、迁移、测试和仓库报告能够支持的事实。

## 0. 先说结论

这是一个“受约束的小型游戏生成与自动试玩实验平台”，而不是普通 CRUD，也不是“一句话生成任意游戏”的成熟产品。用户输入游戏创意后，系统可以通过版本化工作流或 Spring AI 生成结构化规格，由 Java 做权威校验和状态控制，再驱动固定游戏运行时、自动 Player、实验 Director、人工审批及产物导出。

- 【代码确认】V4 主链路包含工作流、不可变原型版本、确定性模拟、Player 批量试玩、Director 类型化工具、人工审批、遥测和导出。证据：`backend-java/src/main/resources/db/migration/V27__add_game_generate_workflow_definition.sql` 至 `V36__add_director_experiment_loop.sql`，以及对应 Controller/Service。
- 【代码确认】V5 已实现 `GameSpec -> Java 编译/诊断 -> Runtime IR -> Cocos 构建 -> ZIP 产物` 的垂直切片，当前只支持 `arcade_collect`。证据：`GameSpecCompiler`、`GenerationRunService`、`CocosBuildWorker`、`PlayableArtifactAssembler`。
- 【代码确认】异步工作流确实使用 MySQL Outbox、RabbitMQ publisher confirm、手动 ACK、Redis 锁、数据库乐观抢占、延迟重试队列、DLQ 和恢复扫描，不是“只在 pom 里加了依赖”。
- 【代码确认】本次本机实跑：Java `mvn clean test` 共 240 项通过；前端 `npm run test:unit` 共 76 项通过；Python `python -m pytest -q` 共 48 项通过。Java 直接运行未 clean 的增量测试曾因残留 `target` 产物导致 JUnit discovery 失败，clean 后恢复，说明构建可重复性仍需注意。默认 Surefire 不执行命名为 `*IT` 的 `AsyncWorkflowIntegrationHarnessIT`，不能把 240 项等同于完整端到端验证。
- 【代码确认】V5 已定义 `READY_TO_BUILD -> BUILDING -> AWAITING_APPROVAL -> APPROVED/REJECTED -> RELEASED` 发布门禁。此前 claim 后继续传递 `READY_TO_BUILD` 旧 Entity 的 P1 已修复：claim 成功后重新读取并核对 run/project、`BUILDING`、版本和 claim token，再把 durable snapshot 交给构建与打包组件；真实 `PlayableArtifactAssembler` 组合测试及错误 token 反向测试均已通过。尚未执行真实 Cocos CLI 端到端构建。证据：`GenerationRunService.build`、`GenerationRunServiceTest.usesDurableClaimSnapshotWithRealArtifactAssembler/doesNotStartCocosWhenReloadedClaimBelongsToAnotherWorker`。
- 【代码确认】RAG 是 fake-hash 8 维嵌入加进程内检索基线，不能写成生产级语义检索。证据：`FakeEmbeddingProvider`、`InMemoryVectorStore`。

最适合你的项目叙事不是“我做了很多微服务组件”，而是：**我把不可靠的模型输出约束成可校验、可追踪、可恢复、可人工审批的工程流水线。**

---

## 1. 项目用途

### 1.1 要解决的问题

【代码确认】LLM 可以生成游戏设计内容，但自由文本或自由代码存在幻觉、不可复现、不可审计和越权执行风险。本项目用封闭的 `GameConfig/GameSpec` 契约、Java 校验器、能力白名单、摘要、版本快照、状态机和审批门禁，把模型限制在固定能力范围内。

【合理推断】根据 UI、接口和产品文案，目标用户主要是：

1. 想快速验证小型游戏创意的独立开发者或策划；
2. 希望比较不同配置、Player Persona 或 Agent 策略的实验者；
3. 需要查看运行证据、失败诊断和可下载原型的项目所有者。

典型场景：输入一个“限时收集并到达出口”的创意，生成规格，Java 拒绝不支持的字段或越界参数，模型根据诊断最多修复三轮，构建固定 Cocos Runtime，自动 Player 运行多组 seed/persona，最后由人审批候选版本。

### 1.2 核心与辅助功能

核心功能：

- 版本化多步骤 Agent 工作流；
- 受约束 GameConfig/GameSpec 的生成、校验和编译；
- 确定性模拟、回放和机器 Player；
- Director 选择白名单工具、控制预算并保存检查点；
- 不可变原型版本、人工审批及带证据导出。

辅助功能：

- JWT 登录和项目级资源隔离；
- Redis 限流、缓存和防重锁；
- RabbitMQ 异步执行、Outbox、重试/DLQ；
- SSE 运行事件、traceId、Micrometer/Prometheus；
- 知识文档生命周期和 RAG 证据基线；
- Docker Compose、本地演示和故障/性能脚本。

### 1.3 完成度判断

| 能力 | 判断 | 证据与边界 |
|---|---|---|
| V4 工作流生成 | 【代码确认】可运行 | Java/Python/Vue 均有实现和测试；旧 Phaser 运行时被 README 标为 legacy |
| 异步消息链路 | 【代码确认】主体实现 | 首次投递可靠性较完整；执行失败重试存在 P1 状态机缺陷 |
| 确定性 Simulation/Replay | 【代码确认】较完整 | 前端单元测试含 100 次同输入同 hash 校验 |
| Player Agent | 【代码确认】可运行 | 支持确定性与逐步 LLM 策略；真实模型质量未被证明 |
| Director | 【代码确认】有真实 Spring AI 与 Python 回退 | 工具结果与幂等缓存仍为内存，重启恢复边界有限 |
| V5 Cocos 构建与发布 | 【代码确认】状态机/API 已接通，stale Entity P1 已修复并通过组合测试 | 仍依赖本机 Cocos，尚未做真实 CLI 端到端验证，且未接入 V4 Player |
| RAG | 【代码确认】原型基线 | fake embedding + 内存检索，不能作为核心卖点 |
| 生产高可用 | 【未发现实现】 | 单机 Compose、本地磁盘/内存存储，无多租户和集群证明 |

### 1.4 三种项目介绍

一句话：

> 一个用 Java 控制模型边界、工作流可靠性和产物门禁的 Agentic 小游戏生成与自动试玩平台。

30 秒：

> 我做了一个面向小游戏创意验证的 Agent 工作台。用户输入创意后，Spring AI 生成受约束的 GameSpec，Java 负责能力白名单、语义编译、构建状态机和人工发布门禁；项目还保留 Python Player、Director 与 RabbitMQ 工作流作为实验链路。当前只支持 arcade_collect，构建 claim 的跨组件状态同步 Bug 已修复并补了真实打包器组合测试，但真实 Cocos CLI 端到端仍待验证，因此我不会把它描述成完全可用的生产系统。

2 分钟：

> 这个项目最初是一个四步 LLM 游戏设计工作流，后来我把重点从“模型能生成什么”转成“怎样让生成过程可控”。当前 V5 主链中，Spring AI 只生成候选 GameSpec，Java 编译器负责封闭字段、范围、跨字段规则和能力白名单；通过后生成 canonical spec、Runtime IR 与 Build Request，并用 SHA-256 绑定来源。构建设计通过 `stateVersion + claim token + lease` 抢占，claim 后从数据库重读并验证持久化快照，再在事务外调用固定 Cocos Runtime Shell，成功后进入人工审批和显式发布。V4 还保留了 Outbox/RabbitMQ 工作流、确定性 Simulation/Replay、Python Player 和受预算约束的 Director。当前限制是只支持一种玩法，V4 与 V5 尚未统一，RAG 仍是 fake embedding，MQ 自动重试有状态机缺陷，且真实 Cocos CLI 端到端仍待验证。

---

## 2. 项目结构、技术栈与启动

```text
GameDev Agent Workbench/
├─ backend-java/          Spring Boot 控制面、领域状态、数据持久化、消息与构建
│  ├─ controller/         HTTP API
│  ├─ application/        工作流 Runner、Step Executor、Artifact Writer
│  ├─ service/            业务服务、Redis、Outbox、Player、遥测、导出
│  ├─ director/           Director 状态、决策客户端、工具注册和授权
│  ├─ gamespec/           V5 能力注册表、Spec Author 和 Java 编译器
│  ├─ messaging/          RabbitMQ Consumer、消息契约、错误分类
│  ├─ mapper/entity/      MyBatis-Plus 数据访问与持久化对象
│  └─ resources/db/       Flyway V1～V38 迁移
├─ python-agent/          FastAPI Agent、Player、LangGraph Director 回退
├─ frontend-vue/          Vue 3 UI、V4 Phaser Runtime、Simulation Core、E2E
├─ cocos-runtime-shell/   Cocos Creator 3.8 固定运行时壳
├─ docker/                MySQL 初始化和 Prometheus 配置
├─ tools/                 演示、E2E、故障注入、并发基线脚本
├─ docs/                  架构、需求、报告、证据和面试文档
├─ docker-compose.yml     本地七服务编排
└─ start-docker.ps1       Windows 一键启动入口
```

技术栈及实际用途：

| 技术 | 实际用途 |
|---|---|
| Java 21 / Spring Boot 3.5.16 | API、状态机、工作流、权限、构建控制面 |
| Spring AI 1.1.8 | GameSpec 结构化输出和 Director tool calling |
| Spring Security / JWT / BCrypt | 无状态鉴权、密码哈希、项目资源隔离 |
| MyBatis-Plus / MySQL 8.4 | 运行、步骤、产物、版本、事件、遥测和审计持久化 |
| Flyway | V1～V38 增量迁移和启动校验；V38 增加构建 claim 与发布审批门禁 |
| Redis 7.4 | 用户缓存、固定窗口限流、工作流与旧 SSE 短租约锁 |
| RabbitMQ 3.13 | `async` profile 下的工作流执行、延迟队列和 DLQ |
| Micrometer / Prometheus | 队列、执行、重试、RAG、模型调用等指标 |
| Python 3.13 / FastAPI | Agent API、Player 运行器、Director 回退接口 |
| asyncio | Player batch 使用 Semaphore 限制并发并 `gather` 汇总 |
| LangGraph | Python Director 的确定性状态图；当前 evidence 明确标 mock |
| Vue 3 / Pinia / Router | 项目、生成、运行、版本、Director 和证据 UI |
| Phaser 3 | V4 固定浏览器运行时，README 已标 legacy |
| Cocos Creator 3.8 | V5 固定 Runtime Shell 和本地 Web Mobile 构建 |
| Docker Compose | 单机开发/演示环境，不等于生产编排 |

启动流程：`start-docker.ps1` 校验环境并执行 Compose；MySQL 先启动，bootstrap 创建应用用户，RabbitMQ/Redis 启动；Java 读取 `.env` 注入 DB、JWT、内部 token 和模型配置，Flyway 校验迁移，`async` profile 启用消息拓扑与调度器；Python 暴露 8000，模拟服务和前端分别提供运行环境与 UI。前端入口为 `http://127.0.0.1:5173/`。

配置原则：敏感值通过环境变量注入；`.env` 被 `.gitignore` 排除，Git 只跟踪 `.env.example`。Java 主配置位于 `application.yml`，RabbitMQ 位于 `application-async.yml`，生产覆盖位于 `application-prod.yml`。

### 2.1 第一阶段审计台账

已确认：仓库不是单一 Spring Boot Demo，而是 Java 控制面、Vue UI、Python Agent、Simulation Service、Cocos Runtime Shell 的多运行时单仓；主启动类为 `GameDevAgentWorkbenchApplication`，启用 Mapper 扫描和异步执行；MySQL 由 Flyway V1～V38 演进；Compose 定义 MySQL 8.4、Redis 7.4、RabbitMQ 3.13、Java、Python、Simulation 和前端，并带健康检查、非 root/cap-drop 与持久卷。证据：根 `README.md`、`docker-compose.yml`、三个 Dockerfile、`application*.yml`、`start-docker.ps1`。

尚未确认或无法仅凭仓库证明：真实 DeepSeek 调用质量与费用、真实 Cocos 在不同 Windows 安装环境的稳定性、多实例压力下的吞吐、生产级安全性、远程对象存储/集群容灾，以及招聘方实际评价。仓库中的 reports/evidence 有历史失败截图和脚本报告，只能作为当时运行记录，不能替代本次复测。

本轮重点阅读范围：V5 的 `gamespec/generation/cocos/artifact`；V4 的 `application/workflow`、`messaging`、`director`、Player/MachineEpisode；全部 Controller 路由；Flyway V1～V38；Spring Security 与全局异常；Vue V5 页面和 API；Python Director/Player；Java、前端和 Python 测试入口。未把 README 或 requirements 文档本身当作实现证据。

---

## 3. 五条核心业务链路

### 链路 A：异步游戏设计工作流

**用户操作：** 在创作台提交 idea。
**入口：** `POST /api/v1/projects/{projectUuid}/workflow-runs`，`AsyncWorkflowController.submit`。
**调用顺序：** Controller → `AsyncWorkflowSubmissionServiceImpl` 校验用户/项目/幂等键 → Redis Lua 限流和 DB backlog gate → 读取 active workflow 与 Prompt 版本 → `AsyncWorkflowSubmitCommandService.create` 在同一事务写 Run、StepRun、RunEvent、Outbox → `OutboxPublisher` 抢占并发布 RabbitMQ → broker confirm 后 Run 变 `QUEUED` → `WorkflowMessageConsumer` 获取 Redis 锁与 DB 执行权 → `SynchronousWorkflowRunner` → `AgentStepExecutor` → `AgentRunService` → Spring AI 或 Python Agent → `DefaultArtifactWriter`。
**数据变化：** `PENDING -> QUEUED -> RUNNING -> SUCCESS/FAILED`；每步保存输入/上下文/输出快照和产物摘要。
**外部依赖：** MySQL、Redis、RabbitMQ、LLM/Python Agent。
**异常路径：** 发布失败回到 `RETRY_PENDING`；消费异常尝试延迟队列/DLQ；过期 PENDING/QUEUED/RUNNING 被恢复扫描。当前 Runner 与 Consumer 的失败状态存在 P1 断层。
**最终结果：** 查询 API 或 SSE 返回持久化事件、步骤和产物。
**关键代码：** `AsyncWorkflowSubmissionServiceImpl:45-116`、`AsyncWorkflowSubmitCommandService:26-60`、`OutboxPublisher:53-126`、`WorkflowMessageConsumer:48-189`、`SynchronousWorkflowRunner:51-134`。

### 链路 B：自然语言生成并修复 GameSpec

**用户操作：** 在 V5 生成台输入创意并点击“生成/修复”。
**入口：** `POST /api/v5/projects/{projectUuid}/gamespec/author`。
**调用顺序：** Vue `gameGenerationApi.author` → `GameSpecController.author` → `SpecAuthorService.author` → 先调用编译器验证项目所有权 → `SpringAiSpecAuthorModel` 通过 Spring AI、结构化输出 Advisor、项目上下文和能力边界调用模型 → `GameSpecCompiler` 编译 → 失败诊断回灌模型，最多三轮。
**数据变化：** 每轮保存候选 spec、诊断、accepted 和 model evidence 于响应中；此 API 本身没有持久化 author 尝试。
**外部依赖：** OpenAI-compatible 模型服务。
**异常路径：** 模型缺失/结构错误映射为业务错误；三轮仍失败则返回 `FAILED` 与全部尝试。
**最终结果：** 合法 canonical spec 或带定位 path/code 的诊断。
**关键代码：** `SpecAuthorService:12-43`、`SpringAiSpecAuthorModel:33-132`、`GameSpecCompiler:35-274`。

### 链路 C：V5 Cocos 可玩包构建

**用户操作：** 提交通过编译的 spec，构建并下载内部试玩包，填写试玩结论，批准后显式发布和下载正式包。
**入口：** `POST /api/v5/projects/{projectUuid}/generation-runs` → `POST .../{runUuid}/build?expectedVersion=n` → `POST .../{runUuid}/approval` → `POST .../{runUuid}/release?expectedVersion=n`。
**调用顺序：** `GenerationRunService.create` 再次编译并冻结 canonical spec、Runtime IR、build request 和 digest → `build` 用旧 `stateVersion`、随机 claim token 和 12 分钟 lease 原子迁移到 `BUILDING` → 事务外由 `CocosBuildWorker` 复制固定 Runtime Shell、写入 IR、启动 Cocos CLI → `PlayableArtifactAssembler` 扫描路径/大小/秘密、加入 provenance/manifest 并确定性 ZIP → `PlayableArtifactStore.put` 以 `runUuid + packageDigest` 原子写本地文件 → 持有相同 claim 的 Worker 才能提交到 `AWAITING_APPROVAL` → `approve` 幂等记录人工决定 → `release` 只接受 `APPROVED`。
**数据变化：** `READY_TO_BUILD -> BUILDING -> AWAITING_APPROVAL -> APPROVED -> RELEASED`，拒绝时进入 `REJECTED`；每次迁移增加 `stateVersion`，保存 claim、package digest 和独立审批记录。
**外部依赖：** 本机 Cocos Creator 可执行文件和文件系统。
**异常路径：** Cocos 不可用时释放 claim 回 `READY_TO_BUILD`，普通构建/安全异常进入 `FAILED`，租约过期允许接管，旧 Worker 不能覆盖赢家；claim 成功后会重读并校验持久化 snapshot，状态、版本或 token 不匹配时在启动 Cocos 前返回并发更新错误。异常发生在 claim snapshot 重读阶段时不会误释放可能属于其他 Worker 的 claim，而由 lease 兜底恢复。
**最终结果：** 审批前只能下载 preview ZIP，只有 `RELEASED` 才能下载正式 ZIP；真实 assembler 组合测试已覆盖 build 到 `AWAITING_APPROVAL`，但当前仍不能据此宣称真实 Cocos CLI 端到端已成功跑通。
**关键代码：** `GenerationRunController.build/previewArtifact/approve/release/artifact`、`GenerationRunService.build/approve/release`、`GenerationRunMapper.claimBuild/completeBuild/transitionStatus`、`CocosBuildWorker.build`、`PlayableArtifactAssembler.assemble`、`PlayableArtifactStore.put/get`、迁移 `V38__converge_v5_generation_release_gate.sql`。

### 链路 D：Player 自动试玩与轨迹持久化

**用户操作：** 对某个不可变 PrototypeVersion 提交 Persona、策略、seed 和并发度。
**入口：** `POST /api/projects/{projectUuid}/player-runs`。
**调用顺序：** `PlayerRunServiceImpl` 校验版本和配置 digest、构造 `episode/1.0` 快照并写 `PENDING` → 事务提交事件触发 `PlayerRunWorker @Async` → DB claim → `PlayerApiClient` 携内部 token 调 Python `/player/episodes/batch` → Python 用 Semaphore 限并发，每个 episode 执行 reset/observe/policy/step 循环 → Java 保存原响应并通过 `MachineEpisodeService` 持久化 batch、episode、step → 完成 PlayerRun。
**数据变化：** 保存策略/persona digest、state hash、trajectory digest、每步 observation/decision/transition/reward 和 token evidence。
**外部依赖：** Python Agent 与 Simulation Service；LLM policy 还依赖模型服务。
**异常路径：** Python 对超时、非法 persona/policy、模拟依赖错误做显式分类；Java Worker 最多尝试三次并有 30 秒恢复扫描。
**最终结果：** 可查询批次、episode 摘要和分页步骤证据。
**关键代码：** `PlayerRunServiceImpl:56-106`、`PlayerRunWorker:32-66`、`python-agent/app/services/player/runner.py`。

### 链路 E：Director 工具调用、实验与人工审批

**用户操作：** 提交目标、事实和预算。
**入口：** `POST /api/projects/{projectUuid}/director-runs`。
**调用顺序：** `DirectorApplicationService` 幂等创建并转 `RUNNING` → after-commit `DirectorExecutionWorker @Async` → DB stateVersion + claim token 抢占 → Spring AI Director 或 Python 回退输出一项决策 → Java 校验 round、预算和 tool schema → `DefaultDirectorToolRegistry` 授权并限时执行 → 持久化决策、工具调用、event 和 checkpoint → 实验完成后唤醒；需要人工判断时进入 `WAITING_APPROVAL` → `PrototypeApprovalService` 写审批并唤醒 Director。
**数据变化：** 状态、检查点、usage、决策 digest、工具输入输出 digest、审批引用持续持久化。
**外部依赖：** LLM/Python、Player 实验、MySQL；工具正文结果当前在内存。
**异常路径：** claim 超时可恢复；预算耗尽终止；工具超时取消 Future；内存结果在重启后丢失。
**最终结果：** DRAFT 候选、审批请求、成功/失败结论和可审计证据。
**关键代码：** `DirectorApplicationService`、`DirectorExecutionWorker:47-105`、`DefaultDirectorToolRegistry`、`PrototypeApprovalService`。

### 最核心链路时序图

```mermaid
sequenceDiagram
    actor U as 用户
    participant V as Vue
    participant J as Java API
    participant DB as MySQL
    participant AI as Spring AI
    participant GC as GameSpecCompiler
    participant CW as Cocos Worker
    participant AS as Artifact Assembler/Store

    U->>V: 输入创意并生成 GameSpec
    V->>J: POST gamespec/author
    J->>J: 先验证项目所有权
    loop 最多 3 轮
        J->>AI: idea + 当前 spec + 编译诊断
        AI-->>J: 结构化候选 GameSpec
        J->>GC: 权威语义编译
        GC-->>J: canonical/IR/digests 或 diagnostics
    end
    J-->>V: 合法 spec + 编译证据
    V->>J: POST generation-runs + Idempotency-Key
    J->>GC: 再次编译并冻结输入
    J->>DB: READY_TO_BUILD
    V->>J: POST build?expectedVersion=n
    J->>DB: CAS claim -> BUILDING + token + lease
    J->>CW: 事务外执行固定 Cocos CLI
    CW-->>J: 输出目录 + log/output digest
    J->>AS: 组装确定性 ZIP
    Note over J,AS: claim 后重读并验证 BUILDING/version/token，再传 durable snapshot
    AS-->>J: package digest + ZIP
    J->>DB: token/version CAS -> AWAITING_APPROVAL
    U->>V: 下载 preview、试玩并填写结论
    V->>J: POST approval + Idempotency-Key
    J->>DB: 写独立审批记录并转 APPROVED
    V->>J: POST release?expectedVersion=n
    J->>DB: APPROVED -> RELEASED
    V->>J: GET artifact
    J->>AS: 按 runUuid+digest 读取并复验
    AS-->>U: 正式 ZIP
```

---

## 4. 真正值得讲的亮点

### 4.1 核心亮点

#### 亮点 1：用 Java 契约约束 AI，而不是相信 Prompt

- 解决问题：模型可能输出未知字段、越界参数、不支持组件或不可达胜利条件。
- 实现：`GameSpecCompiler` 使用封闭字段集合、能力注册表、数值范围、实体唯一性和胜利条件校验，输出稳定诊断码与 JSON path，再把诊断回灌 `SpecAuthorService` 最多修复三轮。
- 收益：同一 spec 规范化后有稳定 SHA-256，可绑定 Runtime IR 和 build request；模型不能绕过 Java 门禁。
- 局限：仅支持一个 archetype，校验器目前是手写单类，能力扩展成本会升高。
- 追问：为什么不用 JSON Schema？答：JSON Schema 适合结构校验，但实体数量、类型相关字段、世界边界和胜利条件属于跨字段语义，因此仍需 Java 语义编译阶段；未来可以“Schema 做结构 + Compiler 做语义”。

#### 亮点 2：Outbox + 幂等 + 状态版本的异步工作流

- 解决问题：写 DB 成功但发 MQ 失败造成任务丢失，以及消息重复造成重复执行。
- 实现：提交事务同时写 Run/Step/Event/Outbox；Publisher 使用数据库租约抢占和 Rabbit confirm；Consumer 使用 message/event id、Redis owner-token 锁和 DB `status_version` 条件更新；API 使用幂等键与请求指纹区分安全重放和冲突。
- 收益：首次投递意图不会因事务与 Broker 双写而丢失；重复消息大多被终态/attempt/stateVersion 拦截。
- 局限：业务步骤异常自动重试存在 P1 断层，不能称“完整 exactly-once”；系统实质是 at-least-once 投递 + 业务幂等趋近 exactly-once effect。
- 追问：Redis 锁和 DB claim 为什么都要？答：Redis 先低成本挡重复并发，DB 条件更新才是持久化最终所有权；即使 Redis 锁过期，DB 状态也应保护核心状态，但长步骤和恢复仍需设计好租约续期。

#### 亮点 3：确定性 Simulation、Replay 与机器证据

- 解决问题：AI 试玩结果难复现，无法判断策略变化还是环境随机性导致差异。
- 实现：固定 50ms tick、显式 PRNG 版本、seed、state hash、trajectory digest；Python Player 逐步 observe/decide/step，保存每一步输入、动作、转移和奖励。
- 收益：同输入与动作可机械复现，错误能定位到首次 hash 分叉；适合对比 persona/策略。
- 局限：只覆盖固定 `arcade_collect` 模拟核心，浏览器表现与 headless 逻辑仍需要持续契约测试。
- 追问：确定性为什么不能用 wall clock？答：系统状态只由离散 tick、seed 和动作推进；wall clock 仅作为观测耗时，不能参与策略结果或 state hash。

#### 亮点 4：受限 Director 的类型化工具和预算检查点

- 解决问题：Agent 自主调用工具可能越权、无限循环、重复副作用或无法恢复。
- 实现：Java 每轮向模型暴露 allowlisted tool definitions；关闭并行工具调用和内部自动执行；Java 再次校验闭合 schema、资源权限、版本和预算；每轮保存 checkpoint、decision/tool digest 和 stateVersion。
- 收益：模型只负责选择，Java 保留执行权和事实权威；失败后可从检查点恢复。
- 局限：工具结果 Store 和 Registry 幂等缓存是进程内，重启后正文丢失；部分源码压成单行，维护成本高。
- 追问：为什么不直接让 Spring AI 自动执行 ToolCallback？答：项目需要先持久化决策、做资源授权、预算控制、幂等和审计，因此将模型 tool call 解析与真实执行拆开。

#### 亮点 5：不可变版本、摘要绑定和人工门禁

- 解决问题：生成产物被修改后，测试证据与导出内容失去对应关系。
- 实现：V4 的 `prototype_version` 通过唯一约束和触发器阻止关键字段更新；V5 通过 `generation_run` 冻结三份编译产物，用 source/runtime/package digest 绑定全链路，构建成功停在 `AWAITING_APPROVAL`，审批独立写 `generation_run_approval`，只有 `APPROVED -> RELEASED` 后正式下载接口才放行。
- 收益：能够回答“这个测试结果究竟对应哪个版本”。
- 局限：V4 导出直接把 ZIP bytes 存 MySQL，V5 则存本地 digest-addressed 文件，存储策略不统一且都不是多节点对象存储方案；V5 目前由人工试玩，没有接入 V4 Player 证据。

### 4.2 次要亮点

- SSE 事件不是内存消息，而是 `workflow_run_event` 有序持久化后再订阅，支持 Last-Event-ID 式重放。
- Python 内部接口使用至少 32 位 shared token、常量时间比较、2 MiB body 上限和 traceId 白名单。
- Cocos Worker 拒绝符号链接和目录逃逸；Artifact Assembler 限制单文件/总包大小并扫描常见秘密。
- 模型 evidence 显式记录 provider/model/promptVersion/token；mock 与真实样本分离，避免评测污染。
- Compose、快速/集成/E2E、故障注入、性能基线脚本体现了比普通 Demo 更完整的交付意识。

### 4.3 包装风险较高的点

- “生产级 RAG”：错误。当前 fake embedding + 内存检索。
- “微服务架构”：不准确。更像模块化单体控制面 + Python/Simulation 辅助进程的单机 Compose。
- “高并发”：没有证据。可以说做过有界并发基线和重复消费保护，不能说支撑多少 QPS。
- “完整可靠消息”：需要先修复 Runner/Consumer 重试断层和 RETRY_WAIT 恢复空档。
- “Cocos 沙箱构建”：当前是独立临时工作区和固定 CLI 参数，不是容器/OS 级安全沙箱。
- “任意游戏生成”：错误。只支持 `arcade_collect` 垂直切片。
- “LangGraph 智能规划显著提升效果”：没有真实样本证明；Python graph 当前是确定性回退且 evidence 标 mock。

---

## 5. 必须掌握的 15 个实现

| 模块 | 你必须会讲的内容 | 边界/风险 |
|---|---|---|
| `AsyncWorkflowSubmissionServiceImpl.submit` | 所有权、幂等指纹、定义/Prompt 快照、限流、短事务 | fingerprint 依赖 JSON 序列化稳定性 |
| `AsyncWorkflowSubmitCommandService.create` | Run/Steps/Event/Outbox 同事务为何避免双写丢失 | 事务只保证 DB，不保证消费一次 |
| `OutboxPublisher` | 租约抢占、publisher confirm、return、超时回收 | 多实例 callback 与 claim owner 要谨慎 |
| `WorkflowMessageConsumer.consume` | 手动 ACK、Redis 锁、DB claim、重试/DLQ | 自动重试状态机有 P1 缺陷 |
| `SynchronousWorkflowRunner.run` | 根据冻结 definition 执行、依赖、hook、artifact | 串行；失败提前把 Run 置终态 |
| `WorkflowRecoveryService` | 扫描 stale PENDING/QUEUED/RUNNING、版本抢占、重建 Outbox | 不扫描 RETRY_WAIT |
| `RedisServiceImpl` / `WorkflowSubmissionGateImpl` | SET NX EX + owner-token Lua 解锁；Lua 限流原子性 | 无锁续租；固定窗口有边界突刺 |
| `AgentRunServiceImpl` | 模型调用、RAG 上下文、响应契约、metric/evidence | 文件较大、职责偏多 |
| `GameSpecCompiler` | 结构与跨字段语义校验、canonicalization、digest、IR | 只支持单 archetype |
| `SpecAuthorService` + `SpringAiSpecAuthorModel` | 结构化输出、Advisor、诊断反馈、三轮修复 | author 尝试未持久化 |
| `GenerationRunService` | 幂等、输入冻结、短事务 claim、租约接管、审批与发布门禁 | HTTP 仍同步等待 Cocos；并发幂等插入竞态未友好处理 |
| `CocosBuildWorker` / `PlayableArtifactAssembler` | 临时工作区、固定目标、超时、路径/秘密扫描、manifest | 本地进程不是强沙箱 |
| `PlayerRunServiceImpl` / `PlayerRunWorker` | 版本绑定、episode snapshot、after-commit async、恢复重跑 | Java 端重试立即执行，无指数退避 |
| Python `runner.py` | Semaphore 有界并发、逐步策略、超时、轨迹证据 | gather 默认等待全部；真实模型成本要限制 |
| `DirectorExecutionWorker` / Tool Registry | stateVersion claim、预算、单 tool call、schema、授权、checkpoint | 内存 store/idempotency，源码可读性差 |

你对前 8 项至少要能白板画调用链、解释失败窗口；对后 7 项要能解释输入输出、状态和为什么这样设计。不要背类名，要能回答“进程在这一行崩了会怎样”。

### 5.1 十五个实现的掌握卡片

下表把“职责、输入输出、过程、依赖、数据结构、设计原因、边界、缺陷、追问和掌握程度”压缩在一起；面试前至少逐行用自己的话复述一次。

| 实现 | 职责与为什么需要 | 输入、输出与关键数据 | 执行过程和依赖 | 边界/潜在 Bug | 典型追问与掌握目标 |
|---|---|---|---|---|---|
| `GameSpecCompiler.compile` | 把不可信模型 JSON 变成可信构建输入；Prompt 不能替代事实门禁 | 输入 `JsonNode`；输出 `GameSpecCompilationResult`，含 diagnostics、canonical spec、Runtime IR、Build Request 和两个 digest | 封闭字段 → 类型/范围 → entity/rule 跨字段约束 → 递归排序 → SHA-256 → IR/请求；依赖 capability registry/ObjectMapper | 单 archetype；手写规则扩展成本高；数组保序是业务假设 | 为什么结构化输出仍要编译？掌握每类诊断、canonicalization 和摘要绑定 |
| `SpecAuthorService.author` + `SpringAiSpecAuthorModel` | 有界地让模型依据确定性诊断修复规格 | idea/currentSpec/项目身份 → 最多 3 个 `SpecAuthorAttempt` 和最终编译结果 | 付费调用前先借 `GameSpecApplicationService.compile` 验所有权；Spring AI Advisors + structured output；每轮再次 Java 编译 | 尝试只在响应内、未持久化；模型超时/费率取决于外部服务 | 为什么是三轮？掌握软约束与硬校验分层、费用边界 |
| `GenerationRunService.create/build` | 冻结构建证据并控制唯一构建执行者 | spec/key/version → `GenerationRun`/`GenerationBuildOutcome`；核心字段为 stateVersion、claimToken、lease、三个 JSON、digests | create 再编译并写 DB；build 条件 claim；重读并校验 durable claim snapshot；事务外调用 Cocos；token/version CAS 完成 | stale Entity P1 已修复；仍有先查后插、HTTP 同步等待和时钟边界 | 进程在 claim/put/complete 各点崩溃怎样？掌握 claimed snapshot 校验，以及陈旧 Worker 防覆盖 |
| `GenerationRunService.approve/release` | 把内部可玩包与正式发布隔离，保留人工责任记录 | decision/reason/key/version → approval outcome 或 RELEASED run | 插入独立 approval；CAS 改状态；正式下载只认 RELEASED | 同一 run 只允许一次决定；并发唯一冲突未转为 replay；REJECTED 无重新生成入口 | 审批为何不直接改一个布尔值？掌握状态前置条件和审计字段 |
| `CocosBuildWorker.build` | 只把可信 IR 注入固定壳，避免模型控制命令行 | Build Request + Runtime IR → exit/log/output directory/digest | 校验固定版本/target/digest → 复制壳且排除生成目录 → 固定 ProcessBuilder 参数 → 10 分钟超时 → 校验输出 | 独立目录不是 OS 沙箱；依赖本机 Cocos；成功码默认含 36 是平台经验值 | 如何防命令注入/目录逃逸？掌握固定参数与强沙箱差异 |
| `PlayableArtifactAssembler/Store` | 生成可验证、可复现、可安全下载的 ZIP | BUILDING run + Cocos 目录 + log digest → manifest/zip/package digest | 拒绝 symlink/危险路径/超限/秘密；排序条目并固定时间戳；digest-addressed 原子落盘，读取时复验摘要 | 密钥正则只覆盖常见文本；一次性读全文件/ZIP 占内存；本地盘不支持多实例 | 确定性 ZIP 怎么做？掌握 payload/package digest 区别及 TOCTOU 边界 |
| `AsyncWorkflowSubmitCommandService.create` | 避免“任务已写库但消息未发”的双写丢失 | 冻结 Run/Steps/event payload → Run + Outbox | 一个 `@Transactional` 中插 Run、StepRun、RunEvent、Outbox | 只保证投递意图，不能保证只消费一次 | 为什么不用事务里直接 RabbitTemplate？掌握 transactional outbox 失败窗口 |
| `OutboxPublisher` | 安全地将 DB 投递意图交给 RabbitMQ | due Outbox rows → PUBLISHING/PUBLISHED/RETRY_PENDING | DB lease claim → publish → publisher confirm/return callback → CAS 更新；扫描过期 claim | confirm 是 broker 接收而非业务完成；多实例 callback/owner 配合复杂 | ACK 与 confirm 区别？掌握至少一次发布和重复消息来源 |
| `WorkflowMessageConsumer.consume` | 消费边界负责契约、去重、执行权、ACK/NACK/重试路由 | `WorkflowRunMessage` → Runner 副作用及 MQ disposition | 校验 schema/attempt/终态 → Redis owner lock → DB claim → Runner → 错误分类 → retry/DLQ | Redis 无续租；Runner 先写 FAILED 导致自动重试断层；retry intent 不走 Outbox | 系统是不是 exactly-once？必须能画出重复、崩溃和重投路径 |
| `SynchronousWorkflowRunner.run` | 按冻结步骤串行生成并验证 artifact | WorkflowRun + project + listener → StepRun/AgentRun/Artifact/Run 状态 | 解析 plan、检查依赖、恢复已成功步骤、执行、hook 评估、持久化事件 | catch 后直接 FAILED 与 Consumer 冲突；最终 updateById 可能与并发 cancel 发生丢更新，需要 CAS 化 | 谁应该拥有失败状态迁移？掌握 runner 与 transport 边界 |
| `WorkflowRecoveryService` | 发现失去心跳/投递的任务并重建投递意图 | stale PENDING/QUEUED/RUNNING → claim、audit、Outbox | 定时扫描 → 版本条件抢占 → 写恢复审计与 Outbox | 不扫描 RETRY_WAIT；扫描时间基于本机时间；批次固定 | 恢复为什么也走 Outbox？掌握恢复不是直接“再跑一次” |
| `RedisServiceImpl` + `WorkflowSubmissionGateImpl` | Redis 只做快速协调和限流，不做最终事实源 | key/token/窗口 → lock/rate decision | SET NX + TTL；Lua 比较 owner 再 DEL；Lua 原子计数/过期 | 无 watchdog；固定窗口边界突刺；Redis 故障采取 fail-closed | 为什么不能 GET 后 DEL？掌握原子性和可用性取舍 |
| Simulation Core / Replay | 给自动试玩提供确定性、可复验的状态机 | config/seed/action/tick → StepResult/stateHash/trajectoryDigest | 固定 50ms tick、显式 PRNG、纯状态推进、终局优先级、逐步 hash；前端 Node 测试验证重复性 | 只覆盖固定玩法；浏览器渲染正确不等于 Core 正确；浮点/版本升级要谨慎 | 同输入为何可复现？掌握 wall clock 与模拟时间分离 |
| `PlayerRunServiceImpl/PlayerRunWorker` | 把不可变版本转换成批量机器 episode 并异步持久化 | version/persona/policy/seeds → PlayerRun 和 episode batch | 校验 config digest → 写 PENDING → after-commit `@Async` → DB claim → 内部 token 调 Python → 持久化结果 → 事件唤醒 Director | 最多 3 次但无指数退避；共享线程池；先查后插竞态；依赖 Python/Simulation | 为什么冻结策略/persona digest？掌握证据可复现与重试副作用 |
| `MachineEpisodeServiceImpl` | 验证并保存批次、episode 和逐步证据，提供聚合/分页 | `PersistMachineEpisodeBatchRequest` → Batch/Episode/Step VO | 规范化 fingerprint、验证 termination/outcome、trajectory digest、序号与 observation digest，事务写三层表 | 一批大量 steps 时事务/逐行 insert 成本高；只验证摘要一致性，不能证明模型决策质量 | 如何发现轨迹被篡改？掌握 digest 能证明什么、不能证明什么 |
| `DirectorExecutionWorker` + `DefaultDirectorToolRegistry` | 让模型只能选择一个有权限、有预算的动作，Java 执行并留痕 | checkpoint/snapshot → decision/tool call/event/status | DB claim → 模型决定 → round/budget/schema/owner 校验 → 固定池超时执行 → 持久化摘要/checkpoint → 恢复扫描 | 结果正文和 registry cache 在内存；副作用与 call record 非原子；READ/WRITE 标注有错；大量单行源码 | 为什么关闭 parallel tool calls？掌握模型选择权与 Java 执行权分离 |

---

## 6. 数据模型与接口

### 6.1 核心关系

```text
SysUser 1 ── N GameProject
GameProject 1 ── N WorkflowRun 1 ── N WorkflowStepRun
WorkflowRun 1 ── N WorkflowRunEvent
WorkflowRun 1 ── N OutboxEvent
WorkflowStepRun N ── 1 AgentRun 1 ── N AgentArtifact
GameProject 1 ── N PrototypeVersion（不可变、自关联 parent）
PrototypeVersion 1 ── N PlaytestSession / MachineEpisode / PlayerRun
DirectorRun 1 ── N DirectorDecision / DirectorToolCall / DirectorRunEvent
DirectorRun 1 ── N ExperimentCandidate
PrototypeVersion 1 ── 0..1 PrototypeApproval
GenerationRun N ── 1 GameProject
GenerationRun 1 ── 0..1 GenerationRunApproval
```

关键约束：

- `workflow_run` 的异步幂等唯一键为 `(user_id, project_id, workflow_type, idempotency_key)`；请求 fingerprint 防止相同 key 携带不同 payload。
- `outbox_event.event_uuid` 唯一，发布索引为 `(status, next_attempt_at)`。
- `workflow_run_event` 同时约束 `(run, sequence)` 和 `(run, event_key)`，保证有序且业务事件幂等。
- `prototype_version` 的 `(project_id, version_number)`、操作幂等键和 artifact 均唯一；数据库触发器阻止更新/删除。
- `generation_run` 的 `(user_id, project_id, idempotency_key)` 唯一，`state_version + build_claim_token + build_claim_expires_at` 支持构建抢占与超时接管；`generation_run_approval` 同时唯一约束 run 和 `(actor_user_id, project_id, idempotency_key)`。

DTO/Entity/VO：Controller 用 DTO 接收入参并通过 Jakarta Validation；Service 将 DTO 转成 Entity 持久化；查询大多返回 VO。一个不一致点是 V5 `GenerationRunController` 直接返回 Entity，暴露了内部字段和持久化结构，建议改成 VO。

事务边界：提交 Run/Step/Outbox、Director 创建/迁移、审批写入属于短事务；LLM、RabbitMQ 和 Cocos 等外部调用原则上在事务外。V38 后 `GenerationRunService.build` 本身不持有事务，`claimBuild/completeBuild` 由 Mapper 的条件更新完成；但整个构建仍占用一个 HTTP 请求线程最长 10 分钟，建议后续改成提交任务后轮询或 SSE。

### 6.2 核心接口表

| 接口 | 用途 | 请求 | 返回 | 权限/校验 | 主要异常 |
|---|---|---|---|---|---|
| `POST /api/auth/register` | 注册 | username/password | UserVO | BCrypt、唯一用户名 | 重名、并发唯一冲突 |
| `POST /api/auth/login` | 登录 | username/password | JWT + UserVO | 状态、密码匹配 | 账号禁用、凭据错误 |
| `POST /api/projects` | 创建项目 | 项目 DTO | ProjectVO | JWT 用户 | 参数错误 |
| `POST /api/v1/projects/{p}/workflow-runs` | 异步提交 | workflowKey/idea/... + 幂等键 | 202 Run摘要 | 项目所有权、限流、backpressure | 幂等冲突、Redis 不可用 |
| `GET /api/v1/workflow-runs/{r}` | 查询 Run | runUuid | RunDetailVO | userId 联表隔离 | 不存在/越权 |
| `GET /api/v1/workflow-runs/{r}/events` | SSE | Last-Event-ID | 事件流 | 所有权、事件脱敏 | 订阅关闭 |
| `POST /api/v5/projects/{p}/gamespec/compile` | 编译 spec | JSON spec | canonical/IR/诊断 | 项目所有权 | 语义不合法 |
| `POST /api/v5/projects/{p}/gamespec/author` | AI 生成/修复 | idea/currentSpec | 尝试与编译结果 | 先验所有权、结构化输出 | 模型不可用/响应非法 |
| `POST /api/v5/projects/{p}/generation-runs` | 冻结构建输入 | spec + 幂等键 | GenerationRun | 所有权、再次编译 | 幂等冲突 |
| `POST /api/v5/projects/{p}/generation-runs/{r}/build` | Cocos 构建 | expectedVersion | BuildOutcome | 所有权、状态版本 | Cocos 未配置、并发更新 |
| `GET .../{r}/preview-artifact` | 下载内部试玩包 | runUuid | ZIP | 所有权、状态至少待审批 | 未就绪/摘要不匹配 |
| `POST .../{r}/approval` | 人工批准/拒绝 | decision/reason + 幂等键 | ApprovalOutcome | 所有权、待审批、唯一决定 | 幂等冲突/并发迁移 |
| `POST .../{r}/release` | 显式发布 | expectedVersion | GenerationRun | 必须 APPROVED | 未批准/并发更新 |
| `GET .../{r}/artifact` | 下载正式 V5 ZIP | runUuid | ZIP | 所有权、必须 RELEASED、digest | 未发布/文件丢失 |
| `POST /api/projects/{p}/player-runs` | 自动试玩 | version/persona/policy/seeds | PlayerRunVO | 版本/digest/协议校验 | 绑定错误、Python 失败 |
| `POST /api/projects/{p}/director-runs` | Director 执行 | goal/budget/facts | DirectorRun | 所有权、幂等、预算 | 工具/模型失败 |
| `POST .../prototype-versions/{v}/approval` | 人工审批 | decision/reason + key | ApprovalVO | 所有权、DRAFT、唯一审批 | 冲突 |
| `POST .../prototype-versions/{v}/exports` | 冻结导出 | 幂等键 | ExportJobVO | 所有权、证据完整、秘密扫描 | 输入不完整/安全拒绝 |

数据库性能观察：多数项目资源都带 project/user 条件与索引；Run 列表有界。未发现典型 ORM N+1，因为主要使用 MyBatis 显式查询；但 `SynchronousWorkflowRunner.findOrCreate` 每一步都会重新查询整组 StepRun，属于可优化的重复查询。`PrototypeExportServiceImpl.freeze` 连续读取多种 artifact，是明确的多次 round-trip，但数据规模小且导出低频，优先级低于正确性问题。

---

## 7. AI 辅助代码审查

### P0：严重安全或数据事故

【未发现】本次静态检查未发现已提交真实密钥、任意命令拼接、未授权跨项目下载或可直接目录穿越。不能因此宣称“没有漏洞”；没有做专业 SAST/DAST 或渗透测试。

### P1：投递简历前建议修复

**已修复的主链缺陷：** V5 原先在 `claimBuild` 更新数据库后仍把 `READY_TO_BUILD` 旧 Entity 交给只接受 `BUILDING` 的 `PlayableArtifactAssembler`。现在 `GenerationRunService.build` 会重读并核对 run/project、状态、版本与 claim token，后续 Worker、Assembler、Store 和 CAS 均使用该 durable snapshot；新增真实 assembler 组合测试和错误 token 反向测试。该项已不再列为当前 P1，但仍需真实 Cocos CLI E2E 验证。

1. **Runner 与 Consumer 的重试状态机断层。** `SynchronousWorkflowRunner:94` 捕获步骤异常后先把 Run 改成 `FAILED`；Consumer 随后在 `WorkflowMessageConsumer:166` 调用只允许 `status='RUNNING'` 的 `recordRetryableFailure`，更新 0 行后 NACK。消息再次投递看到终态后直接 ACK，配置的延迟重试没有生效。修法：Runner 不决定 Run 的最终失败，返回/抛出带分类的 Step failure 让 Consumer 原子迁移到 `RETRY_WAIT/FAILED`；或让 retry SQL 接受带版本的 FAILED 但必须避免与终态语义混淆。补一条真实 Runner exception → retry queue → second attempt success 的集成测试。

2. **RETRY_WAIT 存在崩溃丢唤醒窗口。** Consumer 先把 DB 改为 `RETRY_WAIT`，再直接向 retry exchange 发消息，不经过 Outbox；如果进程恰在两者之间崩溃，`WorkflowRecoveryService` 只扫描 PENDING/QUEUED/RUNNING，不会恢复 RETRY_WAIT。修法：将 retry intent 也写 Outbox，或恢复扫描包含到期 RETRY_WAIT 并重建事件。

3. **全局业务异常默认返回 HTTP 200。** `GlobalExceptionHandler.handleBusinessException/handleValidationException/handleException` 都直接返回 `ApiResponse`，没有 `ResponseEntity` 或 `@ResponseStatus`；除了 JWT Filter 主动写 401 外，未授权、参数错误、冲突和系统错误通常都以 HTTP 200 表达。前端可以读业务 code，但监控、缓存、网关重试和 REST 语义会失真。修法：建立 ErrorCode → 400/401/403/404/409/429/503/500 映射，并补 MockMvc 契约测试。

4. **多条幂等链路存在“先查后插”并发竞态。** `GenerationRunService.create/approve`、`DirectorRunServiceImpl.create`、`PlayerRunServiceImpl.submit`、`MachineEpisodeServiceImpl.persistBatch` 和 `PrototypeApprovalService.decide` 都先查询再 insert。数据库唯一键能阻止重复数据，但并发请求中的输家可能抛 `DuplicateKeyException` 并被全局处理成系统错误，而不是读取赢家结果。修法：捕获唯一键冲突后按同一作用域重查并比较 fingerprint，或使用原子 upsert/insert-ignore 后读取；增加双线程并发测试。

5. **Director 的可恢复性被内存存储削弱。** `DirectorToolConfiguration.directorToolResultStore` 使用 `InMemoryDirectorToolResultStore`，Registry 幂等缓存也为 `ConcurrentHashMap`。数据库虽保存调用摘要/digest/ref，但“工具副作用已发生、调用记录尚未 insert”时崩溃，重启可能再次执行副作用；ref 正文也会丢失。修法：把工具执行意图、结果与 fingerprint 持久化，以数据库唯一键做幂等事实源。

6. **旧 Workflow 的取消可能被 Runner 末尾覆盖。** 命令接口用条件 SQL 把活动 Run 改为 `CANCELED`，但 `SynchronousWorkflowRunner` 持有启动时读出的 Entity，步骤结束后通过 `updateById(run)` 写 `SUCCESS/FAILED`，没有携带旧 statusVersion 条件。取消与 Runner 完成并发时，后写者可能覆盖取消。修法：所有终态迁移统一走状态策略 + version CAS；每步边界重新检查 canceled，并加“执行中取消 vs 最后一步完成”的并发测试。

### P2：质量和维护性问题

1. Redis 锁只有固定 TTL，无 watchdog/续租；工作流默认 900 秒、Demo 300 秒，超时后可能出现第二执行者。DB claim 能降低风险，但 Step 级副作用仍需幂等。
2. `AuthServiceImpl.me` 用裸 `userId` 作为 Redis key，并缓存整个 `SysUser`（包含 passwordHash）。建议 key 使用 `auth:user:{id}:v1`，只缓存 UserVO，状态变更时主动失效。
3. Director、Experiment、Approval、Export 等大量类被压缩成一行，代码可读性差，异常处理也出现 `catch (Exception ignored)`；这是明显的 AI 生成/机械压缩痕迹，会影响面试现场讲解和后续维护。
4. `GenerationRunController` 直接返回 Entity；应建立专用 VO，避免把 claim token、内部 JSON 和表结构耦合到 API。尤其 `GenerationRun` 是否通过 Jackson 暴露 claim 字段需要显式控制。
5. V4、V5、legacy Demo 三套入口和两种产物存储并存，概念很多。README 已说明版本边界，但代码包仍容易让面试官怀疑过度设计。应明确主展示链只选一条。
6. Java `PlayerRunWorker` 的失败重试没有延迟，恢复扫描也可能快速重复调用外部 Python；建议记录 `next_attempt_at` 和错误分类。
7. `SynchronousWorkflowRunner.safe`、Director 若干位置静默忽略异常。Listener 失败可以降级，但至少要计数或 debug 日志；Director 状态迁移失败不能完全吞掉。
8. 用户注册先 count 再 insert，存在并发竞态；数据库唯一键是最终保证，但 DuplicateKey 应映射成业务错误。
9. `@Async` 的 Player、Director、知识索引共享名为 `taskExecutor` 的 2～4 线程池，长模型/网络任务可能互相饥饿；按任务类型拆池并配置拒绝策略和指标。
10. `COMPARE_CANDIDATE_METRICS` 在 `ExperimentDirectorTools` 注册为 `ToolPermission.READ`，但 `PlayerExperimentService.compare` 会写入 `experiment_comparison`；权限语义与真实副作用不一致，应标 WRITE 或拆成纯计算与持久化两步。
11. `FakeEmbeddingProvider` 只有 8 维字符哈希，`InMemoryVectorStore.search` 不计算相似度而把所有命中 score 固定为 1；RAG 只能称证据管线骨架，不能称语义检索。

### P3：可选优化

- 将手写 GameSpec 结构规则与 JSON Schema 组合，减少样板代码。
- 用 Resilience4j 或明确策略统一 Python/LLM 的 timeout、retry 和 circuit breaker。
- 给所有状态枚举增加数据库 CHECK 和 Java 状态转换测试。
- 将 V5 本地产物迁移到对象存储接口，统一 V4/V5 storage abstraction。
- 为 Controller 生成 OpenAPI 示例，并从契约自动生成前端类型。
- 将健康检查测试隔离外部依赖，减少本次 `mvn test` 中 DB health 的大段警告日志。

---

## 8. 面试拷打：48 问

### 项目背景与架构

1. **为什么做这个项目？** 参考答：想解决 LLM 游戏生成不可控、不可复现的问题，所以重点不是自由生成，而是 Java 契约、证据和审批。依据：GameSpecCompiler、版本/事件表。追问：为什么不用普通工作流？答：固定步骤适合稳定主链，Director 用于证据驱动的有限分支，两者并存但边界明确。
2. **它为什么不是普通 CRUD？** 答：存在长任务、跨进程调用、消息可靠性、状态机、确定性模拟、产物摘要与恢复。诚实边界：依然是单机实验台，不是生产 SaaS。
3. **为什么 Java 是控制面？** 答：类型、事务、数据库约束、工具授权和构建门禁需要确定性事实源；模型只做理解和选择。
4. **这是微服务吗？** 答：不把它包装成标准微服务；它是模块化 Java 控制面，加 Python Agent、模拟服务、前端与本地构建进程，通过 Compose 部署。
5. **V4 和 V5 有什么关系？** 答：V4 是 GameConfig/Phaser/Player/Director 实验链；V5 是当前收敛后的 GameSpec/Java Compiler/Cocos/人工审批/发布主链。V5 已有独立审批发布闭环，但尚未复用 V4 Player 自动试玩，因此不能说两代已经统一。

### API、权限与幂等

6. **JWT 流程？** 答：登录用 BCrypt 校验后签发 HMAC JWT；Filter 校验签名/过期并把 userId 放入 AuthenticationPrincipal；Service 再按 userId+project 做资源隔离。
7. **为什么鉴权后还要每个 Service 校验项目所有权？** 答：JWT 只证明用户身份，不证明对 path 中 project/run/version 的权限；对象级授权必须在数据查询处验证。
8. **幂等键和请求指纹分别解决什么？** 答：key 识别同一次业务操作；fingerprint 防止调用者错误地用同一个 key 提交不同参数。
9. **为什么 fingerprint 前要 canonicalize？** 答：避免 JSON 字段顺序不同导致语义相同却摘要不同；数组顺序若有业务意义则保留。
10. **唯一索引和应用层查询是否重复？** 答：应用层提供友好重放/冲突判断，唯一索引处理并发竞态，是最终保证。

### Redis

11. **Redis 在项目里做什么？** 答：用户缓存、Lua 固定窗口限流、工作流和旧 SSE 的 SET NX EX 锁；它不是主数据库。
12. **为什么 Lua 限流？** 答：INCR 与首次 EXPIRE 必须原子执行，否则并发或崩溃可能生成无过期 key。
13. **锁为什么要 owner token + Lua 解锁？** 答：防止旧持有者超时后误删新持有者的锁；只有 value 相同才 DEL。
14. **这个锁安全吗？** 答：对单 Redis 的短租约防重有效，但没有续租；长任务超过 TTL 会失效。因此 DB stateVersion/幂等副作用仍是最终保护，不能称严格分布式互斥。
15. **Redis 挂了怎么办？** 答：提交限流采取 fail-closed 返回 503，Consumer NACK 重投，避免绕过保护；代价是可用性下降。

### RabbitMQ 与可靠性

16. **为什么使用 Outbox？** 答：业务事务和 Broker 事务无法天然原子；先把投递意图与业务状态同事务写 DB，再异步发布。
17. **publisher confirm 和 consumer ACK 区别？** 答：confirm 说明 Broker 接收发布；ACK 说明 Consumer 决定消息已处理。confirm 绝不等于业务完成。
18. **系统是 exactly-once 吗？** 答：不是。Rabbit 是至少一次，项目通过幂等键、messageId、终态检查、Redis 锁和 DB claim 达到尽量一次的业务效果。
19. **重复消息如何处理？** 答：校验 schema/attempt，Run 不存在、终态或 attempt 不匹配直接 ACK；非终态再竞争锁和条件更新。
20. **当前重试有什么 Bug？** 答：Runner 先写 FAILED，Consumer 只能从 RUNNING 转 RETRY_WAIT，导致常见步骤异常无法进入延迟重试；这是我审查后需要优先修的状态机断层。
21. **延迟重试如何实现？** 答：三个 TTL queue 到期后 dead-letter 回主 exchange；30s、5m、30m。边界：重试 intent 当前没走 Outbox，存在 RETRY_WAIT 丢唤醒窗口。
22. **为什么手动 ACK？** 答：只有持久化终态或成功移交 retry/DLQ 后才能 ACK；异常移交失败则 NACK requeue。

### 并发与事务

23. **项目里有哪些并发？** 答：Rabbit Consumer、多实例 Publisher claim、Spring `@Async` Player/Director、Director 工具固定线程池、Python asyncio batch、SSE 订阅。
24. **什么是乐观锁？项目怎么用？** 答：更新时带旧 `state_version/status`；受影响行数为 1 才拥有迁移权，避免两个 Worker 同时提交状态。
25. **为什么外部调用不应放长事务里？** 答：占用连接、持锁、增加回滚和超时风险；应短事务 claim，事务外执行，再短事务完成。V38 后 V5 build 已采用这种结构，但 HTTP 请求线程仍同步等待，下一步是任务化接口。
26. **Python batch 如何限制并发？** 答：`asyncio.Semaphore(concurrency)` 包住每个 episode，再 `gather`；请求 DTO 把 concurrency 限为 1～8、episodes 最多 100。
27. **线程池满了会怎样？** 答：当前未显式配置拒绝策略，使用 Spring 默认；应说明并补充 rejected metric、合理 queue、按任务类型隔离，而不是声称已解决。

### AI、Agent 与 GameSpec

28. **Prompt 能防幻觉吗？** 答：不能。Prompt 降低概率，Java 封闭 schema、语义检查和能力注册表才是硬边界。
29. **结构化输出为什么还要再编译？** 答：结构正确不等于语义正确，例如实体越界、类型字段冲突、没有 collectible 或多个 exit。
30. **Director 与固定 Workflow 的区别？** 答：Workflow 按冻结定义执行确定步骤；Director 每轮基于证据选择一个白名单工具，但受到预算、状态机和人工审批限制。
31. **为什么关闭 parallel tool calls？** 答：简化顺序一致性、预算核算、幂等和审计；每轮只允许一个可持久化决策。
32. **RAG 做到了什么？** 答：文档上传、切块、来源记录、检索记录和 on/off 证据；embedding 与 vector store 仍是 fake/内存，所以没有证明语义质量。

### 测试、缺陷和规划

33. **测试很多是否代表质量高？** 答：说明契约意识较强，但大量是单元/模拟测试；真实 LLM、真实 Cocos、Broker 崩溃窗口和多实例仍需集成/故障测试。
34. **最自豪的测试是什么？** 答：确定性 Core 同输入 100 次得到相同终态 hash，以及 Outbox/锁/状态迁移单测；它们直接验证核心设计目标。
35. **最严重的技术债是什么？** 答：MQ retry 状态机、RETRY_WAIT 恢复、并发幂等冲突处理、HTTP 状态语义、Director 内存结果存储；其次是代码可读性和 V4/V5 概念过多。
36. **如果再给一周做什么？** 答：先修 MQ 与 HTTP/幂等正确性并加真实集成测试，再整理主链源码和演示脚本，最后更新简历；不会在一周内重做生产 RAG。

### Java、Spring、数据库、安全与部署补充 12 问

37. **`@Transactional` 为什么可能失效？**
    - 参考回答：它依赖 Spring 代理；同类内部直接调用、非 public 调用、对象不是容器 Bean，通常不会经过事务拦截。异常回滚还取决于异常类型和配置。
    - 回答依据：提交/审批服务把事务放在 Spring Service public 方法；V5 build 则有意拆成无长事务的外部执行和 Mapper CAS。
    - 继续追问：Mapper 单条 update 是否原子？是，单 SQL 原子，但跨多条语句仍需事务。
    - 诚实边界：本项目没有证明所有事务传播组合，只能解释现有边界。
38. **为什么 Entity 上的 `stateVersion` 不等于用了 MyBatis-Plus `@Version`？**
    - 参考回答：本项目主要在 Mapper 自定义 SQL 的 WHERE 中显式比较 version/status/token，并检查 affected rows；它是业务 CAS，不应泛称框架自动乐观锁。
    - 回答依据：`GenerationRunMapper.claimBuild/completeBuild/transitionStatus`、`DirectorRunMapper.claim`。
    - 继续追问：affected rows 为 0 怎么办？重新读状态并返回冲突/幂等结果，不能盲重试副作用。
    - 诚实边界：不同模块还没完全统一 CAS，旧 Runner 是反例。
39. **为什么 `ConcurrentHashMap` 不能解决分布式幂等？**
    - 参考回答：它只在当前 JVM 生效，重启丢失，多实例彼此不可见，而且不能与数据库副作用原子提交。
    - 回答依据：`DefaultDirectorToolRegistry.idempotency` 和 `InMemoryDirectorToolResultStore`。
    - 继续追问：应放 Redis 还是 MySQL？关键副作用优先数据库唯一键/事务记录，Redis只适合加速或短期协调。
    - 诚实边界：当前 Director 只适合单机受控演示。
40. **核心索引是怎么从查询反推的？**
    - 参考回答：按高频过滤/排序维度设计，例如 Outbox `(status,next_attempt_at)`、Run 项目与创建时间、事件 `(run,sequence)`；唯一索引同时承担幂等事实源。
    - 回答依据：Flyway V9、V15、V30、V35～V38 和对应 Mapper 查询。
    - 继续追问：联合索引最左前缀、选择性、写放大如何权衡？按真实 EXPLAIN/基数验证，不因字段多就全建索引。
    - 诚实边界：仓库未保存生产数据量和 EXPLAIN 报告，不能宣称查询优化百分比。
41. **项目有没有 N+1？**
    - 参考回答：未发现典型 ORM 懒加载 N+1，因为 MyBatis 以显式查询为主；但 Runner 每步重新读取同一组 StepRun，是重复 round-trip。
    - 回答依据：`SynchronousWorkflowRunner.findOrCreate`。
    - 继续追问：怎么改？运行开始加载 Map，状态变更后维护缓存；仍以数据库为恢复事实源。
    - 诚实边界：没有大数据压测，优先级低于状态正确性。
42. **为什么业务错误不能全部返回 HTTP 200？**
    - 参考回答：网关、浏览器、监控、缓存和调用方依赖状态码判断重试、鉴权和告警；body code 只能补充，不能替代 HTTP 语义。
    - 回答依据：`GlobalExceptionHandler` 当前返回裸 `ApiResponse`，JWT filter 是少数明确写 401 的路径。
    - 继续追问：冲突和限流用什么？通常 409 与 429；依赖不可用可 503，具体建立稳定映射表。
    - 诚实边界：修复会改变前端/API 契约，需要联调和回归测试。
43. **JWT 能防止越权吗？**
    - 参考回答：JWT 只证明身份；每个 project/run/version 仍要按当前 userId 查询或交叉验证 projectId，防 IDOR。
    - 回答依据：`JwtAuthenticationFilter`、`GameSpecApplicationService.compile`、`GenerationRunService.get/ownedProject`。
    - 继续追问：Token 泄漏怎么办？短有效期、HTTPS、密钥轮换、刷新/撤销策略；当前项目没有完整 refresh/revocation。
    - 诚实边界：这是单机 Demo 的基础认证，不是企业 IAM。
44. **Cocos Worker 算沙箱吗？**
    - 参考回答：不算。它有隔离工作目录、固定可执行文件/参数、路径和 symlink 检查、超时，但仍是宿主机进程。
    - 回答依据：`CocosBuildWorker` 类注释与 `ProcessBuilder` 参数。
    - 继续追问：生产怎么做？一次性容器或 VM、只读基础镜像、网络隔离、CPU/内存/pid 限额、只输出对象存储。
    - 诚实边界：当前适合可信 runtime shell 和受控开发机。
45. **为什么摘要不能代替签名？**
    - 参考回答：SHA-256 能检测内容变化，但任何人都能重算；数字签名/HMAC 才能证明来自持有密钥的发布者。
    - 回答依据：Compiler、ArtifactAssembler/Store 只计算和复验 digest。
    - 继续追问：何时需要签名？跨机器分发、供应链审计或不可信存储时。
    - 诚实边界：当前本地包只做完整性绑定，没有发布者身份认证。
46. **为什么测试数多仍不能证明端到端可靠？**
    - 参考回答：单测常用 mock，覆盖的是输入输出契约；崩溃窗口、多实例竞态、真实 Broker/模型/Cocos 需要集成、故障注入和 E2E。
    - 回答依据：本次 240/76/48 通过，但默认 Maven 不执行 `*IT`；仓库历史 evidence 也包含 E2E 失败记录。
    - 继续追问：最该补哪条？Runner 第一次失败 → retry outbox/queue → 第二次成功，以及 build 双线程 claim。
    - 诚实边界：不能用“自动化测试很多”替代生产验证。
47. **Docker Compose 解决了什么、没解决什么？**
    - 参考回答：统一单机依赖版本、网络、健康检查和启动顺序；不提供跨节点调度、自动扩缩容、滚动升级或真正 secret manager。
    - 回答依据：`docker-compose.yml`、`start-docker.ps1`、各 Dockerfile。
    - 继续追问：MySQL 镜像重建是否迁移数据？Flyway 由应用运行，named volume 保留数据；删卷才会重建数据目录。
    - 诚实边界：当前部署目标是本地演示，不是 Kubernetes 生产集群。
48. **如果必须删掉一半功能，保留什么？**
    - 参考回答：保留 V5 author → compiler → build claim → artifact → approval → release，加认证和最小项目管理；Player/Director/MQ/RAG 作为后续实验分支。
    - 回答依据：这是当前代码最完整、用户价值最直观、最容易稳定演示的一条链。
    - 继续追问：为何不保留 MQ？当前构建是同步主链，MQ 旧链还有 P1；先保证一个闭环比展示所有名词更重要。
    - 诚实边界：这是求职展示的收敛策略，不代表 MQ/Player 没有学习价值。

面试回答规则：不知道就说“这部分当前是单机实现，我能解释已有保护和失败窗口，生产化会这样改”；不要用“绝对不会重复”“完全安全”“生产级”之类措辞。

---

## 9. 简历项目描述

### 名称建议

**GameDev Agent Workbench｜受约束的 Agentic 游戏生成与自动试玩平台**

### 技术栈

Java 21、Spring Boot、Spring AI、Spring Security/JWT、MyBatis-Plus、MySQL、Flyway、Redis、RabbitMQ、Micrometer/Prometheus、Python/FastAPI、Vue 3、Cocos Creator、Docker Compose。

### 推荐写进简历的 5 条

1. 设计受约束的 GameSpec 生成链路，使用 Java 能力白名单、跨字段语义校验和稳定诊断码约束模型输出，并将编译诊断回灌 Spring AI 进行最多三轮自动修复，避免模型直接生成和执行任意代码。
2. 实现异步工作流的首次投递控制面，将运行、步骤、事件和 Outbox 在同一 MySQL 事务中持久化，结合 RabbitMQ publisher confirm、手动 ACK、Redis owner-token 锁和数据库状态版本降低任务丢失与重复消费风险；自动重试状态机仍在整改，不宣称 exactly-once。
3. 构建固定步长、seed、PRNG 版本和 state hash 驱动的确定性 Simulation/Replay，并通过 Python Player 按 Persona 与策略批量运行 episode，持久化逐步 observation、decision、transition 和轨迹摘要用于复现与对比。
4. 实现受预算约束的 Director Agent，将 Spring AI tool calling 与真实工具执行解耦，在 Java 侧完成闭合参数校验、资源授权、超时、幂等、检查点和人工审批，保留控制面事实权威。
5. **真实跑通 Cocos CLI 后再作为主亮点使用：** 打通 GameSpec 到 Cocos Web Mobile 的可发布产物链路，通过 `stateVersion + claim token + lease` 抢占构建执行权，claim 后校验数据库持久化快照，在事务外执行固定 Cocos Runtime，并以确定性 ZIP、摘要复验、人工审批和显式发布门禁隔离内部试玩包与正式包。

### 招聘平台介绍

> 独立开发的 Agentic 小游戏生成工程实验台。项目不是让模型自由生成代码，而是由 Java 负责 GameSpec 契约、状态机、工具权限、可靠消息、版本证据和人工审批；Vue/Cocos 提供真实构建、试玩审批和发布链，Python 负责 Player/Director 辅助实验。已具备较完整的单元测试、Compose 环境和故障/性能验证脚本；当前为单机垂直切片，仅支持 arcade_collect，V4 Player 与 V5 发布链尚未统一，RAG 仍是原型基线。

不要在简历写未经复测的 QPS、并发用户数、性能提升百分比或模型准确率。若要量化，先固定机器配置、数据集、并发、运行时长、p50/p95/p99、错误率和 mock/real 标识，再保存原始报告。

---

## 10. 7 天学习与整改计划

| 天 | 学习任务 | 代码任务 | 验收标准 |
|---|---|---|---|
| Day 1 | 手画 V5 claim/build/assemble/approval 状态链 | 修复 claim 后 stale Entity，加入真实 Assembler 组合测试并完成一次 Cocos 构建 | 成功进入 AWAITING_APPROVAL，审批发布后 ZIP 摘要可复验 |
| Day 2 | 掌握 ACK/NACK、confirm、DLX、TTL queue | 为 Runner 异常重试写失败测试并重构失败迁移所有权 | 首次失败进入 retry，第二次成功，事件无重复 |
| Day 3 | 学 Redis SET NX EX、Lua 解锁、固定窗口、锁续租 | namespace 用户缓存，只缓存 VO；记录锁 TTL 风险 | 能解释 Redis 挂掉与锁过期行为 |
| Day 4 | 学 `@Async`、线程池、乐观锁和租约 | 为 V38 build claim 增加真实双线程/超时接管测试，并设计异步 build API | 能证明两个并发 build 只有一个取得 claim，旧 Worker 不能提交 |
| Day 5 | 学 Spring AI tool calling、结构化输出、Advisor | 格式化 Director 核心类；持久化 tool result 设计至少落 ADR | 能白板解释模型选择与 Java 执行分离 |
| Day 6 | 学索引、唯一键、事务隔离、Flyway | 跑 Compose 快速/集成验证；整理一条稳定演示路径 | 从登录到产物/证据可重复演示 |
| Day 7 | 按本报告 36 问口述，录音复盘 | 更新 README/简历，删去夸大表述 | 30 秒、2 分钟介绍不卡壳；P1 状态明确 |

优先级：

1. 必须先验证：stale Entity 已修复且真实 Assembler 组合测试通过；接下来用本机真实 Cocos CLI 跑通 build → preview → approval → release，并保存演示证据。
2. 必须修：MQ retry 状态机和 RETRY_WAIT 丢唤醒窗口。
3. 强烈建议修：HTTP 状态码、并发幂等冲突、Director 内存结果存储或至少明确降级边界。
4. 面试前整理：格式化压缩源码、统一命名、给 V5 Entity 增加 VO。
5. 可以讲但不急着改：固定窗口换滑动窗口、对象存储、OpenAPI 类型生成。
6. 暂时不值得投入：生产级向量数据库、多集群高可用、多游戏平台适配；七天内投入大，且会稀释 Java 后端主线。

### 10.1 按收益排序的代码整改清单

| 优先级 | 当前问题 | 修改建议 | 成本 | 面试收益 | 投递前 |
|---|---|---|---|---|---|
| 1 | stale Entity 已修复并通过真实 Assembler 组合测试，但缺本机真实 Cocos CLI E2E 证据 | 跑通 build → preview → approval → release，记录命令、状态版本、摘要与产物截图 | 0.5 天，低 | 极高：证明主链可重复演示，也体现分层测试意识 | 必须 |
| 2 | Runner 先写 FAILED，Consumer 无法转 RETRY_WAIT | 统一失败迁移所有权，retry intent 写 Outbox，补真实二次成功集成测试 | 2～3 天，高 | 极高：能完整讲可靠消息 | 必须 |
| 3 | cancel 可能被 Runner 的 `updateById` 覆盖 | 所有终态使用 statusVersion CAS；步骤边界检查取消 | 1 天，中 | 高：典型并发状态机题 | 必须 |
| 4 | 业务异常多数 HTTP 200 | 建立 ErrorCode→HTTP status 映射，补 Controller 契约测试 | 0.5～1 天，中 | 高：API 基本功明显 | 必须 |
| 5 | 幂等 API 先查后插的并发输家返回系统错 | 捕获唯一键冲突后重查 fingerprint；写双线程测试 | 1～2 天，中 | 高：能讲 DB 是最终事实源 | 建议 |
| 6 | Director 工具结果/幂等为内存，副作用与记录非原子 | 先落 tool execution 表和唯一键；结果正文接文件/对象存储接口 | 2～4 天，高 | 高，但实现面较大 | 至少写清边界 |
| 7 | V5 build 占 HTTP 线程最长 10 分钟 | 改为 202 提交 + 后台 worker + GET/SSE 观察；复用现有 claim | 2 天，中高 | 中高：接口与任务模型更专业 | 可延后 |
| 8 | Generation Controller 返回 Entity | 增加 GenerationRunVO，隐藏 claim/内部 JSON，显式字段转换 | 0.5 天，低 | 中：接口边界清晰 | 建议 |
| 9 | Director 单行压缩代码和静默 catch | 格式化、拆方法，ignored 异常加 metric/log 并分类 | 1～2 天，中 | 中：便于现场讲代码 | 建议 |
| 10 | Player/Director/索引共享异步池 | 分池、命名线程、拒绝策略、队列与活跃数指标 | 1 天，中 | 中：并发基础 | 可延后 |
| 11 | RAG 无真实相似度 | 如果不是主线就明确标 experimental；不要急着接向量库 | 0.5 天文档，低 | 反而避免夸大 | 标注即可 |

学习优先级对应为：状态机与 CAS → Outbox/RabbitMQ 投递语义 → Spring 事务代理 → HTTP/REST 错误语义 → Redis 锁边界 → Spring AI structured output/tool calling → 确定性模拟与摘要 → Docker/Flyway。你最可能“会调用但讲不透”的是 RabbitMQ confirm/ACK/DLX、Spring `@Transactional/@Async` 代理边界、数据库唯一键与乐观锁、Spring AI Advisor/ToolCallback 的控制权边界；面试前应优先补这些，而不是继续加新功能。

---

## 11. 你最不熟悉的四部分速记

### Redis

你的项目不是把 Redis 当数据库，而是当“可丢失的协调/加速层”。缓存错了可以回 DB；限流 Redis 挂了选择拒绝提交；锁用 SET NX EX 获取，用 value 匹配的 Lua 解锁。面试必须主动说无续租的边界。

### MQ

背住四句话：业务与 Outbox 同事务；Publisher confirm 只证明 Broker 接收；Consumer 手动 ACK；系统不是 exactly-once，而是 at-least-once + 幂等。然后主动指出当前执行失败 retry Bug，反而能体现你真的读懂了代码。

### 并发

项目有三类并发保护：Redis 快速挡重复、数据库 `state_version/claim token` 决定最终所有权、业务幂等防副作用重复。Python Semaphore 控制批量 episode 并发；Java 线程池必须有界。V38 已给 Cocos 构建增加租约 claim，但其他“先查后插”的幂等入口仍有并发错误映射问题。

### API

按“身份认证 → 对象所有权 → 参数校验 → 幂等 → 状态前置条件 → 业务执行 → 稳定错误码”检查每个接口。不要认为 URL 中有 projectUuid 就安全，必须查询时绑定 userId/projectId。

---

## 12. 最终评价

这个项目的真实含金量高于普通实习 Demo，原因不是技术名词多，而是已经出现了契约、幂等、事件、状态版本、恢复、证据和安全边界等真实工程问题。它当前最大的风险也来自“功能面过宽”：V4、V5、Phaser、Cocos、Player、Director、RAG、MQ 全部存在，面试官很容易沿任何一点追到底。

你的最佳策略是把主线收缩为三件事：

1. Java 如何约束不可靠的 AI 输出；
2. 异步工作流如何避免丢任务和重复副作用；
3. 确定性 Player 证据如何支持复现、比较和审批。

其余技术作为追问展开。只要你能解释本文列出的 P1 缺陷和修复方案，就已经不是“只会 vibe coding”，而是在真正接管这个项目。
