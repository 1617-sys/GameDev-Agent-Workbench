# V3-00：轻量游戏原型 RFC

> 状态：`PROPOSED`（实现契约已冻结，等待人工审查后标记 `ACCEPTED`）
>
> 前置任务：V2.1 F1-F4 验收完成
>
> 目标版本：项目 `V3.0`，GameConfig 契约 `2.0`
>
> 规范示例：[`examples/game-config-2.0`](examples/game-config-2.0)

## 1. 决策摘要

V3.0 只交付一个正式模板 `arcade_collect`：玩家在单场景中移动、收集目标、躲避巡逻敌人，并在目标达成后到达出口。正式输入和持久化格式只有 GameConfig `2.0`；`top_down_collect` 只作为历史 `1.0` Artifact 的迁移输入。

本 RFC 冻结以下决策：

- GameConfig 先校验原始结构，再迁移或补充明确标为可选的叶子默认值；禁止把缺失的必需结构补成成功。
- Python 只生成规范 JSON 对象；Java 是持久化与可玩资格的权威校验方；Vue/Phaser 只消费 Java 已验证并规范化的 `2.0` Artifact。
- `PrototypeVersion` 是不可变快照。AI 重新生成或人工调参都创建新版本，不能覆盖已有版本。
- `PlaytestSession` 绑定唯一 PrototypeVersion；Telemetry 事件为受限、批量、幂等的事实记录，聚合结果由服务端计算。
- 版本创建、Telemetry 批次和导出请求都使用数据库唯一约束兜底的幂等语义。
- AI 输出、GameConfig、Telemetry 和导出内容都不能携带或执行脚本、HTML、远程 URL、本地路径和任意客户端对象。

## 2. 产品边界

### 2.1 范围

- 从 Prototype Brief 生成概念、核心循环和可玩的 `arcade_collect` 配置。
- 在 Phaser 3 中提供开始、暂停、重开、胜负、生命、计分、倒计时、键盘和触摸控制。
- 保存不可变版本，允许调整时限、玩家速度、生命、敌人数量/速度和收集目标数量。
- 采集最小试玩事件，按版本展示通关率、耗时、得分、受击、失败和重试指标。
- 基于已保存版本、Artifact 和指标生成平衡建议及可离线运行的导出包。

### 2.2 非目标

- 不加入第二模板、Galgame、复杂战斗、剧情、多关卡、高级寻路、实时多人或排行榜。
- 不执行模型生成的 JavaScript、HTML、Shader、Phaser Scene 或其他代码。
- 不接受模型提供的远程资源 URL、本地文件路径或 data URL。
- 不实现通用关卡编辑器、任意 JSON 编辑器、AI 图片生成或微信原生包。
- 不改变 V2 的 Workflow、Outbox、MQ、SSE、重试和 Artifact 既有语义。

## 3. GameConfig 2.0

### 3.1 处理顺序与规范形式

```text
AI/历史 Artifact 原始数据
-> 解开已登记的传输 wrapper（不属于 GameConfig）
-> 按声明版本校验原始结构
-> 1.0 时执行一次确定性迁移
-> 按 2.0 Schema 校验类型、上下限、白名单和跨字段规则
-> 生成 canonical JSON 并计算 SHA-256
-> Java 保存 game-config/2.0 Artifact 并授予 Runtime eligibility
-> Vue/Phaser 只读取该 canonical Artifact
```

Canonical JSON 使用 UTF-8、对象键字典序、数组保持语义顺序、数字使用 JSON 十进制最短表示、无无意义空白。`configDigest` 为 canonical JSON 字节的 lowercase SHA-256。GameConfig 根对象及所有子对象执行 `additionalProperties: false`；未知字段一律拒绝。

传输层可在校验前兼容当前已测试 wrapper：`game_config`、`gameConfig`、`data`、`raw_result.game_config`、`rawResult.gameConfig`。wrapper 不得写入 Artifact，也不得递归超过 4 层。GameConfig `2.0` 本身不接受字段 alias。

### 3.2 通用类型

| 类型 | 契约 |
| --- | --- |
| `Id` | 字符串，`^[a-z][a-z0-9-]{0,31}$`，同一数组内唯一 |
| `ResourceKey` | 字符串，只能取 3.8 节白名单值 |
| `Color` | 小写或大写 `#RRGGBB`，持久化统一为大写 |
| `Text80` | UTF-8 文本，trim 后 1-80 个 Unicode code point；不得含控制字符、`<` 或 `>` |
| `Text160` | 与 `Text80` 相同，但上限 160 |
| `Coordinate` | 有限 JSON number；满足对应几何体完整位于 world 内 |

