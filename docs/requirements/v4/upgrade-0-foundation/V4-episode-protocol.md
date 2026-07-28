# V4 Episode Protocol

> 状态：RFC FROZEN
> 协议版本：`episode/1.0`
> 仿真协议：`simulation/1.0`
> 样本类型：`MACHINE`

## 1. 目的与边界

本协议冻结程序化玩家 Episode 的请求、逐步轨迹、结果、批量运行、重放和持久化边界。确定性 Player 与 LLM Player 必须使用相同的 `EpisodeRequest`、`EpisodeStep` 和 `EpisodeResult`；策略只负责从 Observation 选择 Action，不得改变 Simulation Core 规则或结果结构。

本协议只描述机器 Episode。现有真人 `PlaytestSession`、`PlaytestEvent`、其遥测事件和指标语义保持不变。机器轨迹不得伪装成真人试玩事件，也不得进入现有真人指标的分母。

首版不定义 Agent Prompt、不实现数据库、不改变 GameConfig 2.0，也不承诺重新调用外部 LLM 能产生相同文本。环境重放以记录的 Action 轨迹为事实。

## 2. 规范与公共约束

所有对象均为 UTF-8 JSON 闭合对象，字段区分大小写，未知字段按协议错误处理。时间单位为毫秒，token 和计数为非负整数，成本使用一百万分之一货币单位的整数 `costMicros`，digest 使用 SHA-256 小写 64 位十六进制。

| 标识 | 格式与范围 |
|---|---|
| Episode protocol | `episode/<major>.<minor>`，当前 `episode/1.0` |
| Simulation protocol | 当前固定 `simulation/1.0` |
| GameConfig schema | 当前固定 `game-config/2.0` |
| `episodeId` / `batchId` | 服务端生成的稳定 UUID |
| `clientEpisodeKey` | 批内唯一的调用方键，`1..80` 个安全字符 |
| seed | 无符号 32 位整数 `0..4294967295` |
| `maxSteps` | `1..1000000` |
| 批量数量 | `1..100` 个 EpisodeRequest |

## 3. 不可变引用

每个已接受 Episode 必须绑定以下不可变引用：

```text
PrototypeBinding
  projectUuid: string
  prototypeVersionUuid: string
  gameConfigArtifactUuid: string
  configDigest: sha256
  gameConfigSchemaVersion: "game-config/2.0"
  runtimeCapabilityVersion: string
```

提交端只提供引用和预期 digest；可信服务端必须按项目权限解析 `PrototypeVersion` 和 GameConfig Artifact，并确认：

1. PrototypeVersion 属于该 project；
2. `gameConfigArtifactUuid` 与 PrototypeVersion 当前不可变引用一致；
3. Artifact 通过 GameConfig 2.0 校验；
4. 规范内容的 digest 与请求及 PrototypeVersion 的 `configDigest` 完全一致；
5. runtime capability 支持 `arcade_collect`。

任一不一致以 item 级 `BINDING_MISMATCH` 拒绝，不启动策略或 Core。报告和聚合必须同时保存 `prototypeVersionUuid` 与 `configDigest`，不能只按可变项目名关联。

## 4. 策略、Persona 与模型引用

```text
PolicyRef
  kind: DETERMINISTIC | LLM
  policyId: string
  policyVersion: string
  policyDigest: sha256

PersonaRef
  personaId: string
  personaVersion: string
  personaDigest: sha256

ModelRef
  provider: string
  model: string
  modelVersion: string | null
  promptTemplateId: string
  promptVersion: string
```

`PolicyRef` 和 `PersonaRef` 对所有已接受 Episode 均必填。确定性基线也必须显式使用版本化 Persona，例如 `baseline-neutral/1.0`，不得用 null 隐藏行为条件。`policyDigest` 覆盖可执行策略及其规范参数；`personaDigest` 覆盖进入 Observation/决策的 Persona 定义。

`ModelRef` 的空值规则：

- DETERMINISTIC：必须为 null；
- LLM 且在模型选择前失败：可以为 null；
- LLM 一旦选择模型或发起调用：必须非 null，即使调用失败；
- `modelVersion` 仅当 provider 不提供稳定快照版本时可为 null，不能用展示别名伪装固定版本。

## 5. EpisodeRequest

