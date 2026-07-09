# GameConfig 协议与 Demo 生成链路

这份文档对应“两周执行计划”里从 AI 设计结果走向可玩 Demo 的部分。核心思路是：不要让大模型直接生成一整个游戏工程，而是先让它生成稳定的 `GameConfig` JSON，再由前端 Phaser 运行时把 JSON 渲染成一个轻量小游戏。

## 目标

让链路从“AI 生成设计文档”升级为“AI 生成可试玩 Demo”：

```text
用户输入游戏想法
-> Java 触发 Demo SSE 工作流
-> Java 依次调用 Python Agent
-> Python 通过 LangChain 调用 LLM
-> 生成游戏概念、核心循环、任务拆解
-> 再生成 GameConfig
-> Java 保存 artifact
-> Java 返回 /demo/play?artifactUuid=...
-> Vue3 + Phaser 读取 GameConfig 并运行小游戏
```

## 为什么需要 GameConfig

大模型直接生成完整游戏代码不稳定，容易出现语法错误、文件结构混乱、依赖不一致等问题。

`GameConfig` 的作用是把 AI 输出约束成前端能稳定执行的数据协议。前端只要认识这套字段，就可以把不同题材渲染成同一种轻量玩法。

当前 MVP 只支持一种玩法：

```text
top_down_collect
俯视角移动 -> 收集物品 -> 避开敌人 -> 到达出口
```

这类玩法规则简单、代码少、演示直观，适合求职项目展示。

## 模块拆分

### 1. Python GameConfig Agent

职责：

- 新增 `POST /agent/game-config-generate`
- 接收 Java 传来的 `title/content/context/systemPrompt/userPromptTemplate`
- 通过 LangChain 调用真实 LLM
- 要求模型只返回 JSON
- 如果模型返回不规范，Python 做兜底归一化

核心文件：

- `python-agent/app/routers/agent.py`
- `python-agent/app/services/langchain_agent.py`
- `python-agent/app/prompts/agent_prompts.py`
- `python-agent/app/schemas/agent.py`

### 2. Java Demo Stream Service

职责：

- 通过 SSE 实时推送工作流进度
- 按顺序调用 4 个 Agent：
  - `GAME_CONCEPT`
  - `CORE_LOOP_DESIGN`
  - `TASK_BREAKDOWN`
  - `GAME_CONFIG_GENERATE`
- 每一步成功后保存为 `agent_artifact`
- 第 4 步只保存可执行的 `game_config` 内容
- 调用 `GameBuildClient` 生成可打开的 Demo URL

核心文件：

- `backend-java/src/main/java/com/example/gameworkbench/service/impl/DemoStreamServiceImpl.java`
- `backend-java/src/main/java/com/example/gameworkbench/client/GameBuildClient.java`
- `backend-java/src/main/java/com/example/gameworkbench/common/enums/AgentType.java`
- `backend-java/src/main/java/com/example/gameworkbench/common/enums/ArtifactType.java`

### 3. Vue3 + Phaser Demo Runtime

职责：

- 打开 `/demo/play`
- 读取 URL 中的 `artifactUuid`
- 从后端查询 artifact
- 解析 artifact.content 里的 GameConfig
- 使用 Phaser 渲染可玩 Demo

核心文件：

- `frontend-vue/src/game/GameDemoPage.vue`
- `frontend-vue/src/game/topDownCollectRuntime.js`
- `frontend-vue/src/game/defaultGameConfig.js`

## GameConfig 字段

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `version` | string | 协议版本，当前为 `1.0` |
| `gameType` | string | 当前固定为 `top_down_collect` |
| `title` | string | 游戏标题 |
| `theme` | string | 游戏主题 |
| `world` | object | 世界宽高和背景色 |
| `player` | object | 玩家出生点、速度、颜色 |
| `collectibles` | array | 收集物列表 |
| `enemies` | array | 敌人列表 |
| `exit` | object | 出口配置 |
| `winCondition` | object | 胜利条件 |
| `ui` | object | 页面提示文案 |

## 示例 JSON

```json
{
  "version": "1.0",
  "gameType": "top_down_collect",
  "title": "像素地牢逃脱",
  "theme": "玩家在地牢中收集宝石并找到出口。",
  "world": {
    "width": 960,
    "height": 540,
    "backgroundColor": "#111827"
  },
  "player": {
    "x": 96,
    "y": 96,
    "speed": 220,
    "color": "#60a5fa"
  },
  "collectibles": [
    { "id": "gem-1", "x": 260, "y": 140, "label": "宝石" },
    { "id": "gem-2", "x": 520, "y": 300, "label": "钥匙" },
    { "id": "gem-3", "x": 760, "y": 180, "label": "能量核心" }
  ],
  "enemies": [
    { "id": "enemy-1", "x": 420, "y": 220, "speed": 90, "patrolAxis": "x", "patrolDistance": 180 },
    { "id": "enemy-2", "x": 700, "y": 380, "speed": 80, "patrolAxis": "y", "patrolDistance": 140 }
  ],
  "exit": {
    "x": 860,
    "y": 450,
    "lockedUntilCollected": true
  },
  "winCondition": {
    "collectAll": true,
    "reachExit": true
  },
  "ui": {
    "objective": "收集全部物品后前往出口",
    "controlHint": "使用 WASD 或方向键移动"
  }
}
```

## 调试前置条件

Demo 工作流现在需要 4 个 ACTIVE 模板：

- `GAME_CONCEPT`
- `CORE_LOOP_DESIGN`
- `TASK_BREAKDOWN`
- `GAME_CONFIG_GENERATE`

如果缺少任意一个模板，SSE 会返回提示：先配置 ACTIVE prompt template。

`GAME_CONFIG_GENERATE` 的种子 SQL 见：

```text
backend-java/src/main/resources/db/seed_game_config_prompt_template.sql
```

## 完成标准

- Python `POST /agent/game-config-generate` 可以返回 `game_config`
- Java `POST /api/demo/game/stream` 可以推送 4 个 Agent 阶段
- 三个设计 artifact 和一个 GameConfig artifact 都能保存
- SSE 最终返回 `demoUrl`
- 浏览器打开 `/demo/play?artifactUuid=...` 可以运行可玩 Demo