所有数值必须是 JSON number，不接受数字字符串、`NaN` 和无穷值。所有整数不能有小数部分。边界均为闭区间。

### 3.3 根结构与元数据

十个根结构全部必需；缺少任意一个都必须在默认值归一化前拒绝。

| 路径 | 类型/范围 | 必需 | 默认值 | 消费方 |
| --- | --- | --- | --- | --- |
| `metadata.schemaVersion` | 常量 `"2.0"` | 是 | 无 | Python 输出、Java Schema、Artifact metadata、Vue 路由 |
| `metadata.gameType` | 常量 `"arcade_collect"` | 是 | 无 | Java capability、Phaser Scene 选择 |
| `metadata.title` | `Text80` | 是 | 无 | 版本列表、Runtime 标题、导出 |
| `metadata.seed` | integer，0-2147483647 | 是 | 无 | Phaser 确定性 RNG、复现和版本对比 |
| `viewport.width` | integer，640-1280 | 是 | 无 | Phaser canvas |
| `viewport.height` | integer，360-720 | 是 | 无 | Phaser canvas |
| `viewport.scaleMode` | 常量 `"fit"` | 是 | 无 | Phaser Scale Manager |

`viewport.width / viewport.height` 必须在 16:9 的 1% 误差内。V3.0 不支持竖屏配置；移动端由 `fit` 缩放和外层触摸控件适配。

### 3.4 World 与 Player

| 路径 | 类型/范围 | 必需 | 默认值 | 消费方 |
| --- | --- | --- | --- | --- |
| `world.width` | integer，等于 `viewport.width` | 是 | 无 | 物理世界和相机边界 |
| `world.height` | integer，等于 `viewport.height` | 是 | 无 | 物理世界和相机边界 |
| `world.spawn.x/y` | `Coordinate` | 是 | 无 | 玩家出生点 |
| `world.obstacles` | array，0-16 个 `Obstacle` | 是 | 无；空数组合法 | 障碍渲染与碰撞 |
| `world.obstacles[].id` | `Id` | 是 | 无 | 校验、调试和遥测关联 |
| `world.obstacles[].x/y` | `Coordinate`，中心点 | 是 | 无 | Phaser static body |
| `world.obstacles[].width/height` | integer，24-320 | 是 | 无 | Phaser static body |
| `world.obstacles[].spriteKey` | obstacle `ResourceKey` | 是 | 无 | 资源加载器 |
| `player.speed` | integer，80-400 px/s | 是 | 无 | 移动系统、调参 |
| `player.size` | integer，24-64 px | 是 | 无 | 渲染与碰撞体 |
| `player.maxHealth` | integer，1-5 | 是 | 无 | 生命与失败状态机、调参 |
| `player.hitInvulnerabilityMs` | integer，0-3000 | 是 | 无 | 受击保护计时器 |
| `player.spriteKey` | player `ResourceKey` | 是 | 无 | 资源加载器 |

出生碰撞体与障碍、敌人、收集物、出口不得相交。障碍必须完整位于 world 内；Java Rule 校验至少使用矩形/圆形包围盒证明所有必需实体可放置，Runtime smoke test 再证明存在可玩路径。

### 3.5 Entities 与 Behaviors

