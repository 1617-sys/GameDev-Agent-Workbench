# V3 AI 轻量游戏原型工作台设计

> 状态：`PROPOSED`
>
> 前置版本：完成 V2.1 前端产品化 F1-F4，并形成可运行的稳定基线
>
> 目标版本：项目 `V3.0`，GameConfig 契约 `2.0`
>
> 核心原则：单模板做深、配置驱动、可试玩、可评测、可导出

## 1. 版本定位

V2 解决了 AI 工作流的工程化问题：Java 异步编排、RabbitMQ、Outbox、幂等、恢复、SSE、RAG、评测和可观测性。V2.1 负责补齐普通用户可使用的前端入口。

V3 不建设通用游戏引擎，而是把现有“AI 生成参数后套入固定 Phaser 模板”的 Demo，升级为真正能辅助游戏开发的轻量原型工具：

> 将一句游戏创意转化为一个 3-10 分钟可试玩、可调参、可评测、可复现、可导出的 H5 小游戏垂直切片。

V3 只支持一个正式模板：

```text
arcade_collect
```

它是现有 `top_down_collect` 的兼容升级：玩家在小型场景中移动、收集目标、躲避敌人，并在条件满足后到达出口。模板应能表达“夺宝、逃脱、清理、限时收集”等轻量主题，但不允许 AI 生成任意脚本。

## 2. 用户价值

### 2.1 目标用户

- 想快速验证玩法的独立开发者或学生。
- 需要把概念转成可试玩原型的游戏策划。
- 需要获得结构化需求和开发任务的程序员。
- 面试中需要展示 AI 工程化完整链路的 Java 开发者。

### 2.2 核心使用场景

用户输入：

```text
做一个 90 秒的博物馆夺宝小游戏。玩家需要拿到三件藏品，
避开巡逻守卫，然后从右下角出口离开。
```

系统交付：

1. 游戏概念和核心循环说明。
2. 经过验证的 GameConfig 2.0。
3. 可在桌面和手机浏览器试玩的 Phaser Demo。
4. 可调整的难度参数和不可变的原型版本。
5. 通关时间、失败、重试和得分等试玩数据。
6. 基于数据生成的平衡建议。
7. 可下载的原型包和后续开发任务清单。

## 3. 完整业务链路

```text
注册/登录
-> 创建或选择项目
-> 填写 Prototype Brief
-> Java 提交异步 AI 工作流
-> Python Agent 生成概念、核心循环和 GameConfig 2.0
-> Java 进行 Schema、规则和 Runtime 能力校验
-> 保存不可变 PrototypeVersion 与 Artifact
-> Phaser Runtime 加载配置和白名单资源
-> 用户试玩并上报 Telemetry
-> Java 聚合试玩指标
-> AI 给出平衡建议
-> 用户调整白名单参数并创建新版本
-> 对比版本并导出 Prototype Package
```

## 4. V3 功能范围

### 4.1 Prototype Brief

用户只填写对游戏设计有意义的信息：

- 游戏主题与一句话创意。
- 目标时长，默认 90 秒。
- 难度：简单、普通、困难。
- 视觉主题：地牢、森林、太空、博物馆等有限选项。
- 可选补充要求。

项目 UUID、工作流 Key、Schema 版本、幂等键均由系统管理。

### 4.2 GameConfig 2.0

GameConfig 2.0 是 Python Agent、Java 校验器和 Phaser Runtime 的唯一执行契约，建议结构为：

```text
metadata       版本、标题、模板、随机种子
viewport       画布尺寸、方向、缩放策略
world          场景边界、障碍、出生点
player         移动、生命和可用动作
entities       收集物、敌人和出口
behaviors      敌人巡逻和接触行为
objectives     目标、胜利和失败条件
balance        时限、分数和难度参数
presentation   白名单精灵、颜色、音效和 UI 文案
telemetry      允许采集的试玩事件
```

契约要求：

- 校验必须早于默认值归一化。
- Python Prompt、Java Schema/规则校验、前端类型与示例使用同一字段。
- `top_down_collect` 仅作为 1.0 迁移输入；持久化新版本统一为 `arcade_collect`。
- 所有坐标、速度、数量、时长和资源 key 有明确上下限。
- 不接受 JavaScript、HTML、远程资源 URL 和任意代码字段。
- 同一配置加同一随机种子必须得到可复现的初始关卡。

### 4.3 正式化 Phaser Runtime

Runtime 仍使用 Phaser 3，不替换引擎。正式 Demo 最少包含：

