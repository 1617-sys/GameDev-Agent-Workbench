# Agentic Game Design Lab 产品需求文档

> 文档状态：COMPLETED / SUPERSEDED
> 目标版本：V4  
> 产品形态：面向游戏原型的自主试玩、平衡实验与经验记忆平台  
> 发布版本：`v4.0.0`
> 后继文档：[V5 Agentic Mini-Game Factory PRD](v5/game-generation-studio-prd.md)
> 说明：本文保留 V4 当时的产品决策与验收依据，不再代表当前产品方向。

## 1. 产品结论

GameDev Agent Workbench 下一阶段不再以“多步提示词生成 GameConfig”为核心卖点，而升级为：

> **Agentic Game Design Lab：由 Director 组织玩家 Agent 自主试玩，通过确定性评测与参数搜索产生候选版本，并将实验结论沉淀为可检索记忆的游戏设计实验室。**

平台继续只深耕 `arcade_collect`，不扩张多个半成品 Runtime。差异化来自以下闭环：

```text
设计目标
→ Director 制订实验计划
→ 检索设计知识和历史实验
→ 生成参数假设与候选配置
→ 多种 Player Persona 自主试玩
→ 确定性指标与 Critic 联合评测
→ 生成 DRAFT PrototypeVersion
→ 人工审批
→ 真人试玩
→ 结果写回实验记忆
```

## 2. 产品目标

### 2.1 求职展示目标

项目必须能用可复现证据证明：

1. Agent 能根据环境反馈进行多轮决策，而非一次生成文本。
2. 不同玩家 Agent 具有可以从轨迹和指标观察到的策略差异。
3. Director 能动态选择工具、安排实验、读取结果并决定下一步。
4. LLM 负责假设、规划和解释，确定性程序负责规则校验、仿真、指标与数值搜索。
5. RAG 同时服务设计知识和历史实验记忆，并提供来源、版本和命中证据。
6. Agent 不能直接发布正式版本；候选结果经过验证后进入人工审批。
7. Agentic 方案相对固定工作流或裸模型具有量化收益。

### 2.2 用户价值目标

用户提交“降低新手挫败感，但不要显著降低高手挑战性”一类目标后，系统应能够：

- 自动选择相关指标和历史经验；
- 运行多个玩家画像的批量试玩；
- 找出可能导致问题的参数；
- 搜索并比较多个候选版本；
- 给出有数据依据且标明不确定性的建议；
- 创建可试玩、可回滚、可审批的候选版本。

### 2.3 非目标

V4 不承诺：

- 从任意自然语言生成任意类型的完整游戏；
- 生成并执行任意 JavaScript、Java 或 Python 代码；
- 通过截图和鼠标键盘完成主要仿真路径；
- 用多个角色 Prompt 冒充真正的协作 Agent；
- 让 LLM 独立决定精确数值；
- 无人工审批自动发布正式游戏版本；
- 在没有固定评测集时宣称 RAG 或 Agent 提升效果。

## 3. 核心用户与场景

### 3.1 目标用户

- 独立游戏设计者：希望快速验证数值和难度假设。
- 游戏策划或测试人员：希望批量复现不同水平玩家的行为。
- 项目面试官：希望看到完整 Agent 工程链路及可验证结果。

### 3.2 核心演示场景

基线版本的新手完成率为 32%，熟练玩家完成率为 91%。用户要求将新手完成率提高到 55%—70%，同时让熟练玩家平均通关时间增幅不超过 8%。

系统执行：

1. Director 将目标转换为指标、约束和实验预算。
2. Memory Retrieval 检索相似历史实验和适用的设计知识。
3. Balance Designer 提出需要调整的参数及合法区间。
4. Optimizer 生成候选配置，而不是由 LLM 随机填写数值。
5. 新手、普通、熟练 Player Agent 分别运行多个 Episode。
6. Evaluator 淘汰无解、违规或目标不达标的候选。
7. Critic 解释结果、冲突和不确定性。
8. Director 选择候选并创建 `DRAFT` 版本，等待人工审批。
9. 真人试玩数据与机器 Episode 分开统计，并回写实验记忆。

## 4. Agent 与非 Agent 组件

### 4.1 Game Director Agent

职责：

