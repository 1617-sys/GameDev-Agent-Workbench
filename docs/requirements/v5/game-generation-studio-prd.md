# Agentic Mini-Game Factory 产品需求文档

> 文档状态：ACTIVE IMPLEMENTATION / 范围冻结
> 目标版本：V5
> 最近审计：2026-08-09（基于当前源码、测试与本机 Cocos 构建证据）
> 唯一活跃引擎：Cocos Creator 3.8 LTS
> 首个玩法切片：`arcade_collect`
> 首个构建目标：可下载、可在本地启动的 Cocos Web Mobile 游戏包
> 当前不做：任何小游戏平台适配、开发包、上传、审核和平台 SDK；统一延后到 V6
> 当前工程重点：不新增产品功能；完成本 PRD 尚未闭环的任务，并将现有 AI 调用迁移到 Spring AI

## 1. 产品结论

GameDev Agent Workbench 的 V5 主定位调整为：

> **Agentic Mini-Game Factory：Agent 将受限创意转成 GameSpec，Java 控制生成、校验、实验和发布门禁，Cocos Creator 将通过验证的规格构建为高完成度、可在本地独立运行的小游戏包。**

平台不是“提示词生成一份配置”，也不是“LLM 随机编写并执行游戏代码”。最终必须交付真正可操作、有开始和终局、有视觉反馈、可以下载和复验的游戏包。

```text
Brief
→ Java 创建 GenerationRun、冻结预算和权限
→ Spring AI 驱动的 Java Director 规划下一步
→ Java 在状态机和预算约束下执行一个受控工具
→ 生成 GameSpec
→ Java 语义校验、能力检查与诊断
→ Cocos Runtime Target 装载规格、资源包和表现预设
→ Local Cocos Web Mobile Package
→ Headless / Player Agent / Visual Quality Gates
→ Agent 根据诊断有限修复
→ 人工试玩与批准
```

## 2. 为什么选择 Cocos Creator

V4 的 Phaser Runtime 保留为历史证据，但 V5 不再继续扩展。Cocos Creator 3.8 LTS 成为唯一活跃引擎，因为它同时提供：

- TypeScript 游戏脚本、2D/3D 渲染、动画、粒子、材质、UI、物理与资源系统；
- Web Mobile 构建能力，以及可留到 V6 使用的多平台发布扩展；
- 场景、Prefab、Asset Bundle、构建模板和命令行构建；
- 更适合人工制作高质量 Runtime Shell 和表现预设的编辑器工作流。

选择 Cocos 不意味着视觉质量自动提高。V5 必须把资产、动画、镜头、粒子、音效和 UI 主题纳入正式契约和验收，不能继续使用简单占位图后宣称“引擎效果差”。

## 3. 用户与价值

### 3.1 目标用户

- 想快速验证轻量玩法的个人开发者和策划；
- 需要批量生成同类小游戏原型并比较效果的人；
- 需要查看 Agent 生成、修复和验证证据的技术评审者。

### 3.2 一次任务的交付物

用户提交 Brief 后应得到：

1. 一份版本化、可读、可编译的 GameSpec；
2. 一份可下载、解压并通过仓库启动器在本地运行的 Cocos Web Mobile 游戏包；
3. 一份包含编译、资源、构建、smoke、试玩和表现门禁的证据；
4. 一条 Director 如何选工具、遇到什么诊断、如何修复的可审计轨迹；
5. 一个绑定不可变 artifact digest 的人工批准结果。

微信、抖音、支付宝及其他小游戏平台全部属于 V6。V5 只冻结未来 adapter 所需的干净边界，不实现任何平台专用配置、构建或验证。

## 4. 求职展示目标

项目必须真实展示：

- Java：领域状态机、GameSpec AST、语义校验、能力注册表、类型化 Agent Tool Gateway、事务、幂等、恢复、产物血缘与发布门禁；
- Agent：动态计划、类型化工具调用、读取编译/构建/试玩反馈、有限修复、预算终止与完整轨迹；
- 游戏工程：Cocos Runtime Shell、组件/Prefab/Asset Bundle、表现预设与本地可运行构建包；
- 评测：确定性基线与 LLM Player 对照、失败样本、mock/real 分层和可复现实验。