- 开始、暂停、继续、重新开始和结算状态。
- 键盘与触摸/虚拟摇杆操作。
- 玩家移动、生命或受击保护时间。
- 收集目标、计分、倒计时和出口解锁。
- 敌人水平或垂直巡逻，接触造成受击或失败。
- 障碍物和世界边界碰撞。
- 使用仓库内置、白名单化的精灵与基础音效。
- HUD 展示目标、得分、剩余时间和生命。
- 适配常见桌面与手机浏览器视口。
- Runtime 错误隔离：单个资源或配置错误不能拖垮整个工作台。

V3 不要求复杂地图编辑器、技能树、战斗连招、多关卡剧情和高级寻路。

### 4.4 原型版本与调参

- 每次 AI 生成或人工调参创建一个不可变 `PrototypeVersion`。
- 用户只能调整白名单参数：时限、玩家速度、敌人数量/速度、收集目标和生命值。
- 已发布或已产生试玩数据的版本不能原地覆盖。
- 两个版本可以对比配置差异和核心试玩指标。
- 并发创建版本使用幂等键，并以服务端版本号为准。

### 4.5 试玩数据与评测

采集最小且有价值的事件：

```text
SESSION_STARTED
ITEM_COLLECTED
PLAYER_HIT
GAME_WON
GAME_LOST
SESSION_RESTARTED
SESSION_ENDED
```

聚合指标：

- 试玩次数和有效会话数。
- 通关率、平均通关时间和平均得分。
- 平均失败次数、重试次数和受击次数。
- 退出原因和终局分布。

Telemetry 必须批量、幂等、限流上报。事件不包含 Token、Prompt 原文、个人敏感信息或任意客户端对象。

AI 评测只根据配置和聚合数据给出建议，不直接改写已发布版本。

### 4.6 原型包导出

最终导出一个可复现的 Prototype Package：

```text
prototype-package/
├─ game-design-brief.md
├─ core-loop.md
├─ game-config.json
├─ assets-manifest.json
├─ playtest-summary.json
├─ balance-suggestions.md
├─ development-backlog.md
└─ playable-demo/
```

导出必须基于已保存 Artifact 和 PrototypeVersion，不在下载时重新调用模型。压缩包中不得包含密钥、内部日志、数据库信息和任意未授权资源。

## 5. 系统架构

```text
Vue 3 Product UI
├─ Prototype Brief / Version Compare / Playtest Summary
└─ Phaser 3 arcade_collect Runtime
               |
               v
Java Spring Boot
├─ Existing Workflow / Outbox / MQ / SSE
├─ PrototypeVersion Service
├─ GameConfig 2.0 Validation
├─ Playtest Telemetry Ingestion + Aggregation
└─ Prototype Package Export
               |
               v
Python Agent
├─ Game concept
├─ Core loop
├─ GameConfig 2.0 structured generation
└─ Balance suggestion
```

继续使用当前 MySQL、Redis、RabbitMQ 和 Docker 基础设施。V3 默认仍是模块化单体加独立 Python Agent，不拆新微服务。

## 6. 关键数据边界

建议新增或冻结以下领域概念，最终字段在 V3-00 RFC 中确定：

### PrototypeVersion

- 所属项目和递增版本号。
- 唯一版本 UUID。
- 来源：AI 生成或人工调参。
- GameConfig Artifact UUID 与内容摘要。
- Runtime 契约版本和随机种子。
- 创建时间、状态和父版本 UUID。

### PlaytestSession

- 所属用户、项目和 PrototypeVersion。
- 会话 UUID、开始/结束时间和终局状态。
- 得分、耗时、受击、收集和重试摘要。
- 客户端事件序列去重信息。

原始事件用于短期验证，正式查询以服务端聚合结果为准。数据保留策略和最大事件数量需要在 RFC 中冻结。

## 7. 安全、并发与失败处理

- GameConfig 和资源清单均为数据，禁止执行模型输出中的代码或 HTML。
- 资源只允许引用内置 manifest key，不允许模型提供远程 URL 或本地路径。
- 原型版本不可变；调参创建子版本，防止试玩数据失去对应配置。
- 创建版本、Telemetry 批次和导出请求均需幂等。
- Telemetry 接口需要认证、项目归属校验、批次大小限制和速率限制。
- SSE 断开不影响后台任务执行；页面通过快照恢复。
- 导出失败可重试，但不能重复调用模型或产生不同内容。
- Python Agent 输出无效时，工作流明确失败并保存校验报告，不使用默认配置伪装成功。

