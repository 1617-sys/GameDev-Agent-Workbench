# V4-19 Director 与 Experiment RFC

## 目标

冻结 Director 的目标、状态机、单轮决策、工具调用、实验预算和人工审批协议。

## 允许修改

- 新建 `docs/requirements/v4/upgrade-3a-director-foundation/V4-director-experiment-protocol.md`
- 新建不超过五个协议 JSON 示例

## 只读参考

- `docs/requirements/agentic-game-design-lab-prd.md` 第 4、5、7、9 节
- `docs/requirements/v4/upgrade-0-foundation/V4-episode-protocol.md`
- `docs/requirements/v4/upgrade-2a-player-foundation/V4-player-persona-protocol.md`
- PlayerRun、PrototypeVersion 与 MachineEpisode 现有接口

## 禁止修改

- 生产代码、依赖、数据库和 Docker
- 设计贝叶斯优化、独立 Critic 或正式 RAG

## 必须定义

- 结构化 `DesignGoal`：目标指标、目标区间、保护约束、允许参数和实验预算；
- DirectorRun 状态：PENDING、RUNNING、WAITING_EXPERIMENT、WAITING_APPROVAL、SUCCEEDED、FAILED、CANCELED；
- `DirectorStateSnapshot`、`DirectorDecision`、`ToolCallRequest/Result`；
- 每轮只能 `CALL_TOOL`、`REQUEST_APPROVAL`、`FINISH`、`FAIL` 四选一；
- 工具 allowlist、参数 schema、读写权限、幂等和结果引用；
- 最大轮数、工具调用数、候选数、Episode 数、token、成本、墙钟和失败预算；
- Java 保存状态并执行工具，Python 只做决策的所有权边界；
- 暂停、恢复、取消、人工批准/拒绝和终态不变量。

## 验收标准

- 不允许 Python 直接写数据库或调用用户态 Java API；
- 不允许一次返回多个未排序工具调用；
- 任一决策可由状态快照和历史结果重放；
- Director 无权自动批准或发布版本；
- 自然语言目标必须归一化为受限指标后才能运行。