Java 不需要实现渲染和逐帧引擎，但必须拥有不可绕过的生成控制权；Spring AI 负责模型接入、结构化输出和工具调用适配，不拥有领域状态；迁移期 Python 不得直接修改数据库、Cocos 工程或产物状态；Cocos 不负责业务审批。

## 5. 核心领域模型

### 5.1 GenerationRun

一次生成是可恢复的长事务，不是同步 HTTP 请求：

```text
PLANNING
→ SPEC_DRAFTING
→ VALIDATING
→ BUILDING
→ PLAYTESTING
→ REPAIRING
→ AWAITING_APPROVAL
→ APPROVED / REJECTED / FAILED / CANCELLED
```

Java负责状态转换、幂等、超时、恢复、取消、重试预算和并发隔离。Director 只能提出下一步决策，不能自行修改状态。

### 5.2 GameSpec

GameSpec 是作者层事实，至少描述：

- archetype、世界、实体、组件、规则和胜负条件；
- 关卡 recipe 与参数；
- visual theme、asset pack、animation profile；
- camera、feedback、UI skin 和 audio profile；
- target capability 要求，但不包含平台密钥和发布账户。

自然语言字段不进入 Runtime 语义；未知组件、非法引用、越界参数和不支持能力必须由 Java 返回稳定诊断。

### 5.3 RuntimeCapability

Cocos Runtime Shell 通过版本化能力注册表声明可执行的组件、事件、动作和表现预设。Agent 只能使用已注册能力，不得编造 Cocos API、组件脚本或资源路径。

### 5.4 PlayableArtifact

Artifact 绑定 GameSpec、Java compiler、capability registry、Cocos Creator、Runtime Shell、asset pack、local build profile 和 gate suite 的具体版本及 digest。产物改变后，原审批立即失效。

## 6. Cocos 生成策略

### 6.1 固定 Runtime Shell，生成数据而非源码

首版不允许 Agent 直接生成或编辑：

- Cocos `.scene`、`.prefab`、`.meta`；
- Runtime TypeScript 源码；
- shader、插件和构建模板；
- 任意 JavaScript 或平台 API 调用。

这些内容由人工开发、测试并版本化。Agent 只生成 GameSpec，Java将其编译为 Runtime 可读取的类型化数据、资源 manifest 和 target build request。

### 6.2 构建成本控制

Cocos 命令行构建仍依赖安装好的编辑器和 GUI 环境，因此不得在每一次 Agent 思考或参数修复后执行完整构建。

```text
多数修复轮次：Java validate + deterministic simulation
候选稳定后：Cocos Web Mobile build + browser smoke + local package gate
```

构建 Worker 使用固定 Cocos 版本、固定 Runtime Shell 和隔离工作目录，保存退出码、日志和输出 digest。

### 6.3 视觉质量来自预设组合

大量产出依赖人工制作的高质量模块，而不是批量生成任意代码：

- archetype recipe；
- 角色、敌人和场景 Asset Pack；
- Animation Profile；
- Camera/Shake/Zoom Profile；
- Particle/Hit/Collect/Win Feedback Profile；
- UI Skin 与字体规范；
- Audio Profile。

Agent 负责选择、组合和调参；Java 校验组合是否合法；Cocos 执行表现。

## 7. 首个垂直切片

首版继续使用 `arcade_collect` 证明新链路，但必须摆脱 V4 的“简单 SVG + 参数变化”观感：

- 至少一套完整且风格统一的 2D asset pack；
- idle/run/hit/death 或适用于角色的等价动画状态；
- 收集、受伤、胜利和失败反馈；
- 镜头跟随、有限震动或缩放反馈；
- 完整 HUD、开始页、暂停、结算和重新开始；
- 键盘与触摸控制；
- Web Mobile 游戏包可下载、解压并在本地启动；
- 确定性 Simulation 与 Cocos Runtime 通过 conformance fixtures 对齐关键结果。

首个切片通过后才开发第二 archetype。第二 archetype 必须复用同一 GenerationRun、GameSpec 核心、Java Tool Gateway 和 Artifact Gate，以证明不是复制第二套模板。

## 8. Agent 职责

“Director”“Spec Author”“Player”是有独立输入输出和验收责任的角色，不要求拆成三个独立部署服务。V5 目标形态是在 Java 进程中通过 Spring AI 提供模型能力，角色边界由领域接口、PromptVersion、结构化响应和评测集定义，不能用多个 `ChatClient` Bean 冒充多 Agent：