| 路径 | 类型/范围 | 必需 | 默认值 | 消费方 |
| --- | --- | --- | --- | --- |
| `entities.collectibles` | array，1-20 个 `Collectible` | 是 | 无 | 收集与计分系统 |
| `entities.collectibles[].id` | `Id` | 是 | 无 | 去重、目标与事件关联 |
| `entities.collectibles[].x/y` | `Coordinate` | 是 | 无 | 出生位置 |
| `entities.collectibles[].size` | integer，12-48 px | 是 | 无 | 渲染与碰撞体 |
| `entities.collectibles[].score` | integer，1-1000 | 是 | 无 | 服务端可复算计分 |
| `entities.collectibles[].label` | `Text80` | 是 | 无 | HUD 和无障碍文案 |
| `entities.collectibles[].spriteKey` | collectible `ResourceKey` | 是 | 无 | 资源加载器 |
| `entities.enemies` | array，0-12 个 `Enemy` | 是 | 无；空数组合法 | 敌人系统 |
| `entities.enemies[].id` | `Id` | 是 | 无 | behavior 与事件关联 |
| `entities.enemies[].x/y` | `Coordinate` | 是 | 无 | 出生位置 |
| `entities.enemies[].size` | integer，24-64 px | 是 | 无 | 渲染与碰撞体 |
| `entities.enemies[].speed` | integer，20-240 px/s | 是 | 无 | 巡逻系统、调参 |
| `entities.enemies[].spriteKey` | enemy `ResourceKey` | 是 | 无 | 资源加载器 |
| `entities.exit.x/y` | `Coordinate`，中心点 | 是 | 无 | 出口碰撞区 |
| `entities.exit.width/height` | integer，32-160 px | 是 | 无 | 出口碰撞区 |
| `entities.exit.label` | `Text80` | 是 | 无 | HUD |
| `entities.exit.spriteKey` | exit `ResourceKey` | 是 | 无 | 资源加载器 |
| `behaviors.enemyPatrols` | array，数量等于 enemies | 是 | 无；无敌人时为空 | 巡逻系统 |
| `behaviors.enemyPatrols[].enemyId` | 引用唯一 enemy id | 是 | 无 | behavior 绑定 |
| `behaviors.enemyPatrols[].axis` | `"x" \| "y"` | 是 | 无 | 巡逻方向 |
| `behaviors.enemyPatrols[].distance` | integer，32-480 px | 是 | 无 | 巡逻端点 |
| `behaviors.contact.damage` | integer，1-5 | 是 | 无 | 受击与生命系统 |

每个 enemy 必须且只能有一个 patrol；巡逻线段和实体碰撞体必须完整位于 world 内。收集物 id 与 enemy id 分属不同命名空间，但建议分别使用 `item-` 与 `enemy-` 前缀。出口、收集物和敌人不得与障碍重叠。

### 3.6 Objectives、Balance、Presentation

| 路径 | 类型/范围 | 必需 | 默认值 | 消费方 |
| --- | --- | --- | --- | --- |
| `objectives.targetCollectibles` | integer，1 到 collectibles 数量 | 是 | 无 | 出口解锁、HUD、调参 |
| `objectives.winCondition` | 常量 `"collect_target_then_exit"` | 是 | 无 | Runtime 胜利状态机 |
| `objectives.loseConditions` | 非空唯一数组，取 `"health_depleted"`、`"time_expired"` | 是 | 无 | Runtime 失败状态机 |
| `balance.timeLimitSeconds` | integer，30-600 | 是 | 无 | 倒计时、指标、调参 |
| `balance.winBonus` | integer，0-10000 | 是 | 无 | 服务端可复算计分 |
| `balance.difficulty` | `"easy" \| "normal" \| "hard"` | 是 | 无 | 版本展示、评测分组 |
| `presentation.palette.floor/wall/player/item/enemy/exit` | `Color` | 是 | 无 | Phaser 渲染 |
| `presentation.audio.collect/hit/win/lose` | sound `ResourceKey` | 是 | 无 | 资源加载器与状态反馈 |
| `presentation.ui.objective` | `Text160` | 是 | 无 | HUD |
| `presentation.ui.controls` | `Text160` | 是 | 无 | 开始/暂停界面 |

计分公式固定为：本次 attempt 中首次收集的目标 `score` 之和，胜利时再加 `winBonus`。客户端不得提交可信的总分或增量；Java 根据事件与版本配置复算。`loseConditions` 的数组顺序不影响语义，canonical 化时按上述枚举顺序排序。

### 3.7 Telemetry 声明

| 路径 | 类型/范围 | 必需 | 默认值 | 消费方 |
| --- | --- | --- | --- | --- |
| `telemetry.events` | 下列 7 个常量组成的唯一数组 | 是 | 无 | Runtime 上报 allow-list、Java ingestion |

必须恰好声明：`SESSION_STARTED`、`ITEM_COLLECTED`、`PLAYER_HIT`、`GAME_WON`、`GAME_LOST`、`SESSION_RESTARTED`、`SESSION_ENDED`。该字段让 Runtime 明确只产生协议允许的事件，不能增加自定义事件名。

### 3.8 内置资源白名单

