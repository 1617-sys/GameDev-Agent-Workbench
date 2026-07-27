# V4 Simulation Protocol

> 状态：RFC FROZEN
> 协议版本：`simulation/1.0`
> 适用玩法：`arcade_collect`
> 上游配置：`game-config/2.0`

## 1. 目的与边界

本协议冻结无 UI 仿真的最小状态、动作和逐步执行语义。后续 TypeScript Simulation Core 是玩法规则的唯一事实源；Phaser Adapter 只投影画面、输入、音频和回调，Node Headless Runner 只负责批量驱动。二者不得复制碰撞、计分、受伤、胜负或超时规则。

首版只支持：

- `arcade_collect`；
- 结构化 `Observation`；
- 上、下、左、右、等待和重开六种离散动作；
- 固定 tick、确定性 seed 和可验证状态哈希。

首版明确不支持连续角度、模拟摇杆幅度、截图或像素输入、任意脚本动作、模型生成代码、连续时间步长、第二种玩法以及 Phaser 物理引擎作为规则事实源。

## 2. 规范用语与数据约束

“必须”“不得”“应”是协议要求。所有协议对象均为 UTF-8 JSON；字段名区分大小写；时间和计数为非负整数；ID 使用上游 GameConfig 已验证的稳定 ID。`null` 只允许出现在本文明确声明可空的字段中。

### 2.1 单位与坐标

| 概念 | 权威单位 | 规则 |
|---|---|---|
| tick | `50 ms` | 固定为 `TICK_MS = 50`，调用方不得传入 delta |
| 时间 | ms | 整数；1 秒等于 1000 ms |
| 世界坐标 | milli-pixel，记作 `mp` | `1 px = 1000 mp`；Core 状态只保存整数 mp |
| 速度 | mp/s | GameConfig 的整数 px/s 乘 1000 |
| Observation 坐标 | px | `mp / 1000`，最多三位小数，不参与规则计算 |
| 原点 | 世界左上角 | `x` 向右增加，`y` 向下增加 |

每 tick 的轴向位移为 `velocityMpPerSecond * 50 / 1000`。GameConfig 2.0 的速度是整数 px/s，因此结果等于 `speed * 50` mp，仍为整数，不需要浮点舍入。所有碰撞、距离比较和状态哈希均使用整数 mp；展示层的浮点坐标不得写回 Core。

### 2.2 几何

- 玩家、敌人和收集物是圆，半径为各自 GameConfig `size / 2`，换算为 mp。
- 障碍和出口是以 GameConfig `x, y` 为中心的轴对齐矩形。
- 两个圆在中心距离平方小于或等于半径和平方时接触。
- 圆与矩形在圆心到矩形最近点距离平方小于或等于圆半径平方时接触。
- “接触”对收集、受伤和到达出口均算命中；对实体障碍则算阻挡，移动实体停在首次接触位置且不得穿透。

## 3. Episode 初始化

创建 Episode 的输入必须包含：

| 字段 | 类型 | 说明 |
|---|---|---|
| `protocolVersion` | string | 固定 `simulation/1.0` |
| `episodeId` | string | 调用方生成的本次轨迹稳定 ID |
| `gameConfig` | object | 已通过三端一致校验的完整 GameConfig 2.0 |
| `configDigest` | string | 规范化 GameConfig UTF-8 JSON 的 SHA-256 小写十六进制 |
| `seed` | integer | `0..4294967295`；必须持久化，不得只依赖隐式默认值 |
| `maxSteps` | integer | `1..1000000`；Episode 的已接受动作预算 |
| `observationPolicy` | object | 第 9 节定义的 `FULL` 或 `PERSONA` 投影策略 |

调用方未显式提供 seed 时，可以在协议边界把 `gameConfig.metadata.seed` 转成无符号 32 位整数，但创建后的输入必须含解析后的 `seed`。`maxSteps` 是 Episode 执行预算，不是 GameConfig 字段，不得写回 GameConfig 2.0。

初始化顺序固定为：