- 理解实验目标并补全可执行约束；
- 根据当前状态动态选择工具；
- 控制实验预算、停止条件和失败恢复；
- 汇总候选证据并决定继续搜索、结束或请求人工介入；
- 创建实验报告和 `DRAFT` 候选版本。

Director 不得：

- 绕过 GameConfig 校验；
- 直接写数据库；
- 自动发布正式版本；
- 在证据不足时声称目标已经实现。

### 4.2 Player Persona Agents

首批提供三类画像：

- `NOVICE`：较慢反应、有限视野、较高操作误差和保守策略；
- `REGULAR`：正常反应和启发式决策；
- `EXPERT`：更完整状态利用、更低操作误差和目标效率优先。

每个 Player Agent 必须执行 `observe → decide → act → receive feedback` 循环。画像差异必须落实为策略、可见信息、动作延迟或错误模型的差异，不能只修改人格描述。

### 4.3 Balance Designer Agent

职责：

- 根据目标、轨迹和历史知识提出可证伪假设；
- 选择允许修改的参数和搜索边界；
- 解释候选版本可能产生的影响；
- 对搜索结果形成面向人的设计结论。

精确参数候选由 Optimizer 工具产生。

### 4.4 Playtest Critic

职责：

- 检查目标、约束和指标是否一致；
- 识别平均值掩盖的失败模式；
- 结合典型 Episode 分析体验问题；
- 对主观维度提供带 rubric 的补充评价。

规则合法性、完成率、耗时和动作效率等客观判断由确定性 Evaluator 承担。第一版 Critic 可以是 Director 的受控子流程，待独立价值被评测证明后再拆成独立 Agent。

### 4.5 现有生成步骤的定位

`GAME_CONCEPT`、`CORE_LOOP_DESIGN`、`TASK_BREAKDOWN`、`GAME_CONFIG_GENERATE` 保留为 `Prototype Generation Skill` 内部步骤，不再对外宣称为四个自主 Agent。

## 5. 工具协议

Agent 只能通过类型化工具访问系统能力。首批工具包括：

- `get_prototype_version`
- `validate_game_config`
- `create_draft_version`
- `retrieve_design_knowledge`
- `retrieve_experiment_memory`
- `create_experiment`
- `run_episode_batch`
- `get_experiment_metrics`
- `compare_candidates`
- `search_parameter_candidates`
- `request_human_approval`

所有工具调用必须记录：Agent、Run、输入摘要、输出摘要、耗时、状态、错误分类、重试次数和关联版本。敏感字段按现有证据脱敏规则处理。

## 6. Runtime 环境协议

### 6.1 Observation

结构化 Observation 至少包含：

- Episode ID、tick 和剩余时间；
- 玩家位置、速度、生命或状态；
- 可见实体及相对位置；
- 已收集数量、目标数量和当前得分；
- 最近动作结果；
- 当前终止状态与原因。

首版以结构化状态为正式评测路径。浏览器画面仅用于展示和后续黑盒复核。

### 6.2 Action

动作空间必须有限、可校验、可重放，例如：

- `MOVE_UP`
- `MOVE_DOWN`
- `MOVE_LEFT`
- `MOVE_RIGHT`
- `WAIT`
- `RESTART`

非法动作返回明确错误，不得静默修正。

### 6.3 Episode

每个 Episode 保存：

- PrototypeVersion 与 GameConfig 哈希；
- Persona 与策略版本；
- 随机种子；
- Observation/Action/Reward 轨迹；
- 结束原因；
- token、模型、耗时和成本；
- 指标计算版本。

相同配置、策略版本和随机种子必须可重放。

## 7. 实验与评测

### 7.1 客观指标

- 完成率；
- 平均与 P50/P95 完成时间；
- 超时率、死亡率、卡死率；
- 有效动作比例；
- 路径效率；
- 不同 Persona 的表现差距；
- 候选相对基线的变化；
- token、延迟和单次实验成本。

### 7.2 分层门禁

候选版本按以下顺序验收：

1. `CONFIG_VALID`：Schema 和业务规则通过；
2. `RUNTIME_READY`：Runtime smoke 通过；
3. `PLAYABLE`：在规定预算内可完成且无明显无解状态；
4. `TARGET_MET`：目标指标和保护约束同时满足；
5. `HUMAN_APPROVED`：人工批准进入发布流程。