### Director

- 根据目标和当前证据选择下一工具；
- 管理生成、修复和试玩计划；
- 在预算耗尽、无进展或能力不支持时停止。

### Spec Author

- 在 capability registry 内生成或修改 GameSpec；
- 读取结构化诊断，不修改 Runtime 源码；
- 不得伪造构建或测试结果。

### Player

- 确定性 Player 作为必选基线；
- LLM Player 每步读取真实环境反馈；
- 产生动作、状态 hash、终止原因和 persona 指标。

## 9. Java Agent Tool Gateway

Spring AI 只能暴露 Java 注册的高层工具；迁移期 Python 兼容链路也只能通过同一 Gateway 调用：

- `get_runtime_capabilities`
- `validate_game_spec`
- `revise_game_spec`
- `run_deterministic_simulation`
- `build_cocos_preview`
- `build_mini_game_package`
- `run_playability_gates`
- `create_prototype_draft`

Java 对每次调用执行身份、项目归属、状态、JSON Schema、预算、幂等、超时和权限校验，并持久化请求、结果、错误、耗时和 digest。Spring AI 的 Tool Callback 只是现有 `DirectorToolRegistry` 的适配层，不能复制业务逻辑，也不能绕过 `GenerationRun` / `DirectorRun` 状态机。

Director 不采用框架默认的无限自动工具循环。每轮只允许模型返回一个类型化决策，由 Java 校验、落库并执行；下一轮必须从新的持久化快照恢复。这样可以继续执行现有预算、重试、人工审批、取消和故障恢复语义。Python 和模型均无权直接运行 Cocos 可执行文件。

## 10. RAG 定位

RAG 保留为辅助工具，只检索带来源和版本的：

- GameSpec 与 capability 文档；
- asset pack、许可和使用限制；
- archetype 设计经验；
- 历史编译、构建和试玩失败；
- 已批准的实验结论。

检索结果不能新增未注册能力或绕过 Java/Cocos gate。RAG 是否有效必须通过固定数据集、引用命中率和 on/off 对照验证。正式实现使用 Spring AI `EmbeddingModel` 与 Qdrant `VectorStore`，MySQL 继续保存文档、Chunk、索引任务、RetrievalRecord、版本与项目归属；Spring AI 的 `Document` 只作为基础设施传输对象，不替代领域实体。

## 11. 本地产物目标与 V6 边界

V5 只有一种正式可玩产物：`LOCAL_COCOS_WEB_PACKAGE`。

它用于本地试玩、浏览器自动化、下载交付和面试演示，必须包含游戏内容、manifest、启动说明或启动脚本以及验证证据。它不是微信、抖音、支付宝或任何其他平台的开发包。

V6 才研究并实现各小游戏平台的 target adapter、开发者工具、真机验证、账号配置、AppID、审核、备案、隐私、登录、支付、广告、排行榜与上传流程。V5 不为某个平台提前写占位实现。

## 12. 自动验收

### 12.1 规格与构建 Gate

- GameSpec schema、语义、引用和 capability 通过；
- 未包含远程脚本、路径穿越和未登记资源；
- 相同冻结输入产生相同规范化 digest；
- Cocos 构建退出码、日志、版本和输出文件完整；
- Web Mobile 目标的 manifest 可追溯。

### 12.2 可玩性 Gate

- 加载、开始、输入、暂停、终局和重开可达；
- 至少一个确定性 Player 能完成目标；
- seed、动作、状态 hash 和终止原因可重放；
- Runtime 与 Simulation 的关键规则 conformance 通过。

### 12.3 Visual Quality Gate

自动检查只覆盖可机械验证事实：

- 必需动画、UI、反馈、音频和资源不存在缺失引用；
- 首屏、进行中、胜利、失败截图均可生成；
- 不出现占位资源、拉伸异常、透明错误和致命日志；
- 目标分辨率与横竖屏策略正确。

“是否好看、是否有趣”必须由人工试玩 Review 决定，不能让模型给自己的作品打分后自动通过。

## 13. 首版成功标准

一次连续演示必须完成：

1. 输入一个受限小游戏 Brief；
2. Director 选择 archetype、asset pack 和表现预设；
3. 产生至少一个非法 GameSpec，并由 Java 返回稳定诊断；
4. Agent 在有限预算内修复；
5. 通过 Simulation 后触发 Cocos 构建；
6. 打开 Web Mobile 游戏并完成真实操作；
7. 下载本地包，在工作台之外启动同一个游戏；
8. 查看 Player 轨迹、artifact digest 和人工审批。