1. 校验协议主版本、GameConfig、digest、seed、预算和 Observation policy；失败则不创建 Episode。
2. 把所有 GameConfig 几何乘 1000 转成整数 mp。
3. 玩家位于 `world.spawn`，速度为 0，生命为 `player.maxHealth`，无敌截止时间为 0。
4. 收集物均为 active；分数、收集数和重开次数为 0；出口按目标数计算为锁定。
5. 敌人位于配置初始位置，并按第 8 节从 seed 生成初始巡逻方向。
6. 状态为 `RUNNING`，`step=0`、`attempt=1`、`attemptStep=0`、`elapsedMs=0`、`remainingMs=timeLimitSeconds*1000`。
7. 计算初始状态哈希并产生生命周期事件 `SESSION_STARTED`。该事件属于 Episode 创建结果，不重复塞入第一个 `StepResult`。

初始化后即为 RUNNING，不暴露 Phaser 的 READY/PAUSED 展示状态。暂停是 Runner 调度行为，不消耗 tick，也不改变 Core 状态。

## 4. SimulationState

`SimulationState` 是 Core 的完整权威状态，采用以下逻辑结构。实现可以使用 TypeScript 类型，但序列化字段和语义必须一致。

```text
SimulationState
  protocolVersion: "simulation/1.0"
  episodeId: string
  configDigest: sha256
  seed: uint32
  tickMs: 50
  step: integer
  maxSteps: integer
  attempt: integer
  attemptStep: integer
  elapsedMs: integer
  remainingMs: integer
  status: RUNNING | TERMINATED
  terminationReason: null | WON | HEALTH_DEPLETED | TIME_EXPIRED | MAX_STEPS | ERROR
  restartCount: integer
  score: integer
  collectedIds: string[]
  targetCollectibles: integer
  exitUnlocked: boolean
  player: PlayerState
  enemies: EnemyState[]
  obstacles: ObstacleState[]
  collectibles: CollectibleState[]
  exit: ExitState
```

### 4.1 PlayerState

```text
id: "player"
position: { xMp: integer, yMp: integer }
velocity: { xMpPerSecond: integer, yMpPerSecond: integer }
radiusMp: integer
health: integer
maxHealth: integer
invulnerableUntilMs: integer
```

`invulnerableUntilMs` 使用当前 attempt 的 `elapsedMs` 时钟。当且仅当 `elapsedMs < invulnerableUntilMs` 时玩家无敌；相等时可以再次受伤。

### 4.2 EnemyState

```text
id: string
position: { xMp, yMp }
velocity: { xMpPerSecond, yMpPerSecond }
radiusMp: integer
axis: "x" | "y"
originMp: integer
patrolDistanceMp: integer
direction: -1 | 1
speedMpPerSecond: integer
```

敌人只沿配置巡逻轴移动。`originMp` 是 GameConfig 初始坐标在巡逻轴上的值。

### 4.3 静态和可收集实体

```text
ObstacleState  = { id, center: {xMp,yMp}, widthMp, heightMp }
CollectibleState = { id, position: {xMp,yMp}, radiusMp, score, active }
ExitState = { id: "exit", center: {xMp,yMp}, widthMp, heightMp, unlocked }
```

数组顺序固定：障碍、敌人和收集物均保持规范化 GameConfig 中的数组顺序；任何需要同时处理多个接触的规则按实体 ID 的 Unicode code point 升序执行。`collectedIds` 按实际收集顺序保存。

## 5. Action

合法 `Action` 是只包含 `type` 的闭合对象：

```json
{ "type": "MOVE_UP" }
```

`type` 只能是：

| Action | 本 tick 玩家速度 |
|---|---|
| `MOVE_UP` | `(0, -player.speed)` |
| `MOVE_DOWN` | `(0, +player.speed)` |
| `MOVE_LEFT` | `(-player.speed, 0)` |
| `MOVE_RIGHT` | `(+player.speed, 0)` |
| `WAIT` | `(0, 0)` |
| `RESTART` | 特殊重置，不执行世界移动 |

每个 MOVE 只持续一个 tick；下一个 step 不会隐式沿用旧方向。首版没有对角移动、按键组合、动作时长、连续角度或任意 payload。未知字段、缺失/未知 `type`、非对象、JSON 非有限数值，以及对已终止 Episode 调用 step，均为非法动作请求。

`RESTART` 仅在 `RUNNING` 时合法。它消耗一个 Episode step，令 `attempt += 1`、`restartCount += 1`，重新执行第 3 节的玩法状态初始化，并把 `attemptStep`、`elapsedMs` 归零；`step` 和 `maxSteps` 不归零。它产生 `SESSION_RESTARTED`，不产生新的 `SESSION_STARTED`。若消费后 `step >= maxSteps`，重置完成后立即以 `MAX_STEPS` 终止。Episode 已终止后如需重玩，宿主必须创建新 Episode ID；不得用 RESTART 复活终态。