```text
EpisodeRequest
  episodeProtocolVersion: "episode/1.0"
  clientEpisodeKey: string
  prototype: PrototypeBinding
  simulation:
    protocolVersion: "simulation/1.0"
    coreVersion: string
    seed: uint32
    maxSteps: integer
    observationPolicy: FULL | PERSONA policy object
  policy: PolicyRef
  persona: PersonaRef
  model: ModelRef | null
  metricVersion: string
  experiment:
    experimentId: string
    cohortKey: string
    candidateKey: string
  labels: object
```

`coreVersion` 是可部署构建的不可变版本或内容 digest，不能写 `latest`。`observationPolicy` 必须符合 Simulation Protocol；其内容和版本参与 request fingerprint。`metricVersion` 必填，用于冻结 reward 和聚合口径。

`experiment` 可为 null，表示独立回归；非 null 时三个字段均必填。`labels` 只允许最多 16 个低基数字符串键值，每个键和值不超过 64 字符，不得包含 Prompt、密钥、自由文本设计内容或个人信息；labels 不影响仿真重放。

seed 对已接受请求永不为 null。调用方如希望采用 GameConfig seed，必须在提交前解析并显式写入；服务端不得在运行中选择随机 seed。

## 6. EpisodeStep

`EpisodeStep` 是一条不可变决策和状态转换记录：

```text
EpisodeStep
  sequence: integer
  attempt: integer
  simulationStepBefore: integer
  simulationStepAfter: integer
  observation: Observation
  observationDigest: sha256
  decision:
    requestedAction: JSON value
    policyDurationMs: integer
    modelCallId: string | null
  transition:
    appliedAction: Action | null
    accepted: boolean
    advanced: boolean
    previousStateHash: sha256
    stateHash: sha256
    scoreDelta: integer
    events: TelemetryEvent[]
    status: RUNNING | TERMINATED
    terminationReason: null | WON | HEALTH_DEPLETED | TIME_EXPIRED | MAX_STEPS | ERROR
    error: object | null
  reward:
    version: string
    valueMicros: integer
```

规则：

- `sequence` 从 1 连续递增，记录每次策略请求，包括被 Simulation Protocol 拒绝的非法 Action。
- `observation` 是决策前投影，必须与 EpisodeRequest 的 Observation policy 一致；`observationDigest` 覆盖其规范 JSON。
- `transition` 是 Simulation `StepResult` 的无损字段映射，不得由 Runner 重算。
- 非法 Action 仍保存为 EpisodeStep；其 before/after step 与 hash 相同，reward 必须为 0。
- `reward.version` 由 `metricVersion` 解析。首版 `score-delta/1.0` 规定 `valueMicros = scoreDelta * 1000000`；Reward 只用于策略评测，不写回 Core 分数。
- `modelCallId` 仅引用受控审计记录。确定性策略必须为 null；LLM 若未发起模型调用也可为 null。
- `policyDurationMs` 衡量从 Observation 可用到 Action 产出的墙钟时间，成功和决策失败均必须记录；若进程在计时开始前失败，则该 Step 不创建，由 Episode error 记录。

原始 Observation、请求 Action 和 Transition 必须保留，不能只保存最终分数。为控制 API 大小，可以分页返回 steps，但 canonical Episode artifact 的逻辑内容必须是完整有序列表。

## 7. EpisodeResult

```text
EpisodeResult
  episodeProtocolVersion: "episode/1.0"
  episodeId: UUID
  batchId: UUID
  clientEpisodeKey: string
  sampleSource: "MACHINE"
  prototype: PrototypeBinding
  simulation: resolved simulation input
  policy: PolicyRef
  persona: PersonaRef
  model: ModelRef | null
  metricVersion: string
  executionStatus: COMPLETED | FAILED | REJECTED | CANCELLED
  terminationReason: null | WON | HEALTH_DEPLETED | TIME_EXPIRED | MAX_STEPS | ERROR
  outcome: null | WON | LOST | TRUNCATED | ERROR
  stepCount: integer
  acceptedActionCount: integer
  invalidActionCount: integer
  finalStateHash: sha256 | null
  finalScore: integer | null
  trajectoryDigest: sha256 | null
  steps: EpisodeStep[]
  usage: ModelUsage
  timing: EpisodeTiming
  error: EpisodeError | null
  audit: AuditMetadata
```