以下情况不算完成：

- 只展示 JSON、截图或录屏；
- 仍然运行 Phaser 页面；
- 只是把 V4 SVG 换成另一套图片；
- Agent 按固定预写步骤执行；
- Agent 直接修改 Cocos 工程文件或生成可执行代码；
- 产物只能嵌在工作台里，无法下载和本地启动；
- mock 小样本被宣传成真实模型收益。

## 14. 非目标

- 一句话生成任意类型游戏；
- 同时建设多个游戏引擎；
- 实现任何微信、抖音、支付宝或厂商小游戏平台适配；
- LLM 直接生成并执行 JavaScript/Java/Python；
- 为了“多 Agent”拆出没有独立责任的角色；
- 在首个切片稳定前开发商城、社区、广告或复杂运营后台；
- 在真实评测前宣称 Agent 或 RAG 提高了最终游戏质量。

## 15. 当前基线与剩余任务

### 15.1 已有且本轮不重复建设

截至 2026-08-09，仓库已有以下可验证基线：

- `arcade_collect/1` Capability Registry、GameSpec 0.1 编译、稳定 diagnostics、canonical/runtime/build-request digest；
- 项目隔离且幂等的 GenerationRun、状态版本、Cocos Build Worker 和两个 GameSpec Director Tools；
- `GameSpec → Cocos 3.8.8 web-mobile build → ZIP → 本地启动器 → 下载摘要复验` 主链路；
- V5 Generation Studio 的能力读取、规格编辑、服务端编译、构建、刷新和产物下载；
- 本机两次真实 Cocos CLI 构建证据；
- Java 全量单元/上下文测试 225 个通过、0 失败、1 个因 Docker/Testcontainers 环境跳过。

以上只证明“受控规格可以被编译和构建”，不等于完整 PRD 已完成。

### 15.2 未完成任务清单

| 优先级 | 任务 | 当前缺口 | 完成判据 |
| --- | --- | --- | --- |
| P0 | Spring AI 迁移底座 | **已完成 M1 与首个 M2 切片**：默认经领域端口调用 Spring AI，`APP_AI_PROVIDER=python` 可回滚；尚缺 shadow comparison 与 Director/Player 切流 | 完成第 16 节 M0-M2，旧链路可回滚且默认流量切到 Spring AI |
| P0 | 首套正式 Asset Pack 与表现系统 | 当前 Runtime Shell 能构建，但视觉完成度尚未达到第 7、12 节 | 动画、反馈、HUD、开始/暂停/结算、音频和触控均由真实构建验证并经人工试玩通过 |
| P0 | GameSpec 到 Runtime 的 conformance | 编译摘要和构建已存在，Simulation/Cocos 关键规则一致性证据不足 | 固定 fixtures 对胜负、碰撞、计分、时限和 seed 结果给出一致结论 |
| P0 | V5 Agent 诊断修复闭环 | **已完成首个闭环**：Studio 可调用 Spring AI Spec Author，Java 编译器 diagnostics 驱动最多三次修复并返回完整 attempt 轨迹；持久化审计仍随 M3 收口 | 至少一次非法规格被稳定诊断，Spring AI Spec Author 在预算内修复并保留完整轨迹 |
| P0 | Playability / Visual / Artifact Gate 编排 | 各基础能力存在，但尚未形成 V5 单一不可绕过门禁 | Gate 顺序、失败阻断、证据引用、重试和最终状态均有自动化测试 |
| P0 | V5 人工审批绑定 artifact digest | V4 有审批基座，V5 GenerationRun 产物审批闭环仍需落地 | 审批绑定不可变 digest，产物变化后审批自动失效，未审批不得进入完成态 |
| P1 | Player 迁移与真实对照 | LLM Player 仍由 Python `httpx` 调模型 | Spring AI Player 与确定性 Player 共用协议，超时、非法动作、token 和成本均可追踪 |
| P1 | 正式 RAG | 当前 Java 为 `fake-hash-v1` + 内存 VectorStore | 真实 EmbeddingModel + Qdrant、项目/版本过滤、重启恢复和 RAG on/off 报告通过 |
| P1 | E2E 与交付证据收口 | 真实构建已有手工证据，但连续演示和外部启动验收尚未自动化 | 第 13 节连续演示可重复，失败样本、环境限制和 digest 均归档 |
| 停止线 | 第二 archetype | 尚未批准 | 仅在上述 P0 全部完成且首个切片人工 Review 通过后评估；不属于当前开发范围 |