## 6. StepResult 与非法动作

每次 `step(requestedAction)` 必须返回一个 `StepResult`：

```text
StepResult
  protocolVersion: "simulation/1.0"
  episodeId: string
  step: integer
  requestedAction: JSON value
  appliedAction: Action | null
  accepted: boolean
  advanced: boolean
  previousStateHash: sha256
  stateHash: sha256
  status: RUNNING | TERMINATED
  terminationReason: null | WON | HEALTH_DEPLETED | TIME_EXPIRED | MAX_STEPS | ERROR
  scoreDelta: integer
  events: TelemetryEvent[]
  error: null | { code, message, retriable }
  observation: Observation
```

合法动作令 `accepted=true`。MOVE/WAIT 令 `advanced=true` 并推进一个 50ms tick；RESTART 令 `advanced=false`，但仍消耗 step。`scoreDelta` 是本 step 的游戏分数变化，不是可配置的 Agent 奖励函数。

非法动作必须返回：

- `accepted=false`、`advanced=false`、`appliedAction=null`；
- `step`、状态、时间、PRNG、事件和分数完全不变；
- `previousStateHash == stateHash`；
- `events=[]`、`scoreDelta=0`；
- `error.code` 为 `INVALID_ACTION` 或 `EPISODE_TERMINATED`，`retriable=false`；
- 基于未改变状态重新投影 Observation，并在 `lastAction` 中暴露拒绝原因。

非法动作不是 `ERROR` 终止。只有合法请求执行时 Core 发现不变量破坏、整数溢出、非有限派生值或无法完成规范步骤，才把状态原子地转为 `TERMINATED/ERROR`，产生不含敏感数据的稳定错误码，并禁止继续 step。不得静默修正非法动作，也不得由 Phaser 与 Headless 采用不同容错。

## 7. 单个 MOVE/WAIT step 的唯一顺序

合法 MOVE 或 WAIT 必须原子地按以下顺序执行；任何 Adapter 不得插入额外玩法规则：

1. **前置校验**：确认状态为 RUNNING、Action 合法、旧状态哈希和内部不变量有效。
2. **占用预算**：`step += 1`、`attemptStep += 1`。
3. **推进时钟**：`elapsedMs += 50`，`remainingMs = max(0, remainingMs - 50)`。
4. **超时判定**：若 remaining 变为 0 且 `loseConditions` 包含 `time_expired`，立即以 `TIME_EXPIRED` 终止；本 step 不再移动、碰撞、收集或到达出口。
5. **应用玩家动作**：设置本 tick 速度，沿唯一轴做连续扫掠，按第 7.1 节处理世界边界和障碍。
6. **移动敌人**：按 GameConfig 数组顺序沿巡逻轴扫掠；到达巡逻端点、世界边界或障碍首次接触点时停下，并仅为下一 tick 反转一次方向；剩余位移丢弃。
7. **收集**：找出所有与玩家接触且 active 的收集物，按 ID 升序逐个设为 inactive、追加 `collectedIds`、累加其 score，并各产生一个 `ITEM_COLLECTED`。达到 `targetCollectibles` 时立即且永久解锁出口。
8. **敌人接触与受伤**：找出所有接触玩家的敌人并按 ID 升序检查。若玩家仍在无敌窗口，本 step 不受伤；否则只采用第一个敌人造成一次 `contact.damage`，产生一个 `PLAYER_HIT`，并设置新的无敌截止时间。多个敌人同 tick 不叠加伤害。
9. **生命终止**：若 `loseConditions` 包含 `health_depleted`，生命最低为 0，降为 0 时以 `HEALTH_DEPLETED` 终止；若不包含，生命最低为 1。
10. **出口与获胜**：仅在仍 RUNNING、出口已解锁且玩家接触出口时增加 `winBonus`，产生 `GAME_WON`，并以 `WON` 终止。锁定出口的接触没有副作用。
11. **步数终止**：若仍 RUNNING 且 `step >= maxSteps`，以 `MAX_STEPS` 终止。该原因不映射为现有 `GAME_LOST`，由 Episode 层记录；不得新增同名遥测事件。
12. **收尾**：把终态玩家和敌人速度设为 0，校验不变量，规范化状态，计算 hash，投影 Observation，并返回 StepResult。