### 7.1 状态与结果映射

| executionStatus | 条件 | terminationReason / outcome |
|---|---|---|
| `COMPLETED` | Core 正常到达终态 | `WON/WON`；`HEALTH_DEPLETED` 或 `TIME_EXPIRED` / `LOST`；`MAX_STEPS/TRUNCATED` |
| `FAILED` | 策略、Runner、Core 或依赖在运行中失败 | Core 已给 ERROR 时 `ERROR/ERROR`，否则 terminationReason 可 null、outcome=`ERROR` |
| `REJECTED` | 绑定、协议或请求校验失败，未开始运行 | 二者均 null |
| `CANCELLED` | 明确取消且未得到 Core 终态 | terminationReason 为 null，outcome=`TRUNCATED` |

游戏失败（死亡或超时）仍是成功执行的 `COMPLETED`，不得记作基础设施 FAILED。`steps` 可以为空，仅当 REJECTED、启动前 FAILED/CANCELLED，或策略在首个 Action 前失败。`trajectoryDigest` 和 `finalStateHash` 仅在至少成功创建 Core 初态后非 null；REJECTED 时均为 null。

`trajectoryDigest` 覆盖按 sequence 排序的所有 EpisodeStep 规范 JSON。Result 持久化后不可修改；补充指标必须创建新 metricVersion 的派生记录。

### 7.2 ModelUsage 空值规则

```text
ModelUsage
  status: NOT_APPLICABLE | REPORTED | UNAVAILABLE
  inputTokens: integer | null
  outputTokens: integer | null
  totalTokens: integer | null
  costMicros: integer | null
  currency: string | null
  providerLatencyMs: integer | null
  unavailableReason: string | null
```

- DETERMINISTIC：`status=NOT_APPLICABLE`，其余字段全部为 null。
- LLM 且 provider 返回完整 usage：`status=REPORTED`，三个 token 字段、`costMicros`、三字母大写 currency 和 providerLatencyMs 全部非 null；0 是有效值。
- LLM 未调用 provider：`status=UNAVAILABLE`，token、成本、currency 和 providerLatencyMs 全部为 null，`unavailableReason=NOT_INVOKED`。
- LLM 已调用但 provider 未返回完整 usage：`status=UNAVAILABLE`，所有 token/成本字段必须为 null，不得把未知值写成 0；providerLatencyMs 若已测量可非 null；`unavailableReason` 必填稳定枚举。
- 多次模型调用先逐调用审计，再以相同币种整数求和；币种不同则总 `costMicros/currency` 必须为 null、status 为 UNAVAILABLE，并在审计中保留各调用成本。

### 7.3 EpisodeTiming 空值规则

```text
EpisodeTiming
  queuedMs: integer
  wallDurationMs: integer | null
  simulationDurationMs: integer | null
  policyDurationMs: integer | null
```

`queuedMs` 总是非 null。REJECTED 或从未开始的记录，其余三项均可为 null。开始运行后 `wallDurationMs` 必须非 null；至少创建 Core 初态后 `simulationDurationMs` 必须非 null；至少开始一次策略决策后 `policyDurationMs` 必须非 null，并等于所有已创建 EpisodeStep decision duration 加上产生 Episode 级决策失败的已测量时间。所有 duration 是观测墙钟，不参与确定性状态 hash。

### 7.4 EpisodeError

```text
EpisodeError
  phase: VALIDATION | POLICY | SIMULATION | RUNNER | DEPENDENCY | CANCEL
  code: stable string
  message: safe string
  retryable: boolean
  failedSequence: integer | null
```

错误不得包含 Prompt、Observation 全文、token、密钥、堆栈或外部服务响应正文。可重试只表示技术条件，不能覆盖同一 batch item 的既有成功结果。

## 8. EpisodeBatchResult 与提交语义

所有单 Episode 也通过数量为 1 的批量接口提交。批量请求由 envelope 和 `episodes` 组成：