GameConfig 只保存 key，不保存 URL、路径、base64 或 manifest 内容。V3-02 可为 key 绑定仓库内资源；加载失败使用同类别内置几何占位，不改变 key 或配置摘要。

| 类别 | 允许值 |
| --- | --- |
| player | `player.blue`, `player.green` |
| collectible | `collectible.gem`, `collectible.artifact`, `collectible.core` |
| enemy | `enemy.guard`, `enemy.drone` |
| exit | `exit.portal`, `exit.door` |
| obstacle | `obstacle.stone`, `obstacle.metal`, `obstacle.wood` |
| sound | `sfx.collect`, `sfx.hit`, `sfx.win`, `sfx.lose`, `sfx.silent` |

资源白名单属于 Runtime capability `arcade-collect-runtime/1`。新增 key 是 capability 的向后兼容小版本变更；删除或改变 key 语义是破坏性变更，必须保留旧资源直到对应 PrototypeVersion 的保留期结束。

### 3.9 合法与非法示例

- 唯一规范合法样例：[`valid-minimal.json`](examples/game-config-2.0/valid-minimal.json)。Python Prompt、Java、Vue 和文档测试必须直接读取或机械复制校验该文件，不得另写语义不同的“权威示例”。
- 缺失必需结构：[`invalid-missing-entities.json`](examples/game-config-2.0/invalid-missing-entities.json)，必须报 `REQUIRED` at `$.entities`，不能由默认实体补齐。
- 非法远程资源：[`invalid-remote-resource.json`](examples/game-config-2.0/invalid-remote-resource.json)，必须报 `RESOURCE_KEY_NOT_ALLOWED` at `$.player.spriteKey`。
- 越界巡逻：[`invalid-out-of-bounds-patrol.json`](examples/game-config-2.0/invalid-out-of-bounds-patrol.json)，必须报 `WORLD_BOUNDS` at `$.behaviors.enemyPatrols[0].distance`。

错误格式冻结为 `{code, path, message, severity}`；`code` 与 `path` 稳定供测试和 UI 使用，`message` 可本地化。上述错误和所有 Schema/白名单错误均为 `BLOCKING`。

## 4. GameConfig 1.0 兼容与迁移

### 4.1 接受边界

只有同时满足以下条件的历史输入可进入迁移：

1. `version == "1.0"` 且 `gameType`（或唯一 alias `game_type`）为 `top_down_collect`。
2. 在任何默认值应用前，通过当前 1.0 必需结构校验：`title`、`world`、`player`、`items|collectibles`、`enemies`、`exit`、`rules`、`ui` 存在且类型正确，必需坐标为有限数字。
3. wrapper 和字段 alias 只允许 3.1 及 4.2 明列的集合；未知模板、`winCondition` 根对象、脚本、HTML、URL、路径和未知字段拒绝。
4. 迁移后完整通过 2.0 Schema、规则与 Runtime capability 校验；不能迁移的输入仍可作为不可玩的历史证据展示原文，但不得进入 Runtime。

`obstacles` 是 1.0 唯一可缺省的结构，缺失时迁移为空数组。1.0 的纯视觉叶子可按 4.2 的固定值迁移；任何 gameplay 必需数据不得猜测。

### 4.2 确定性映射