终止优先级由上述顺序唯一决定：`TIME_EXPIRED` 高于本 tick 空间交互；随后是 `HEALTH_DEPLETED`、`WON`、`MAX_STEPS`。一次 step 只能有一个 terminationReason。

### 7.1 移动与阻挡

玩家动作是轴向的，因此使用圆心沿线段对“世界边界内缩圆半径后的矩形”和“每个障碍扩张圆半径后的 AABB”做 sweep。取非负路径参数最小的首次接触；并列时世界边界优先于障碍，多个障碍按 ID 升序。圆心停在接触坐标，速度仍表示本 tick 请求速度，直至第 12 步终态清零或下一动作覆盖。不得用离散终点采样造成高速穿透。

敌人使用同样 sweep，但额外受 `[originMp-patrolDistanceMp, originMp+patrolDistanceMp]` 限制。首次接触后停下、`direction *= -1`，下一 tick 才沿新方向移动。若同一位置同时命中多个边界，只反转一次。

## 8. Seed、PRNG 与状态哈希

### 8.1 Seed

协议 1.0 使用当前 Runtime 已采用的 Mulberry32 变体。初始化 `value = seed >>> 0`；每次取值依次执行：

```text
value = (value + 0x6d2b79f5) >>> 0
mixed = value
mixed = imul(mixed ^ (mixed >>> 15), mixed | 1)
mixed = mixed ^ (mixed + imul(mixed ^ (mixed >>> 7), mixed | 61))
random = ((mixed ^ (mixed >>> 14)) >>> 0) / 4294967296
```

按 GameConfig `entities.enemies` 数组顺序各消费一次 random；`random < 0.5` 得 `direction=-1`，否则为 `1`。RESTART 从同一 Episode seed 重新初始化 PRNG，因此相同 attempt 初态相同。协议 1.0 其他规则不得隐式消费随机数；未来新增随机调用属于破坏重放的变更。

### 8.2 状态哈希

`stateHash` 是完整 `SimulationState`（不含 hash 自身）的 SHA-256 小写 64 位十六进制。序列化规则固定为：

1. 只包含第 4 节字段，不含 Observation、lastAction、展示文本或资源 URL。
2. 对象 key 按 Unicode code point 递归升序；数组保留协议规定顺序。
3. 所有数值均为十进制整数；字符串使用标准 JSON 转义；无多余空白；编码为 UTF-8。
4. `null` terminationReason 必须参与 hash。

StepResult 同时携带前后 hash，使重放器可以逐 step 定位首次分叉。任何实现只要给定相同规范 GameConfig、configDigest、seed、maxSteps、初态和已接受动作序列，就必须得到逐步相同的完整状态、事件、终止原因和 hash。

## 9. Observation 与 Persona 边界

`SimulationState` 永远只在可信 Core、Adapter、Runner、重放器和 Evaluator 内部可用。Player Persona 只能收到由纯函数 `projectObservation(state, policy, lastAction)` 生成的 Observation；投影不得改变状态、PRNG 或 hash。

所有 Observation 至少包含：

```text
protocolVersion, episodeId, kind, step, attempt,
elapsedMs, remainingMs, stateHash,
status, terminationReason,
player { position{x,y}, velocity{x,y}, health, maxHealth, invulnerable },
progress { collected, target, score, exitUnlocked, restartCount },
visibleEntities[],
lastAction { type|null, accepted, code|null, scoreDelta, events[] }
```

位置和速度在 Observation 中分别使用 px 和 px/s。`invulnerable` 是布尔值，不暴露绝对无敌截止时间。

### 9.1 FULL

`{ "kind": "FULL" }` 用于确定性基线、测试、Evaluator 和调试。`visibleEntities` 包含所有 active 敌人、active 收集物、障碍和出口，带绝对位置、几何、速度以及相对玩家的 `dx/dy`；收集物不得继续作为 active 实体出现。FULL 仍不暴露 PRNG 内部值、未规范化 GameConfig、密钥或服务端元数据。

### 9.2 PERSONA

```json
{ "kind": "PERSONA", "visionRadiusPx": 240 }
```

`visionRadiusPx` 必须为整数 `1..2000`，属于 Episode/Persona policy，不是 GameConfig 字段。圆实体按中心距离、矩形按玩家中心到矩形最近点距离判断可见，距离小于或等于半径即纳入。PERSONA：