### 15.3 执行顺序

1. 先完成 Spring AI M0-M2，同时保持现有确定性编译、构建和下载链路可运行。
2. 并行完成 Asset Pack、表现系统和 conformance fixtures；它们不依赖模型迁移。
3. 用 Spring AI 接通 Spec Author 诊断修复，再接入 Director 受控决策与 Player。
4. 将 Simulation、Playability、Visual、Artifact 和人工审批串成不可绕过的 V5 Gate。
5. 完成真实 RAG、E2E、失败样本和交付报告后关闭 V5。

如果首个切片仍无法产出明显优于 V4 的可玩游戏，停止 Agent 深化和 RAG，优先修正 Runtime Shell、资产与表现系统。若 Spring AI 迁移无法保持现有状态、预算、审计和回滚语义，则停止切流，不以“已经接入 `ChatClient`”作为完成。

## 16. Spring AI 迁移决策

### 16.1 版本与范围

首轮迁移采用：

- Java 21 保持不变；
- Spring Boot 从 3.3.5 升级到 3.5.x 的最新可用 patch；
- Spring AI 锁定 1.1.8 BOM；
- 首个 Provider 继续使用当前 OpenAI-compatible endpoint 与现有模型，不在迁移中更换模型或 Prompt 目标；
- 引入 `spring-ai-starter-model-openai`；RAG 阶段再引入 `spring-ai-starter-vector-store-qdrant`。