| 1.0 输入 | 2.0 输出 | 规则 |
| --- | --- | --- |
| `version` | `metadata.schemaVersion` | `1.0` 改为 `2.0` |
| `gameType\|game_type` | `metadata.gameType` | 只允许 `top_down_collect`，改为 `arcade_collect` |
| `title` | `metadata.title` | 原值 |
| 无 | `metadata.seed` | 对 1.0 canonical JSON 做 SHA-256，取前 8 hex 并与 `0x7fffffff` 按位与 |
| `world.width/height` | `viewport.width/height`、`world.width/height` | 必须满足 2.0 viewport 边界和比例，否则拒绝 |
| 无 | `viewport.scaleMode` | 固定 `fit` |
| `world.backgroundColor` | `presentation.palette.floor` | 合法 Color 时使用；否则使用 `#101827` |
| `player.x/y` | `world.spawn.x/y` | 原值，迁移后检查碰撞与边界 |
| `obstacles` | `world.obstacles` | 缺失变空数组；`spriteKey=obstacle.stone`；保留 id/几何 |
| `player.speed/size` | 同名字段 | `size` 缺失用 28；越界拒绝而非截断 |
| 无 | `player.maxHealth`、`hitInvulnerabilityMs` | `loseCondition=touch_enemy` 时固定 1、0 |
| 无 | `player.spriteKey` | 固定 `player.blue` |
| `items\|collectibles` | `entities.collectibles` | `size` 缺失用 18，`score=100`，`spriteKey=collectible.gem`；其余保留 |
| `enemies` | `entities.enemies` | `size` 缺失用 28，`spriteKey=enemy.guard`；保留 id/坐标/speed |
| `enemy.axis\|patrolAxis` | `behaviors.enemyPatrols[].axis` | 两者同时存在且冲突则拒绝 |
| `enemy.range\|patrolDistance` | `behaviors.enemyPatrols[].distance` | 两者同时存在且冲突则拒绝；缺失拒绝 |
| `exit` | `entities.exit` | `width=54`、`height=72`、`label=EXIT` 只补缺失叶子；`spriteKey=exit.door` |
| `rules.targetItems` | `objectives.targetCollectibles` | 必须为 1..items.length |
| `rules.winCondition` | `objectives.winCondition` | 只接受 `collect_all_then_exit`，改为 `collect_target_then_exit` |
| `rules.loseCondition` | `objectives.loseConditions` | 只接受 `touch_enemy`，改为 `[health_depleted]` |
| 无 | `balance` | `timeLimitSeconds=90`、`winBonus=500`、`difficulty=normal` |
| `theme.palette` | `presentation.palette` | 同名六色覆盖；缺色使用 1.0 文档示例的固定六色 |
| 无 | `presentation.audio` | collect/hit/win/lose 对应同名 `sfx.*` |
| `ui.objective` | `presentation.ui.objective` | 原值 |
| `ui.controls\|controlHint` | `presentation.ui.controls` | 两者同时存在且冲突则拒绝 |
| 无 | `telemetry.events` | 固定 3.7 的七事件集合 |

### 4.3 持久化与废弃

- 历史 `game-config/1.0` Artifact 永不原地修改；只读预览可在内存迁移，但页面必须标明“由 1.0 临时迁移”。
- 创建 PrototypeVersion 时必须保存一个新的 canonical `game-config/2.0` Artifact，并记录源 Artifact UUID 与源摘要；版本只引用 2.0 Artifact。
- 新的 Python 输出、新工作流和人工调参从 V3-01 起只允许写 `2.0/arcade_collect`。
- 1.0 兼容读取至少保留到 V3 系列结束。删除迁移器前必须统计连续 90 天无 1.0 读取，并完成离线迁移或明确归档；不得静默删除。
- 2.0 不接受 1.0 alias。alias 只存在于独立迁移器，避免长期污染正式 Schema。

## 5. PrototypeVersion 最小领域契约

| 字段 | 契约与消费方 |
| --- | --- |
| `versionUuid` | 服务端 UUID；API、Telemetry、导出和父子关系的外部标识 |
| `projectId` | 服务端内部 FK；所有权与隔离，不信任客户端 |
| `versionNumber` | 每项目从 1 连续递增的正整数；列表与对比展示 |
| `parentVersionUuid` | 首版本为 null；此后必须指向同项目已有版本；调参谱系 |
| `source` | `AI_GENERATED \| TUNED`；来源展示与审计 |
| `gameConfigArtifactUuid` | 唯一、已通过三层校验的 `game-config/2.0` Artifact |
| `configDigest` | 与 Artifact canonical 内容一致的 SHA-256；完整性、幂等和导出 |
| `runtimeCapabilityVersion` | 固定创建时使用的 capability，例如 `arcade-collect-runtime/1` |
| `createdBy` | 认证用户内部 id；审计，不对其他项目暴露 |
| `createdAt` | 服务端 UTC 时间；排序和审计 |

整行及其引用的 GameConfig 内容创建后不可更新或软删除。V3.0 不在 PrototypeVersion 内设计可变发布状态；如未来需要发布生命周期，应建立独立记录，不能修改版本快照。

创建版本的幂等范围为 `(userId, projectId, CREATE_PROTOTYPE_VERSION, Idempotency-Key)`，key 规则复用现有 1-128 URL-safe 约束。请求指纹包含 source、parentVersionUuid、源 Artifact/config 或调参白名单值。相同 key 和相同指纹返回原 `versionUuid`；相同 key 不同指纹返回 `409 IDEMPOTENCY_KEY_CONFLICT`；失败重试不得分配新逻辑版本。

