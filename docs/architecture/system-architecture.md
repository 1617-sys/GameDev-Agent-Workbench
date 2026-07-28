# 系统架构与核心链路

本文描述当前代码事实和下一阶段边界。历史 R0–R7/V3 报告记录当时环境与验收结果，不自动代表生产能力。

## 产品边界

当前系统只支持 `arcade_collect`：模型生成受白名单约束的 GameConfig，固定 Phaser Runtime 负责执行。四个“Agent”是冻结定义驱动的串行 LLM Workflow，不具备自主工具调用、规划、反思或 Agent 间协商。

下一阶段继续深化单玩法平衡实验，不优先扩展多个 Runtime；RAG 保留，但从确定性检索桩升级为有质量评测的持久化语义检索。

## 当前组件

```mermaid
flowchart TB
    subgraph Client["Browser"]
        Vue["Vue 3 · Auth / Projects / Studio / Run / Versions"]
        Phaser["Phaser · arcade_collect Runtime"]
    end

    subgraph Java["Spring Boot · Java 21"]
        API["JWT API · project ownership"]
        Submit["Async submission · idempotency"]
        Outbox["Outbox publisher"]
        Consumer["RabbitMQ consumer"]
        Runner["Frozen four-step Workflow"]
        Contract["GameConfig contract / evaluation"]
        Version["Prototype version / telemetry / export"]
        Retrieval["Knowledge / retrieval provenance"]
        Query["Read model / SSE / commands"]
        Recovery["Heartbeat / recovery audit"]
    end

    MySQL[("MySQL · durable facts")]
    Redis[("Redis · rate / execution locks")]
    MQ[("RabbitMQ · workflow messages")]
    Python["FastAPI · Prompt / LLM / RAG context"]
    Provider["Mock or OpenAI-compatible provider"]

    Vue -->|"JWT HTTP / SSE"| API
    API --> Submit
    API --> Query
    Submit --> Redis
    Submit --> MySQL
    Outbox --> MQ
    MQ --> Consumer
    Consumer --> Runner
    Runner --> Retrieval
    Runner --> Python
    Python --> Provider
    Runner --> Contract
    Runner --> MySQL
    Contract --> Version
    Version --> MySQL
    Query --> MySQL
    Query --> Vue
    Vue --> Phaser
    Phaser -->|"version-bound telemetry"| API
    Recovery --> MySQL
```

| 边界 | 当前责任 | 主要代码入口 |
| --- | --- | --- |
| Vue/Phaser | 登录、项目、Brief、Run/SSE、可玩预览、版本、遥测与导出 | [`frontend-vue/src/features`](../../frontend-vue/src/features) |
| Java API | 鉴权、归属、请求契约、read model 和下载 | [`controller`](../../backend-java/src/main/java/com/example/gameworkbench/controller) |
| 异步执行 | 幂等提交、Outbox、Consumer claim、重试和恢复 | [`AsyncWorkflowSubmissionServiceImpl.java`](../../backend-java/src/main/java/com/example/gameworkbench/service/impl/AsyncWorkflowSubmissionServiceImpl.java)、[`WorkflowMessageConsumer.java`](../../backend-java/src/main/java/com/example/gameworkbench/messaging/WorkflowMessageConsumer.java) |
| Workflow | 读取冻结定义，串行执行四个预定义 LLM Step | [`application/workflow`](../../backend-java/src/main/java/com/example/gameworkbench/application/workflow) |
| Python | Prompt、OpenAI-compatible 调用、GameConfig 解析和 RAG 上下文 | [`python-agent/app`](../../python-agent/app) |
| 原型实验 | GameConfig、不可变版本、试玩复算、建议和导出 | [`PrototypeVersionServiceImpl.java`](../../backend-java/src/main/java/com/example/gameworkbench/service/impl/PrototypeVersionServiceImpl.java)、[`PlaytestTelemetryServiceImpl.java`](../../backend-java/src/main/java/com/example/gameworkbench/service/impl/PlaytestTelemetryServiceImpl.java) |
| RAG 基线 | 文档/Chunk、项目隔离、候选协议和 RetrievalRecord | [`service`](../../backend-java/src/main/java/com/example/gameworkbench/service) |

## 生成与可靠执行时序

```mermaid
sequenceDiagram
    actor U as User
    participant V as Vue
    participant A as Java API
    participant D as MySQL
    participant O as Outbox
    participant Q as RabbitMQ
    participant C as Consumer
    participant W as Workflow Runner
    participant P as Python LLM

    U->>V: submit Prototype Brief
    V->>A: POST workflow-runs + Idempotency-Key
    A->>D: transaction: WorkflowRun + OutboxEvent
    A-->>V: 202 + workflowRunUuid
    O->>Q: publish with confirm
    Q->>C: at-least-once delivery
    C->>D: durable execution claim
    C->>W: execute frozen definition
    loop concept / loop / tasks / config
        W->>P: prompt + previous outputs + optional RAG snapshot
        P-->>W: output + execution metadata
        W->>D: StepRun / AgentRun / Artifact / Evaluation
    end
    W->>D: SUCCESS + immutable PrototypeVersion
    C-->>Q: ACK after durable terminal outcome
```