### 7.3 Agent 增益评测

固定评测集至少包含 20 个设计目标，每个目标对比：

- 裸模型一次生成；
- 现有固定 Workflow；
- Director + Tools，不启用 RAG；
- Director + Tools + Experiment Memory RAG。

报告至少包含目标达成率、非法工具调用率、平均迭代轮数、总成本、P95 延迟和人工采纳率。没有对照实验，不得对外声称 Multi-Agent 或 RAG 有效。

## 8. RAG 与实验记忆

### 8.1 两类知识库

`Design Knowledge` 保存经过审核的设计原则、约束和适用条件。

`Experiment Memory` 保存：

- 问题和目标；
- 基线版本与指标窗口；
- 参数假设和候选配置；
- 机器与真人样本来源；
- 结果、结论和置信度；
- 被接受、拒绝或修改的原因。

### 8.2 检索要求

- 使用真实 embedding 和持久化向量索引；
- 记录 provider、model、dimension 和索引版本；
- 强制 project、document status 和 version 过滤；
- 返回 chunk、score、rank、来源和引用文本；
- 建立固定检索集，报告 Recall@K、MRR/nDCG、错误项目命中数和延迟；
- 实验报告必须区分“检索到的经验”和“本轮实际验证的事实”。

## 9. 分阶段交付

### Upgrade 0：PRD、边界与基线冻结

目标：在新增代码前冻结语义和当前质量基线。

交付物：

- 本 PRD 通过 Review；
- Agent、Skill、Tool、Evaluator 的术语统一；
- 旧同步/单 Agent API 的调用方和弃用计划；
- 当前主流程、测试、延迟和成本基线；
- V4 数据模型与 API RFC。

验收：旧系统全部测试仍可复现；V4 不以修改旧链路作为隐性前提。

### Upgrade 1：可控制、可重放的游戏环境

目标：让程序化玩家通过正式协议操作 Runtime。

交付物：

- Observation/Action 契约；
- headless 或确定性 simulation adapter；
- Episode/Step 持久化；
- 随机种子、终止原因和 replay；
- 基础批量运行 API 与测试。

验收：同一配置、策略和种子得到一致结果；至少能完成 100 局批量运行并生成指标。

### Upgrade 2：Player Persona 与试玩评测

目标：先证明 Agent 真能玩，再引入管理 Agent。

交付物：

- 一个基于工具调用的 Player Agent；
- 新手、普通、熟练三套可解释策略；
- Episode 轨迹查看器；
- 完成率、时间、失败原因和路径效率指标；
- Persona 差异的固定评测集。

验收：三类 Persona 在固定地图上呈现稳定、可解释的指标差异；Agent 失败可从轨迹定位。

### Upgrade 3：Director 与实验编排

目标：形成第一条真正的 Agentic 闭环。

交付物：

- Director 状态图；
- 类型化 Tool Registry；
- 实验预算、停止条件、超时和恢复；
- 候选比较与 DRAFT 创建；
- 人工审批节点；
- Agent Run/Tool Call 可观测页面。

验收：Director 能从自然语言目标出发，自主完成一次基线分析、候选试玩、比较和 DRAFT 创建；不得直接发布。

### Upgrade 4：参数优化与 Critic

目标：将“LLM 猜参数”升级为“LLM 提假设、算法做搜索”。

交付物：

- 参数空间和约束声明；
- 首版网格/随机搜索基线；
- 贝叶斯优化工具；
- 多目标评分和保护约束；
- Critic rubric 与典型失败轨迹分析。

验收：在固定调参任务中，相同仿真预算下优于随机搜索基线，且不会提交违反保护约束的候选。

### Upgrade 5：真实 RAG 与实验记忆

目标：让系统复用历史经验，并量化检索价值。

交付物：

- 正式 embedding provider 和持久化向量库；
- Design Knowledge 与 Experiment Memory 分区；
- 索引任务、重试、删除失效和 provenance；
- 检索评测集；
- RAG on/off Agent 对照报告。

验收：重启后索引可恢复；检索指标可复现；至少一个固定任务中 RAG 相对无 RAG 有可量化收益，否则如实报告无增益。