并发创建在数据库事务内锁定项目版本序列（或使用等价原子序列），分配 `max + 1`，并以唯一约束 `(project_id, version_number)` 和幂等唯一约束兜底。唯一冲突只可重读相同幂等结果或有限重试序列分配，不能返回两个版本。

调参 API 只接受以下白名单叶子：`balance.timeLimitSeconds`、`player.speed`、`player.maxHealth`、`objectives.targetCollectibles`、`entities.enemies` 数量及各 enemy 的 `speed`。服务端从父版本构建完整候选配置、重新校验并保存新 Artifact；客户端不能提交 JSON Patch 或替换完整配置。

## 6. PlaytestSession 与 Telemetry

### 6.1 会话

| 字段 | 契约 |
| --- | --- |
| `sessionUuid` | 服务端 UUID，外部标识 |
| `userId/projectId` | 从认证与版本关系得到，不采信请求体 |
| `prototypeVersionUuid` | 必须属于该项目；创建后不可改变 |
| `status` | `ACTIVE \| ENDED`；服务端状态 |
| `startedAt/endedAt` | 服务端 UTC 时间；endedAt 仅 ENDED 非空 |
| `lastSequence` | 已见最大序号，只用于接收窗口，不代表无缺口 |
| `eventCount` | 已去重原始事件数，最大 1000 |
| `outcome` | `WON \| LOST \| ABANDONED \| NONE`，按有序事件派生 |
| `score/durationMs/hitCount/collectedCount/restartCount` | 服务端按配置和事件派生，不接收客户端汇总 |

一个 session 表示一次打开 Runtime，可包含多个 attempt。`SESSION_RESTARTED` 结束当前 attempt 并开始下一个；`SESSION_ENDED` 结束 session。会话最长 30 分钟，超时由服务端关闭为 `ABANDONED`。

### 6.2 批次与事件 envelope

批次请求包含 `batchUuid`、`sessionUuid` 和 `events`。`batchUuid` 为 UUID；`events` 为 1-50 项；解压后的 JSON 请求体不超过 64 KiB；每 session 最多 1000 个事件。限流基线为每用户每分钟 60 批、每 session 每分钟 30 批、每用户每小时 20 个新 session；实现可收紧但不得放宽而不更新 RFC。

每个事件只能包含：

| 字段 | 契约 |
| --- | --- |
| `eventUuid` | UUID；全局事件去重键 |
| `sequence` | integer，1-1000；session 内唯一 |
| `type` | 3.7 的七个事件之一 |
| `clientElapsedMs` | integer，0-1800000；相对 session 开始，不能代替服务端时间 |
| `payload` | 对应事件的封闭对象；禁止额外字段 |

Payload 冻结为：

| type | payload | 规则 |
| --- | --- | --- |
| `SESSION_STARTED` | `{}` | 必须是 sequence 1 且 elapsed 0；仅一次 |
| `ITEM_COLLECTED` | `{itemId}` | itemId 必须存在；同 attempt 同 item 只计一次 |
| `PLAYER_HIT` | `{enemyId}` | enemyId 必须存在；生命与得分由服务端推导 |
| `GAME_WON` | `{}` | 目标已满足且到达出口的 Runtime 事实；服务端复算目标/分数 |
| `GAME_LOST` | `{reason}` | `reason=HEALTH_DEPLETED \| TIME_EXPIRED`，须与可复算状态一致 |
| `SESSION_RESTARTED` | `{}` | 仅可跟在进行中或 GAME_WON/GAME_LOST attempt 后；服务端增加 attempt |
| `SESSION_ENDED` | `{reason}` | `reason=COMPLETED \| USER_EXIT \| PAGE_HIDDEN \| TIMEOUT`；session 最终事件 |

不得包含键盘/鼠标轨迹、IP、User-Agent、设备指纹、自由文本、Prompt、Token、Cookie、任意 metadata、客户端对象或客户端计算的总分。网络层常规安全日志按现有平台策略处理，不能复制进 Telemetry payload。

### 6.3 幂等、乱序和聚合