关键语义：

- 投递是 at-least-once，业务幂等依赖数据库 claim、attempt、状态和唯一约束；
- Redis 锁只减少昂贵重复执行，不能代替 durable correctness；
- Workflow/Prompt/输入快照在运行开始后不可被 ACTIVE 配置改写；
- Python 返回模型结果，Java 决定业务成功和可玩资格；
- 当前执行图是固定串行链，不是模型动态编排。

## 原型与平衡实验时序

```mermaid
sequenceDiagram
    actor O as Project Owner
    actor T as Tester
    participant UI as Vue / Phaser
    participant API as Java API
    participant DB as MySQL
    participant LLM as Balance Agent

    O->>UI: open immutable PrototypeVersion
    UI->>API: create PlaytestSession
    API->>DB: bind session to version/config digest
    O->>UI: play
    UI->>API: batch restricted events
    API->>DB: validate sequence and recompute metrics
    O->>API: request balance suggestion
    API->>LLM: frozen config + aggregate snapshot
    LLM-->>API: recommendation
    API->>DB: persist suggestion
    O->>API: tune allowed parameters
    API->>DB: create immutable child version
    O->>API: compare versions / export package
```

当前缺口：公开 `/demo/play` 尚未把外部测试者安全绑定到项目和版本遥测。下一阶段需要可撤销、限时、限权的分享 token，并区分外部玩家、所有者和机器人样本。

## RAG 当前事实

```mermaid
flowchart LR
    Upload["Knowledge upload"] --> Chunk["Character-window chunks"]
    Chunk --> Fake["fake-hash-v1 · 8 dimensions"]
    Fake --> Memory["InMemoryVectorStore"]
    Query["Query"] --> Memory
    Memory --> Candidates["project-filtered candidates · score=1"]
    Candidates --> Context["bounded untrusted context"]
    Context --> LLM["LLM Workflow Step"]
    LLM --> Used["used_references"]
    Used --> Record["RetrievalRecord provenance"]
```

已经成立的能力：

- 文档和 Chunk 绑定项目与版本；
- RAG-on/off/empty/unavailable/mock 显式区分；
- 运行后读取持久化 RetrievalRecord，而不是展示时重新检索；
- 历史引用不会因为文档后来失效而被改写。

尚未成立的能力：

- 真实 embedding 和 cosine similarity；
- 稳定的语义 topK 与质量指标；
- 重启后可恢复的持久化向量索引；
- 受限 PDF 文本提取；
- 按实际注入文本/token 计算 context budget；
- RAG-on/off 对生成质量的可信增益结论。

目标架构和验收指标见[B 路线与 RAG 升级路线图](../roadmap-balance-lab-rag.md)。

## 核心持久化关系

```mermaid
erDiagram
    SYS_USER ||--o{ GAME_PROJECT : owns
    GAME_PROJECT ||--o{ WORKFLOW_RUN : contains
    WORKFLOW_RUN ||--o{ WORKFLOW_STEP_RUN : executes
    WORKFLOW_RUN ||--o{ OUTBOX_EVENT : publishes
    WORKFLOW_RUN ||--o{ WORKFLOW_RUN_EVENT : records
    WORKFLOW_STEP_RUN ||--o| AGENT_RUN : invokes
    AGENT_RUN ||--o{ MODEL_CALL_METRIC : measures
    WORKFLOW_STEP_RUN ||--o{ AGENT_ARTIFACT : produces
    AGENT_ARTIFACT ||--o{ EVALUATION_REPORT : evaluates
    GAME_PROJECT ||--o{ KNOWLEDGE_DOCUMENT : owns
    KNOWLEDGE_DOCUMENT ||--o{ KNOWLEDGE_CHUNK : splits
    AGENT_RUN ||--o{ RETRIEVAL_RECORD : cites
    GAME_PROJECT ||--o{ PROTOTYPE_VERSION : versions
    PROTOTYPE_VERSION ||--o{ PLAYTEST_SESSION : tested_by
    PLAYTEST_SESSION ||--o{ PLAYTEST_EVENT : contains
    PROTOTYPE_VERSION ||--o{ PROTOTYPE_EXPORT_JOB : exports
```

## 下一阶段架构约束

- 只选择一个正式向量后端，避免维护多套半成品实现；
- 索引 job 必须有持久状态、attempt、错误分类、重建和失效语义；
- 公开试玩 token 只能读取一个冻结版本并提交受限遥测；
- 建议只创建 DRAFT 候选，不得自动覆盖已测试版本；
- RAG 质量、生成质量和系统性能分别评测，不能用一个通过率互相替代；
- 新旧同步/异步 API 应明确 canonical path，并逐步弃用重复入口；
- 在单玩法实验闭环完成前，不增加第二 Runtime 或新的基础设施组件。
