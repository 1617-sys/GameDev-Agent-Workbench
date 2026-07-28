# V4-09 Player 与 Persona RFC

## 目标

冻结 Player 决策循环、Persona 行为参数和 Java → Python → Simulation Service 单向调用契约。

## 允许修改

- 新建 `docs/requirements/v4/upgrade-2a-player-foundation/V4-player-persona-protocol.md`
- 新建不超过四个协议 JSON 示例

## 只读参考

- `docs/requirements/v4/upgrade-0-foundation/V4-simulation-protocol.md`
- `docs/requirements/v4/upgrade-0-foundation/V4-episode-protocol.md`
- `frontend-vue/src/features/demo/runtime/headless/index.ts`
- PRD 第 4.2、6、7 节

## 禁止修改

- 生产代码、依赖和 Docker
- 设计 Director、Optimizer 或 RAG

## 必须定义

- `reset/observe/step/close` 环境会话；
- `PlayerPolicy.decide(observation) -> action`；
- 决策超时、非法输出、重试、模型失败和预算耗尽；
- `NOVICE`、`REGULAR`、`EXPERT` 的视野、决策间隔、动作误差和规划能力；
- Persona 随机行为必须由独立、可重放 seed 驱动；
- 确定性 Player 与 LLM Player 使用相同输入输出；
- Java 发起运行、Python 决策、Node 推进环境、Java 持久化的所有权边界。

## 验收标准

- 不允许一次生成完整动作序列冒充 Agent；
- 每一步必须记录 Observation digest、Action、决策耗时和错误；
- Persona 差异可通过参数和固定实验验证，不依赖人格文案；
- 协议包含会话 TTL、并发上限和关闭语义。