```text
EpisodeBatchRequest
  episodeProtocolVersion: "episode/1.0"
  clientBatchKey: string
  episodes: EpisodeRequest[1..100]

EpisodeBatchResult
  episodeProtocolVersion: "episode/1.0"
  batchId: UUID
  clientBatchKey: string
  requestFingerprint: sha256
  status: ACCEPTED | RUNNING | SUCCEEDED | PARTIAL_SUCCESS | FAILED
  counts: { total, queued, running, completed, failed, rejected, cancelled }
  items: EpisodeBatchItem[]
  createdAt: timestamp
  completedAt: timestamp | null

EpisodeBatchItem
  clientEpisodeKey: string
  episodeId: UUID
  executionStatus: QUEUED | RUNNING | COMPLETED | FAILED | REJECTED | CANCELLED
  resultRef: string | null
  error: EpisodeError | null
```

### 8.1 幂等

- 提交必须携带 HTTP `Idempotency-Key`，安全字符长度 `8..128`。
- 幂等作用域为 `(authenticatedProjectOwner, projectUuid, Idempotency-Key)`，保留期不得短于批次及其 Episode 的最长审计保留期。
- `requestFingerprint` 是去除传输时间戳后，对完整规范 `EpisodeBatchRequest` 计算的 SHA-256；episodes 保持提交顺序。
- 同一作用域、同一 key、同一 fingerprint 必须返回同一 `batchId` 和当前持久化 BatchResult，不重复排队或调用模型。
- 同一 key、不同 fingerprint 必须整体返回 `IDEMPOTENCY_CONFLICT`；不得创建第二批任务。
- `clientEpisodeKey` 必须批内唯一；item 重试仍引用原 batch/item。技术重跑若被产品允许，必须创建显式 attempt/audit，而不能覆盖成功 Episode。

### 8.2 数量和错误层级

首版每批 `1..100` 项，规范 JSON 请求体最大 2 MiB，服务端并发上限独立配置且不改变结果语义。

以下是 batch 级失败，不创建任何 Episode：认证/授权失败、envelope 非法、0 或超过 100 项、重复 clientEpisodeKey、请求体超限、未知 major、幂等冲突。除此以外，prototype 绑定、policy/persona、seed 或单项字段错误均生成该 item 的 REJECTED EpisodeResult，其他合法项继续。

### 8.3 部分失败

- 每个 item 独立提交状态和结果，不使用全批次数据库回滚。
- 一个 Episode FAILED/REJECTED/CANCELLED 不取消其他 item，除非调用方另发批次取消命令。
- 已完成 EpisodeResult 和轨迹一经持久化不可因兄弟 item 失败而删除、覆盖或降级。
- `SUCCEEDED` 表示全部 item COMPLETED；`FAILED` 表示没有 item COMPLETED 且所有 item 已终态；`PARTIAL_SUCCESS` 表示至少一个 COMPLETED 且至少一个非 COMPLETED 终态。
- BatchResult 是索引，不内嵌所有轨迹；`resultRef` 指向不可变 EpisodeResult。读取批次必须能在部分运行中返回已完成项。

## 9. Replay

### 9.1 环境重放的最小不可变输入

要验证 Simulation Core，必须保存：

1. `episodeProtocolVersion`、`simulation.protocolVersion` 和精确 `coreVersion`；
2. `prototypeVersionUuid`、`gameConfigArtifactUuid`、完整规范 GameConfig 或其不可变可解析引用，以及 `configDigest`；
3. seed、maxSteps 和完整 Observation policy；
4. 按 sequence 保存的每个 `requestedAction`，包括非法动作和 RESTART；
5. 初始 state hash、每 step previous/final state hash、最终 state hash；
6. 期望 terminationReason、事件顺序和 trajectoryDigest。

环境 replay 直接喂入记录 Action，不调用原策略。逐 step 首个 hash 不一致即失败，报告 sequence、expected hash、actual hash、协议/Core 版本，不继续用最终分数掩盖分叉。

### 9.2 策略重放

确定性策略的再执行还需要 PolicyRef、PersonaRef、policy parameters、Observation policy 和 policy runtime version；相同输入必须得到相同 Action 序列。

LLM 策略重新调用 provider 不保证位级确定性。审计必须保存 ModelRef、Prompt template/version/digest、模型调用参数、响应 digest 和解析后的 Action；“recorded-decision replay”使用保存的 Action 验证环境。“live-policy rerun”是新的 Episode，必须使用新 episodeId 并与原 Episode 建立 `replayOfEpisodeId` 关联，不能覆盖原结果。