## 8. 明确非目标

- 不做 Galgame、剧情节点、角色立绘、分支和存档系统。
- 不做 Unity、Godot 或通用游戏引擎导出。
- 不做平台跳跃、塔防、卡牌等第二种 Runtime 模板。
- 不在 V3.0 承诺微信小游戏原生包；先交付移动端 H5，微信适配留给 V3.x。
- 不做 AI 图片生成、素材训练、在线地图编辑器和任意代码生成执行。
- 不做实时多人、排行榜、支付、广告、社交和账号运营系统。
- 不为 V3 新拆微服务或引入新的消息中间件。

## 9. AI 协作与额度策略

- 一次只执行一张任务卡，任务开始时只读取卡片列出的上下文。
- `gpt-5.4` 处理 DTO、Mapper、表单、样式、测试夹具和文档等确定性工作。
- `gpt-5.5` 只用于契约设计、版本并发、Telemetry 幂等、Runtime 状态机和最终审查。
- 每张卡计划不超过 8 行，直接实现、验证、审查 diff。
- 不使用多 Agent；跨 Java、Python、Vue 的任务按契约先后顺序串行完成。
- 只运行当前模块的聚焦测试，阶段验收时再运行 quick Harness 和 Docker E2E。
- 真实踩坑追加到 `docs/PITFALLS.md`，不重复创建大段过程报告。

## 10. 阶段任务

| 任务 | 交付结果 | 预计有效工时 |
| --- | --- | ---: |
| V3-00 | 冻结产品边界、GameConfig 2.0 和数据 RFC | 4-6 小时 |
| V3-01 | Python、Java、Vue 契约统一与 1.0 迁移 | 8-12 小时 |
| V3-02 | 正式化移动端 H5 Phaser Runtime | 14-20 小时 |
| V3-03 | AI 生成链路、资源清单和 Artifact 闭环 | 8-12 小时 |
| V3-04 | 不可变原型版本、白名单调参和版本对比 | 10-14 小时 |
| V3-05 | 试玩 Telemetry、聚合指标和 AI 平衡建议 | 12-18 小时 |
| V3-06 | 原型包导出、Docker E2E 和发布验收 | 8-12 小时 |

对应任务卡：

- [V3-00：轻量游戏原型 RFC](requirements/v3/V3-00-lightweight-prototype-rfc.md)
- [V3-01：GameConfig 2.0 契约统一](requirements/v3/V3-01-game-config-v2-contract.md)
- [V3-02：Phaser H5 Runtime 正式化](requirements/v3/V3-02-phaser-h5-runtime.md)
- [V3-03：AI 生成与资源 Artifact 闭环](requirements/v3/V3-03-ai-generation-artifact.md)
- [V3-04：原型版本、调参与对比](requirements/v3/V3-04-prototype-version-tuning.md)
- [V3-05：试玩数据与平衡评测](requirements/v3/V3-05-playtest-telemetry-evaluation.md)
- [V3-06：导出与发布验收](requirements/v3/V3-06-export-release-acceptance.md)

## 11. 工期估算

在 V2.1 前端已经稳定、单人使用 Codex Plus、每天投入 3-5 个有效小时的前提下：

- V3 纯实现与验证：约 `64-94 小时`。
- 熟悉代码、等待模型额度、修复联调问题的缓冲：约 `16-26 小时`。
- 总计：约 `80-120 小时`。
- 全职集中开发：约 `12-18 个工作日`。
- 边学习边开发：约 `3-5 个自然周`。

如果 V2.1 F1-F4 尚未完成，需要额外预留 `12-20 小时`，先完成并发布稳定基线。工期不包含微信小游戏原生发布、AI 生成图片、第二游戏模板和云端正式部署。

## 12. V3 完成定义

- 用户能够从创意生成一个 3-10 分钟可试玩的 `arcade_collect` H5 原型。
- Python、Java、Vue 和文档统一使用 GameConfig 2.0，契约测试可重复通过。
- Demo 在桌面与手机浏览器具有完整开始、游玩、失败/胜利和结算流程。
- 用户可以创建调参版本，对比配置和试玩指标，历史版本保持不可变。
- Telemetry 安全、幂等地形成通关率、耗时、得分和失败统计。
- AI 能基于聚合数据生成可追溯的平衡建议。
- 原型包可以离线导出，内容与已保存版本一致且不包含敏感信息。
- Docker 主链路 E2E、quick Harness、安全审查和人工试玩验收通过。