- 只包含视野内 active 实体；按 `enemy`、`collectible`、`obstacle`、`exit` 的类型顺序及 ID 升序稳定排序；
- 可见实体提供相对坐标；为执行移动可同时提供量化到 1px 的绝对坐标；
- 不暴露 seed、configDigest、敌人巡逻 origin/range、视野外实体、已收集物坐标或完整障碍图；
- 始终保留剩余时间、生命、进度、出口是否解锁、终止状态和最近动作结果，避免产生协议外隐藏终止；
- `stateHash` 只作为不透明重放标识，不得被解释为状态特征。

Observation 投影策略和版本必须随 Episode 轨迹保存。策略改变不能用于与旧轨迹直接比较，除非重新投影同一完整状态轨迹。

## 10. 遥测映射

协议不得改名或新增现有试玩遥测事件。允许事件仍严格为：

`SESSION_STARTED`、`ITEM_COLLECTED`、`PLAYER_HIT`、`GAME_WON`、`GAME_LOST`、`SESSION_RESTARTED`、`SESSION_ENDED`。

Core 规则事件到遥测的映射为：

| 条件 | 事件 |
|---|---|
| Episode 初始化 | `SESSION_STARTED` |
| 接受 RESTART | `SESSION_RESTARTED` |
| 每个收集物首次收集 | `ITEM_COLLECTED`，payload 仅含 `itemId` |
| 每次实际扣血 | `PLAYER_HIT`，payload 仅含 `enemyId` |
| WON | `GAME_WON` |
| HEALTH_DEPLETED / TIME_EXPIRED | `GAME_LOST`，reason 保持对应值 |
| 宿主关闭会话 | `SESSION_ENDED` |

`MAX_STEPS` 和 `ERROR` 是 Episode 终止原因，不发明新试玩事件；Episode 持久化层单独记录。相同规则事件只能由 Core 产生一次，Phaser Adapter 和 Headless Runner 不得二次推断或重复发送。

## 11. 协议版本与兼容规则

- 版本格式为 `simulation/<major>.<minor>`。
- major 改变表示破坏性变更，包括 tick、单位、动作语义、step 顺序、碰撞、PRNG、hash 字段或终止优先级变化；消费者必须拒绝未知 major。
- minor 只允许增加经能力协商的可选字段或新 Observation 投影，不得改变已有输入对应的状态、事件或 hash。
- 生产者默认只发送双方共同支持的最高 minor。未协商的新字段不得进入 hash；当前 1.0 的协议对象是闭合对象，未知字段按非法输入处理。
- GameConfig 版本独立于 Simulation Protocol。1.0 只接受规范化 `game-config/2.0`；历史 GameConfig 必须先由现有迁移器完成迁移和校验。
- 回放必须记录 protocolVersion、Core 构建版本、configDigest、seed、maxSteps、Observation policy 和动作序列。任一项不同都不是同一重放条件。

## 12. 不变量与验收

每个已接受 step 后必须满足：

- 所有权威坐标、速度和时间均为安全整数；实体身体位于世界边界内且不穿透障碍；
- `0 <= health <= maxHealth`、`remainingMs >= 0`；
- `collectedIds` 唯一，且与 inactive 收集物集合一致；
- `score` 等于已收集物分数之和，加上仅在 WON 时出现一次的 winBonus；
- `exitUnlocked == (collectedIds.length >= targetCollectibles)`；
- TERMINATED 必有且只有一个 terminationReason，终态速度为 0，之后除拒绝请求外不可变化；
- 同一输入逐 step hash、事件顺序和终止原因一致。

规范示例：

- [`examples/simulation/ongoing-step.json`](examples/simulation/ongoing-step.json)：合法移动后的进行中 Persona Observation；
- [`examples/simulation/won-step.json`](examples/simulation/won-step.json)：收集目标后接触出口并获胜；
- [`examples/simulation/invalid-action.json`](examples/simulation/invalid-action.json)：未知动作被拒绝且状态/hash 不变。

后续 V4-04 实现必须以这些语义为测试依据；V4-06 和 V4-07 只能调用同一 Core。若 Phaser 的历史细节与本 RFC 冲突，必须通过 Adapter 迁移与回归测试处理，不得在两个宿主中保留两套规则。