### Upgrade 6：产品化演示与求职证据

目标：把工程能力变成面试官十分钟内可以理解的证据。

交付物：

- 实验控制台和候选对比页面；
- Episode replay 与 Agent 决策轨迹；
- 一键准备、运行、重置演示；
- Agent/RAG/Optimizer 消融实验报告；
- 架构、README、简历、面试问答同步更新；
- 端到端、故障恢复、安全与成本门禁。

验收：从全新环境启动后，可以稳定演示完整场景；所有宣传数字均能追溯到固定输入、版本和原始结果。

## 10. 兼容与弃用策略

旧同步/单 Agent API 不在 Upgrade 0 立即删除：

1. Upgrade 0 标记 deprecated，并记录调用量和调用方；
2. Upgrade 1—2 保留为固定 Workflow 基线及回归通道；
3. Upgrade 3 将默认 UI 流量切到 Director；
4. Upgrade 5 完成对照实验后冻结旧 API；
5. Upgrade 6 前仅在调用方清零、迁移文档和回滚验证完成后删除。

旧 API 的保留价值是提供实验基线，不再承担产品主叙事。

## 11. 工期预测

估算假设：一名开发者全职投入，熟悉当前仓库；Codex 承担代码生成、测试补齐、机械重构和文档同步；开发者负责需求决策、PR Review、运行验证和演示判断。

| 阶段 | 乐观 | 现实 | 主要不确定性 |
|---|---:|---:|---|
| Upgrade 0 | 2 天 | 3—4 天 | 契约和迁移边界是否反复变化 |
| Upgrade 1 | 5 天 | 7—10 天 | Phaser 与 headless 仿真的一致性 |
| Upgrade 2 | 5 天 | 7—10 天 | Persona 是否形成稳定行为差异 |
| Upgrade 3 | 7 天 | 10—14 天 | 多轮状态恢复、工具幂等和人工审批 |
| Upgrade 4 | 5 天 | 7—10 天 | 目标函数、样本预算与优化收敛 |
| Upgrade 5 | 7 天 | 10—14 天 | embedding、向量库和评测集质量 |
| Upgrade 6 | 5 天 | 7—12 天 | UI、E2E、演示稳定性与材料收口 |

日历预测：

- **第一个可信垂直切片：2—3 周。** 完成 Upgrade 0—2，可以展示三类 Agent 自主试玩和可重放轨迹。
- **Agentic MVP：4—6 周。** 完成 Upgrade 0—4，可以展示 Director 驱动的自动平衡实验闭环。
- **求职级完整版本：7—10 周。** 完成真实 RAG、对照评测、前端证据链和演示硬化。
- **如果每天只能投入 3—4 小时：约 12—16 周。**

任何“全功能两周完成”的估计都意味着牺牲至少一项：可重放环境、真实评测、RAG 质量、故障恢复或演示稳定性。Codex 能显著压缩实现时间，但无法替代指标设计、实验运行和人工验收。

## 12. Vibe Coding 执行规则

每个 Upgrade 必须单独完成以下循环：

```text
PRD/RFC
→ 契约测试
→ 最小实现
→ 单元与集成测试
→ 浏览器或端到端验收
→ 指标/证据归档
→ 文档同步
→ 人工 Review
```

实施约束：

- 每个 Codex 任务只负责一个明确验收目标；
- 先写契约和失败用例，再允许跨模块实现；
- Java 负责业务事实、版本、权限、审计和可靠执行；
- Python 负责 Agent 状态图、模型调用、策略和优化算法；
- Phaser Runtime 负责真实交互展示，同时提供与仿真协议一致的适配器；
- 不在同一提交中混合数据库迁移、Agent 重构和大规模 UI 改版；
- 每个阶段保留可运行主干，不建立长期不可运行的“大重构分支”；
- Codex 生成的完成声明必须由测试、日志或固定实验结果支撑。

## 13. 已批准架构决策

以下决策以“最快形成可信、可评测的 Agent 闭环”为标准，不再作为实施阶段的开放问题：

### D1：唯一主定位

接受 `Agentic Game Design Lab` 作为项目唯一主定位。旧的“多步 AI 游戏配置生成器”只作为已有能力和对照基线，不再与新定位并列宣传。

