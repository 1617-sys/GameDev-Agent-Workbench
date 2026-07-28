# V4-02 Simulation Protocol RFC

## 目标

冻结无 UI 仿真的最小状态、动作和 step 语义，使 Phaser Adapter 与 Headless Runner 能共享同一个 Simulation Core。

## 允许修改

- 新建 `docs/requirements/v4/upgrade-0-foundation/V4-simulation-protocol.md`
- 新建不超过三个 JSON 示例到 `docs/requirements/v4/upgrade-0-foundation/examples/simulation/`

## 只读参考

- `frontend-vue/src/features/demo/runtime/topDownCollectRuntime.js`
- `frontend-vue/src/features/demo/runtime/gameConfig.js`
- `docs/game-config-schema.md`
- PRD 第 6、7、13-D4 节

## 禁止修改

- 所有生产代码和测试代码
- GameConfig 2.0 字段
- 现有遥测事件名称

## RFC 必须定义

- `SimulationState`、`Observation`、`Action`、`StepResult`；
- tick 长度、坐标、速度和时间单位；
- 玩家、敌人、障碍、收集物和出口的最小状态；
- 动作合法性与非法动作结果；
- 碰撞、受伤、无敌窗口、收集、胜负和超时的 step 顺序；
- `WON`、`HEALTH_DEPLETED`、`TIME_EXPIRED`、`MAX_STEPS`、`ERROR` 终止原因；
- seed、状态哈希、协议版本和向后兼容规则；
- 完整状态与 Persona 可见 Observation 的边界。

## 验收标准

- 给定 config、seed、初始状态和动作序列，协议不存在未定义行为；
- Phaser 与 Headless 不允许分别实现玩法规则；
- 示例至少包含进行中、获胜和非法动作；
- RFC 明确首版不支持连续角度、截图输入和任意脚本动作。

## 交付限制

不实现代码，不评价其他模块，不顺手设计 Agent Prompt。