## 10. 机器 Episode 与真人 Playtest 隔离

| 维度 | 机器 Episode | 真人 Playtest |
|---|---|---|
| 样本标识 | `sampleSource=MACHINE` | 现有 PlaytestSession/Event，逻辑来源为 HUMAN |
| 原始数据 | EpisodeRequest/Step/Result 存储 | 现有 session/event 表与 API |
| 动作事实 | Observation → Action → Transition | 浏览器限制遥测事件 |
| 指标 | 版本化 machine metric | 现有真人 playtest aggregate |
| 主关联 | project + PrototypeVersion + config digest | project + PrototypeVersion |

实现时必须使用独立的机器 Episode 聚合/存储边界，不向现有 `playtest_session`、`playtest_event` 写入伪造记录，不复用真人 sessionUuid，也不改变其 sequence、outcome 或服务端复算语义。

机器与真人只通过 `projectUuid`、`prototypeVersionUuid`、时间窗口和显式 `experimentId/cohortKey` 关联。跨来源报告必须展示分开的 MACHINE/HUMAN cohort、样本数和指标；不得直接合并完成率、时长或死亡率分母。若展示联合结论，必须保存使用的两组指标版本及 episode/session manifest。

## 11. 保存边界与可追溯性

### 11.1 原始轨迹（不可变）

必须保存规范 EpisodeRequest、解析后的绑定、初始 hash、完整 EpisodeStep 序列、EpisodeResult、trajectoryDigest、协议/Core 版本。大轨迹可以压缩到对象存储，但数据库必须保存内容 digest、大小、存储版本和不可变引用；对象缺失时报告必须标记 evidence unavailable，不能只凭聚合值宣称成功。

### 11.2 聚合指标（可再生、版本化）

每个 machine metric snapshot 必须包含：

- `metricVersion` 和生成时间；
- project、PrototypeVersion、configDigest；
- sampleSource=`MACHINE`；
- 输入 Episode ID 有序 manifest 或 manifest digest 加可解析引用；
-过滤条件、cohort、seed 集合、总样本数和各终止原因计数；
- 完成率、耗时、非法动作率、路径效率、token、成本等派生值及空值数量。

聚合不得覆盖原始 Episode。算法变化创建新 metricVersion；任何报告单元格必须能从 metric snapshot 展开到 Episode IDs，再定位具体 PrototypeVersion 和原始轨迹。

### 11.3 审计元数据

必须保存 batch/episode/client keys、幂等 fingerprint、认证 actor、project、correlation/trace ID、创建/排队/开始/结束时间、执行节点和构建版本、Policy/Persona/Model refs、每次模型调用 usage 或不可用原因、错误 phase/code、取消 actor/reason、重试/重放关联。

审计保存 digest 和安全元数据；Prompt/response 正文若因评测确需保存，必须进入访问受控且有独立保留策略的证据存储，不能混入普通日志或错误 message。密钥永不保存。

## 12. 版本兼容与验收不变量

- Episode 版本格式为 `episode/<major>.<minor>`；未知 major 必须拒绝。minor 只允许增加经协商的可选视图字段，不得改变已有 request fingerprint、step、usage 空值或结果语义。
- Simulation Protocol、Core、Policy、Persona、Prompt、Metric 各自独立版本化；不得用一个“app version”替代。
- 确定性和 LLM 策略对同一终止原因、非法动作和部分失败使用相同枚举及结果结构。
- 一个 item 的失败不得使任何已完成 sibling EpisodeResult 或轨迹丢失。
- MACHINE 数据不能写入或聚合进真人 Playtest 指标。
- 任一报告指标必须能追溯到 metricVersion → Episode manifest → EpisodeResult → PrototypeVersion/configDigest → 原始轨迹。

规范示例：

- [`examples/episode/deterministic-episode-result.json`](examples/episode/deterministic-episode-result.json)：确定性策略正常获胜；
- [`examples/episode/llm-episode-failure.json`](examples/episode/llm-episode-failure.json)：LLM 在 Core 启动前失败及 usage 空值；
- [`examples/episode/partial-batch-result.json`](examples/episode/partial-batch-result.json)：批量运行保留成功项并报告部分失败。