- 批次幂等唯一约束为 `(session_id, batch_uuid)`，同时存 canonical batch SHA-256。相同 UUID/摘要重放返回首次结果；不同摘要返回 409。
- 事件以 `eventUuid` 全局唯一，并以 `(session_id, sequence)` 唯一。完全相同的重复事件不重复计数；同 UUID 或 sequence 内容冲突时整个批次拒绝。
- 单批在一个事务中原子校验和写入；不得部分接受。批内先按 sequence 排序，再和已有事件合并验证状态机。
- 允许 sequence 有缺口和批次乱序。收到 `SESSION_ENDED` 后保留 60 秒补交窗口，只接受 sequence 小于结束事件 sequence 的缺口事件；窗口后 session 封闭，任何新事件拒绝。聚合每次按 sequence 全量或等价增量重算，结果必须一致。
- 同一 sequence 的 `clientElapsedMs` 必须不小于前序事件且不大于后序事件；违反则拒绝。服务端接收时间只用于审计、超时和限流。
- 原始事件在线保留 30 天后删除；session 摘要和按版本聚合保留到项目删除。删除遵循项目级数据删除策略。AI 只读取版本配置和至少 5 个已结束 session 的聚合数据；样本不足必须标记，不得读取原始事件或用户标识。

## 7. 导出幂等语义

导出请求绑定 `prototypeVersionUuid`，幂等范围为 `(userId, projectId, EXPORT_PROTOTYPE, Idempotency-Key)`。首次接受时冻结输入清单：版本 UUID/configDigest、Artifact UUID/摘要、资源 manifest 版本、试玩聚合的 `snapshotAt` 与摘要、导出格式版本。请求指纹取该清单的 canonical SHA-256。

- 同 key/同指纹返回同一个 export job 和文件；同 key/不同指纹返回 409。
- 重试失败 job 复用冻结输入，不重新调用 AI、不重新抓取最新聚合、不创建语义不同的包。
- 新 key 可创建包含更新后试玩快照的新导出，必须产生不同 manifest 摘要。
- ZIP 条目按路径排序，时间戳统一使用 PrototypeVersion `createdAt`，文本 UTF-8/LF；manifest 记录每个文件 SHA-256。相同冻结输入必须得到相同内容摘要。
- 失败不得发布半成品；临时文件清理后 job 保留可读错误。路径穿越、符号链接、密钥、Token、内部日志、数据库地址和非白名单资源均阻止导出。

## 8. 并发、失败与安全策略

| 场景 | 冻结行为 |
| --- | --- |
| Python 输出非法 JSON/缺结构 | 工作流失败并保存受控原文与校验报告；不得调用 Python 默认配置伪装成功 |
| Java Schema/Rule/capability 失败 | Artifact 保留为不可玩证据，`runtimeEligible=false`；不得创建 PrototypeVersion |
| Vue 收到未经验证或摘要不符配置 | 不 mount Runtime，展示稳定错误码和重试入口 |
| 资源加载失败 | 使用同类别本地几何/静音占位，记录非敏感 warning；玩法继续 |
| 并发版本创建 | 数据库序列与唯一约束决定唯一编号；幂等重放返回同一版本 |
| Telemetry 重放/乱序/超限 | 按 6.3 去重或原子拒绝；不重复聚合 |
| SSE/页面断开 | 不影响后台生成、版本或导出；通过持久化快照恢复 |
| 导出失败 | 对同一冻结输入可重试；不重新调用模型，不暴露半包 |

所有写 API 必须认证并由服务端验证 user-project-version-session 归属。日志仅记录内部 id、稳定错误码、trace id 和摘要，不记录 GameConfig 自由文本、Telemetry payload、Prompt 或凭证。展示文案用文本节点，禁止 `v-html`。JSON 解析后只进入白名单 DTO，不能 `eval`、动态 import、拼接选择器或脚本。

## 9. 当前字段错位与统一矩阵