原因：双重定位会稀释简历叙事，也会诱发无关功能继续扩张。

### D2：只支持一种玩法

V4 只覆盖 `arcade_collect`。除非 Agentic MVP 和固定评测全部完成，否则不增加第二种 Runtime。

原因：新增玩法会同时扩大 Schema、仿真、Persona、指标、优化参数和测试矩阵，但不会证明更强的 Agent 能力。

### D3：确定性策略与 LLM 策略并存，但顺序交付

先实现确定性 Player Baseline，再实现调用工具的 LLM Player Agent；两者使用同一 Observation/Action/Episode 协议并长期保留为对照。

原因：只有确定性基线才能证明 LLM Agent 是否带来新能力，也能在模型不可用时完成低成本回归。不得同时开发两套协议。

### D4：TypeScript Simulation Core 是玩法事实源

从现有 Phaser Runtime 中抽取无 UI、可注入随机种子、可逐 tick 执行的纯 TypeScript Simulation Core。浏览器 Phaser Adapter 和 Node Headless Runner 共同调用该 Core。

Java 不再实现第二套碰撞和玩法规则；Java 继续作为项目、版本、权限、审批、审计、实验元数据与持久化事实源。

原因：当前真实玩法逻辑已存在于 Phaser。另写 Java 仿真最快开始、最慢收尾，长期必然发生规则漂移。

### D5：优化算法渐进升级

首版按以下顺序实现：

1. 固定候选，用于契约验证；
2. 随机搜索，作为最低基线；
3. 网格搜索，用于小参数空间和可复现实验；
4. 贝叶斯优化，只有在相同预算对照中胜过基线后才成为默认工具。

原因：没有基线的贝叶斯优化只是技术名词，无法形成可信求职证据。

### D6：正式向量库选择 Qdrant

MySQL 继续保存知识文档、Chunk、索引任务和 RetrievalRecord 等业务事实；Qdrant 只保存向量、检索字段和业务 ID 引用。

原因：现有主库是 MySQL。选择 pgvector 会额外引入 PostgreSQL 关系数据库职责，或迫使项目迁库；Qdrant 的边界更窄，接入和替换成本更低。V4 不同时维护两个正式向量后端。

### D7：旧 API 渐进弃用

批准第 10 节的渐进弃用方案。旧同步/单 Agent API 在完成 Agentic 对照实验之前不得删除，但立即停止新增功能。

原因：它既是回滚通道，也是评估 Director 工作流增益所必需的基线。

### D8：以六周 Agentic MVP 为主交付目标

主计划采用六周全职节奏：

- 第 1 周：Upgrade 0，完成契约、基线和 TypeScript Simulation Core 拆分起步；
- 第 2 周：完成 Upgrade 1，可重放 Episode 与批量仿真；
- 第 3 周：完成 Upgrade 2，确定性与 LLM Player、三类 Persona 和轨迹指标；
- 第 4 周：完成 Upgrade 3，Director、Tool Registry、DRAFT 与人工审批；
- 第 5 周：完成 Upgrade 4，搜索基线、贝叶斯优化和 Critic；
- 第 6 周：完成 Upgrade 5 的最小正式链路以及 Upgrade 6 的核心演示、对照报告和文档。

第 7—10 周作为可选硬化期，不进入六周 MVP 的功能承诺，只处理真实使用暴露的问题、扩充评测集和提高演示稳定性。

若任一周延期，按以下顺序删减，不推迟核心闭环：

1. 延后独立 LLM Critic，先使用确定性 Evaluator 和 Director 受控复核；
2. 减少前端装饰和次要图表；
3. 缩小知识库与评测集规模，但不取消 RAG on/off 对照；
4. 延后贝叶斯优化成为默认，只保留已验证的随机/网格搜索；
5. 不删减 Episode 可重放、工具审计、人工审批和固定基线。

## 14. 实施优先级

所有任务按以下优先级裁决冲突：

```text
可执行闭环
> 可重放与可验证
> 对照评测
> 故障可定位
> RAG 和优化深度
> UI 完整度
> Agent 数量
> 技术名词数量
```

如果某项工作不能改善闭环、评测或求职证据，本版本默认不做。