Spring AI 1.1.x 官方支持 Spring Boot 3.4.x/3.5.x。Spring AI 2.0.0 虽是当前最新稳定主线，但要求 Spring Boot 4.0.x/4.1.x；V5 不把 Boot 4 大版本升级与 AI 迁移捆绑。版本依据见 [Spring AI 1.1.8 Getting Started](https://docs.spring.io/spring-ai/reference/1.1/getting-started.html) 与 [Spring AI 2.0.0 Getting Started](https://docs.spring.io/spring-ai/reference/getting-started.html)。

本次迁移只替换现有 AI 基础设施，不新增聊天、图像生成、MCP、通用工作流编排或新的 Agent 角色。

### 16.2 目标架构

```text
Controller / Worker
→ V5 Application Service 与持久化状态机
→ 领域端口：SpecAuthorModel / DirectorDecisionModel / PlayerPolicyModel
→ SpringAiModelGateway
   ├── ChatClient / ChatModel
   ├── 结构化输出到既有 DTO
   ├── PromptVersion 与安全 Advisor
   ├── Micrometer observation → ModelCallMetric
   └── DirectorToolRegistry → ToolCallback 适配器
→ OpenAI-compatible Provider

KnowledgeIndexing / Retrieval
→ Spring AI EmbeddingModel
→ Spring AI Qdrant VectorStore
→ MySQL 领域记录与 provenance
```

业务代码依赖项目自己的领域端口，不直接依赖 `ChatClient`、Provider DTO 或 Spring AI `Document`。这样可以隔离框架升级，并保留 fake/recorded provider 的确定性测试。

### 16.3 迁移阶段

| 阶段 | 工作内容 | 退出条件 |
| --- | --- | --- |
| M0 基线冻结 | 固定现有 Python 请求/响应、Prompt、模型参数、错误分类、mock 标记和评测 fixtures | Java/Python 契约测试、225 个 Java 测试和真实 Provider smoke 基线可复现 |
| M1 框架底座 | 升级 Boot、引入 Spring AI BOM/Starter，建立领域端口、Provider 配置、超时重试、指标与脱敏 | 不改变产品 API；缺少密钥时 fail closed；测试不依赖付费模型 |
| M2 低风险切流 | 先迁移现有单次内容生成和结构化 GameSpec 输出，使用 shadow comparison 比较 Python 与 Spring AI | 结构、错误语义、PromptVersion、token、延迟、mock 和 trace 可追溯 |
| M3 Agent 切流 | 迁移 Spec Author、Director 决策和 LLM Player；Director 保持“单轮决策—Java 落库—执行工具—再决策” | 预算、幂等、取消、恢复、权限和人工审批测试全部通过 |
| M4 RAG 切流 | 以 Spring AI `EmbeddingModel` / Qdrant `VectorStore` 替换 fake 与内存实现 | 维度、模型、索引版本、项目过滤、删除失效、重启恢复和检索指标通过 |
| M5 Python 退役 | 默认流量切到 Spring AI，观察稳定窗口后删除 LangChain/LangGraph/直接 `httpx` 模型调用 | 仓库无生产 Java→Python AI 依赖；回滚演练和文档完成 |

迁移期间使用显式配置在 `python` 与 `spring-ai` 实现间切换；不得双写领域状态。Shadow 模式只比较经过脱敏的输入摘要、结构化结果和指标，不执行第二份有副作用的工具调用。

### 16.4 实现约束

- `ChatClient` 只负责模型交互；GenerationRun、DirectorRun、预算、重试、审批和 artifact lineage 继续由现有 Java 服务负责。
- Spec Author 和 Player 使用封闭 DTO / JSON Schema；解析失败是显式模型错误，不能用字符串截取或静默默认值掩盖。
- Director 使用用户控制的工具执行流程，工具调用必须经过现有 registry、authorizer、schema validator、idempotency 和结果存储。
- Prompt 继续走现有 PromptVersion 与快照，不把 Prompt 常量散落到 `@Tool` 或 Controller。
- Spring AI observation 接入现有 traceId、AgentRun、ModelCallMetric；原始 Prompt、模型响应和密钥不进入普通日志。
- Provider 的默认重试必须收敛到项目预算；禁止框架重试、Worker 重试和消息重投叠加后突破最大模型调用数。
- RAG 查询必须显式带 `projectId`、document status、version 和 embedding model 过滤；禁止使用无过滤的通用 Advisor 直接访问全库。
- mock、recorded 与 real provider 必须是三个可区分事实；生产环境禁止因 Provider 失败自动回退为 mock 成功。

## 17. Spring AI 迁移验收

- [x] Boot 3.5.x + Spring AI 1.1.8 的依赖树无冲突，现有 Java 测试不回归（232 tests，0 failures，1 个 Docker 条件跳过）。
- [ ] 所有生产模型调用均通过项目领域端口进入 Spring AI，不再直接使用 Python LangChain、LangGraph 或 `httpx` 调模型。
- [ ] 同一冻结输入可比较 Python 与 Spring AI 的结构化结果；差异有报告，不能只比较自然语言文本。
- [x] Spec Author 能生成合法 GameSpec，也能根据 Java diagnostics 在最多三次模型调用预算内修复非法 GameSpec。
- [x] Director 使用 Spring AI 用户控制 Tool Calling；每轮只选择一个工具，Java 在执行前持久化决策，并保留工具参数、结果 digest、token、耗时和终止原因，重启后从检查点恢复（provider 未提供价格时成本记为 0，不推算）。
- [x] Tool Callback 仅适配现有 DirectorToolRegistry，可信 user/project/run/call 上下文由服务端注入；项目归属、schema、幂等、超时和人工审批仍由 Java 领域层执行。
- [ ] Player 非法动作、模型超时、结构化输出失败和预算耗尽均产生稳定错误，不被记为完成。
- [ ] 正式 RAG 使用真实 embedding 与 Qdrant，重启后可检索，跨项目数据不能命中。
- [ ] 生产缺少密钥或 Provider 不可用时 fail closed；mock/recorded 数据不混入真实成功率与成本。
- [ ] Spring AI 默认切流后完成一次回滚演练；稳定窗口结束前保留 Python 兼容实现，之后再删除。

当前实现状态（2026-08-09）：Spring Boot 3.5.16、Spring AI 1.1.8、OpenAI-compatible ChatClient、GameSpec DTO 结构化校验、compiler diagnostics 有界修复、项目上下文/能力边界/调用证据 Advisor，以及可恢复 Director Tool Calling 已落地。Director 使用用户控制执行模式，Spring AI 返回单个 tool call 后由 Java 状态机落库、校验并执行；测试配置关闭付费模型，生产缺少 ChatModel 时显式失败，不降级为 mock。M3 Player、M4 生产级 RAG、shadow comparison、回滚演练与 Python 退役仍按未勾选项推进。