| 当前来源 | 当前字段/行为 | 2.0 唯一字段/行为 | V3-01 处理 |
| --- | --- | --- | --- |
| Python Prompt/normalize | `version`, `top_down_collect` | `metadata.schemaVersion`, `arcade_collect` | Prompt 直接生成 2.0；删除新输出默认补全 |
| Java Hook/Registry | 只接受 `top_down_collect`，schema `1.0` | 只对新写入接受 `arcade_collect`, `2.0` | 增加独立 1.0 migrator；更新 capability |
| Vue default/runtime | 根 `title`、`world.backgroundColor` | `metadata.title`、`presentation.palette.floor` | Runtime 改读 2.0；默认配置只作显式 demo fixture |
| Python/Java/Vue | `items` 或 `collectibles` | `entities.collectibles` | alias 仅在 1.0 migrator |
| Python/Vue | 根 `obstacles` | `world.obstacles` | 统一迁移并消费 |
| Python/Vue | player 内 `x/y` | `world.spawn.x/y` | 统一迁移并消费 |
| Python/Vue | enemy `axis/range` | `behaviors.enemyPatrols[].axis/distance` | 行为与实体拆分，按 enemyId 引用 |
| Python 历史 fallback | `patrolAxis/patrolDistance` | 同上 | 只作为 1.0 alias，冲突拒绝 |
| seed SQL | `collectibles`, 根 `winCondition` | `entities.collectibles`, `objectives` | seed Prompt 改读唯一规范样例 |
| Java rule | items/enemies 仅检查 id | 完整数量、几何、引用、白名单和边界 | 按本 RFC 增加稳定错误码 |
| Vue/Python | `theme.palette` | `presentation.palette` | 2.0 不保留 theme alias |
| Vue/Python | `ui.controls/controlHint` | `presentation.ui.controls` | controlHint 仅为 1.0 alias |
| 1.0 rules | `targetItems/winCondition/loseCondition` | `objectives.*` | 按 4.2 显式映射；不接受根 winCondition |
| Python parser | JSON 失败后 `{}` + 默认可玩配置 | 原始输出失败 | 删除“补成成功”；保存 validation failure |
| Vue normalize | 必需数组缺失时 defaultGameConfig | 只允许合法后的可选资源降级 | 校验必须先于任何 normalize |
| 文档/各语言测试 | 各自复制示例 | `examples/game-config-2.0/valid-minimal.json` | 测试直接读取或由脚本机械同步 |

## 10. 实施顺序与责任

```text
V3-01 shared fixture + Python output
-> Java 1.0 migrator / 2.0 Schema / Rule / capability
-> Vue extraction / validation / typed consumer
-> cross-language contract tests
-> V3-02 Phaser state machine and resource manifest
-> V3-03 workflow Artifact integration
-> V3-04 immutable PrototypeVersion
-> V3-05 PlaytestSession / Telemetry / aggregation
-> V3-06 deterministic export and release acceptance
```

| 层 | 生成/校验/消费责任 | 禁止事项 |
| --- | --- | --- |
| Python | 仅生成直接的 2.0 JSON；使用规范样例约束 Prompt | 不补全缺失必需结构，不决定 runtime eligibility |
| Java | 解 wrapper、迁移 1.0、完整校验、canonicalize、摘要、持久化和幂等 | 不信任客户端摘要、版本号、总分或资源 URL |
| Vue | 读取已验证 Artifact、显示错误、产生受限事件 | 不维护第二套 Schema，不让 default config 掩盖失败 |
| Phaser | 消费 2.0 字段、白名单资源和固定种子，发七类事件 | 不执行任意配置代码，不自行迁移历史输入 |
| 文档/测试 | 以本 RFC 和规范 JSON 为唯一事实源 | 不复制漂移的示例 |

## 11. 验收检查表

- [x] GameConfig 2.0 字段、类型、上下限、默认策略、资源白名单和规范示例完整。
- [x] Python、Java、Vue、seed Prompt、Runtime 和历史文档的已知字段错位进入统一矩阵。
- [x] 1.0 输入的接受、迁移、拒绝、只读预览和 2.0 持久化策略明确。
- [x] PrototypeVersion 不可变、父子关系、版本号并发与创建幂等明确。
- [x] Telemetry 事件 payload、去重键、乱序、批次限制、限流、保留期和隐私边界明确。
- [x] 导出冻结输入、失败重试、确定性和幂等语义明确。
- [x] 后续任务顺序和跨层责任明确，不需要重新猜测核心契约。
- [ ] 人工审查通过后将本 RFC 状态改为 `ACCEPTED`。

## 12. 验证命令

```powershell
git diff --check
rg -n "top_down_collect|arcade_collect|GameConfig" docs python-agent backend-java frontend-vue/src/features/demo/runtime
```

## 13. 完成定义

- 本文通过人工审查并标记 `ACCEPTED`。
- diff 仅包含本 RFC 与必要的合法/非法规范示例。
- 第 9 节矩阵能够解释 Python 生成、Java 校验和 Phaser 消费的一致关系。
