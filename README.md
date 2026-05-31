# GameDev Agent Workbench

面向小游戏开发流程的 AI Agent 工作台。

本项目不是单纯的「调用大模型接口」，而是尝试把 AI 能力放进一个完整的后端业务系统里：用户登录、游戏项目管理、Prompt 模板管理、Agent 执行记录、Workflow 编排、产物沉淀和前端展示。

当前版本定位为：

```text
AI 游戏设计工作台
```

后续升级目标是：

```text
AI 小游戏原型生成平台
```

也就是从「生成游戏设计方案」继续升级到「生成结构化 GameSpec，并由前端游戏运行时渲染出可玩的小游戏 Demo」。

## 项目定位

传统小游戏开发前期通常需要经历：

```text
游戏想法
-> 游戏概念设计
-> 核心玩法循环
-> 开发任务拆解
-> 原型实现
```

本项目希望把这条链路做成 AI 辅助工作流：

```text
用户创建游戏项目
-> 输入游戏想法
-> Java 创建 AgentRun / WorkflowRun
-> Java 根据 AgentType 选择 Prompt 模板
-> Java 调用 Python FastAPI Agent
-> Python 调用真实 LLM 或 mock fallback
-> Java 保存执行记录和 AgentArtifact
-> 前端展示运行结果、历史记录和项目产物
```

## 当前完成度

当前项目已经完成 AI 游戏工具流的基础后端闭环。

| 模块 | 状态 |
| --- | --- |
| 用户注册 / 登录 | 已完成 |
| JWT 鉴权 | 已完成 |
| 当前用户上下文 | 已完成 |
| 游戏项目管理 | 已完成 |
| AgentRun 执行记录 | 已完成 |
| AgentArtifact 产物沉淀 | 已完成 |
| Workflow 三步链路 | 已完成 |
| Prompt 模板管理 | 已完成 |
| Java 调 Python FastAPI | 已完成 |
| Python 调真实 LLM | 已完成 |
| mock fallback | 已完成 |
| Apifox 接口调试 | 已完成基础用例 |
| 轻量前端工作台 | 已完成基础演示 |
| 可玩小游戏生成 | 规划中 |

## 技术栈

### Java 后端

- Java 21
- Spring Boot 3
- Spring Security
- JWT
- MyBatis-Plus
- MySQL
- Bean Validation
- REST API
- 全局异常处理
- 统一响应结构

### Python Agent 服务

- Python
- FastAPI
- Pydantic
- HTTP LLM Client
- Prompt 模板渲染
- mock fallback

### 前端

- HTML
- CSS
- JavaScript
- Fetch API
- LocalStorage token 管理

### 开发工具

- IntelliJ IDEA
- VS Code
- Apifox
- Maven
- Git / GitHub
- DataGrip

## 核心业务模型

### GameProject

游戏项目表，用于管理用户创建的小游戏项目。

一个用户可以创建多个游戏项目，每个项目下可以运行多次 Agent，也可以沉淀多份 AI 产物。

### AgentRun

Agent 执行记录表。

用于记录一次 Agent 调用的完整过程，包括：

- 所属用户
- 所属项目
- Agent 类型
- 输入内容
- 输出内容
- 执行状态
- 错误信息
- 执行耗时

它是项目里非常关键的一张表，因为 AI 调用不是普通 CRUD，它需要保留执行过程，方便后续查询、复盘、重试和问题排查。

### PromptTemplate

Prompt 模板表。

用于管理不同 AgentType 对应的提示词模板，让 Prompt 不再硬编码在业务代码里。

当前链路中，Java 会根据 `agentType` 查询 ACTIVE 状态的 Prompt 模板，并把模板信息传给 Python Agent 服务。

### WorkflowRun

工作流执行记录表。

当前支持固定三步游戏设计工作流：

```text
GAME_CONCEPT
-> CORE_LOOP_DESIGN
-> TASK_BREAKDOWN
```

后一步 Agent 会继承前一步 Agent 的输出内容作为上下文。

### AgentArtifact

Agent 产物表。

用于保存 Agent 生成的项目资产，例如：

- 游戏概念设计
- 核心循环设计
- 开发任务拆解
- 后续计划中的 GameSpec

## 当前核心链路

### 单 Agent 运行

```text
POST /api/agent/run
-> 校验 JWT
-> 获取当前用户
-> 校验项目归属
-> 创建 AgentRun，状态 RUNNING
-> 根据 agentType 查询 ACTIVE PromptTemplate
-> 构造 PythonAgentRequest
-> 调用 Python FastAPI Agent
-> Python 调用真实 LLM
-> Java 保存输出内容
-> AgentRun 更新为 SUCCESS / FAILED
-> 返回统一结构结果
```

### 三步 Workflow 运行

```text
POST /api/workflow/game-design/run
-> 创建 WorkflowRun
-> 执行 GAME_CONCEPT
-> 执行 CORE_LOOP_DESIGN，并继承游戏概念输出
-> 执行 TASK_BREAKDOWN，并继承前两步输出
-> 保存 3 条 AgentRun
-> 保存 3 条 AgentArtifact
-> 更新 WorkflowRun 状态
-> 返回 Workflow 结果
```

## 主要接口

### 认证

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/auth/register` | 注册 |
| POST | `/api/auth/login` | 登录 |
| GET | `/api/auth/me` | 当前用户 |

### 游戏项目

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/projects` | 创建项目 |
| GET | `/api/projects` | 查询项目列表 |
| GET | `/api/projects/{projectUuid}` | 查询项目详情 |
| PUT | `/api/projects/{projectUuid}` | 更新项目 |

### Agent

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/agent/run` | 运行单个 Agent |
| GET | `/api/agent/runs` | 分页查询运行记录 |
| GET | `/api/agent/runs/{runUuid}` | 查询运行详情 |

### Workflow

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/workflow/game-design/run` | 运行游戏设计工作流 |
| GET | `/api/workflows/{workflowRunUuid}` | 查询工作流详情 |

### Artifact

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/projects/{projectUuid}/artifacts` | 查询项目下产物 |
| GET | `/api/artifacts/{artifactUuid}` | 查询产物详情 |

### Prompt Template

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/promptTemplate/modify` | 创建 Prompt 模板 |
| GET | `/api/promptTemplate/get` | 查询 Prompt 模板 |
| PUT | `/api/promptTemplate/{templateUuid}` | 更新 Prompt 模板 |

## 当前项目价值

这个项目的重点不是页面复杂度，而是 AI 应用后端链路：

- 如何把大模型能力接入真实业务系统
- 如何管理 Prompt 模板
- 如何保存 AI 执行记录
- 如何处理 Agent 状态流转
- 如何让 Java 后端和 Python Agent 服务协作
- 如何把多次 Agent 调用编排成 Workflow
- 如何让 AI 输出变成可查询、可追踪、可沉淀的项目产物

## 下一阶段升级方向：AI 小游戏原型生成平台

当前项目已经能生成游戏设计文档，但还没有直接生成可玩的小游戏。

下一阶段不会直接让 AI 每次自由生成完整游戏代码，因为这样稳定性较差、依赖容易混乱、调试成本高。

更可控的升级方案是：

```text
AI 生成结构化 GameSpec
-> 前端游戏运行时读取 GameSpec
-> 渲染出可玩的小游戏 Demo
```

## 计划新增模块

### 1. GameSpec 结构化游戏配置

新增 `GAME_SPEC_GENERATE` Agent。

它不再只返回自然语言文本，而是返回固定 JSON：

```json
{
  "gameType": "TOP_DOWN_SHOOTER",
  "player": {
    "speed": 240,
    "hp": 100
  },
  "enemies": [
    {
      "type": "slime",
      "hp": 30,
      "speed": 80
    }
  ],
  "levels": [
    {
      "name": "Level 1",
      "enemyCount": 8
    }
  ],
  "winCondition": "defeat_all_enemies"
}
```

这一步的目标是把 AI 输出从「设计文档」升级成「可执行配置」。

### 2. GameSpec Artifact

新增产物类型：

```text
GAME_SPEC_RESULT
```

用于保存结构化游戏配置。

这样每一次生成的小游戏原型都有可追踪、可复用的配置数据。

### 3. 前端游戏运行时

前端新增小游戏预览区域。

优先考虑使用：

- Phaser
- PixiJS
- Canvas

第一版不追求复杂游戏，只追求一个能玩的模板：

```text
玩家移动
-> 敌人生成
-> 碰撞检测
-> 分数统计
-> 胜负条件
```

### 4. 模板化小游戏生成

先支持少量稳定类型：

- 俯视角射击
- 平台跳跃
- 点击放置
- 文字冒险
- 简单卡牌原型

AI 负责生成配置，前端模板负责运行游戏。

### 5. 导出能力

后续支持导出：

- GameSpec JSON
- 游戏设计文档
- 任务拆解文档
- 可运行前端 Demo 包

## 升级后目标链路

```text
用户输入小游戏想法
-> GAME_CONCEPT 生成游戏概念
-> CORE_LOOP_DESIGN 生成核心循环
-> TASK_BREAKDOWN 拆解开发任务
-> GAME_SPEC_GENERATE 生成结构化配置
-> 保存 GameSpec Artifact
-> 前端游戏运行时读取 GameSpec
-> 渲染可玩小游戏 Demo
-> 用户预览和继续迭代
```

## 为什么不直接接一个 AI 平台生成完整游戏

直接让外部 AI 平台生成完整游戏代码，短期看起来更快，但存在几个问题：

- 生成结果不稳定
- 代码依赖不可控
- 很难沉淀成自己的业务系统
- 后端价值容易被弱化
- 不利于展示工程能力

本项目更倾向于：

```text
AI 负责生成结构化设计和配置
平台负责管理、编排、保存、预览和迭代
```

这样更接近真实 AI 应用工程，也更适合作为 Java 后端实习项目展示。

## 简历表达方向

可以概括为：

> 基于 Spring Boot + FastAPI 构建 AI 小游戏开发工作台，支持 JWT 鉴权、Prompt 模板管理、Agent 执行记录、Workflow 编排、真实 LLM 调用和项目产物沉淀。系统通过 Java 后端管理业务状态，通过 Python Agent 服务承接模型调用，并计划基于 GameSpec 将 AI 设计结果渲染为可玩的小游戏原型。

后续完成 GameSpec 和游戏预览后，可以升级为：

> 基于 Spring Boot + FastAPI + Phaser 构建 AI 小游戏原型生成平台，实现从游戏创意、核心玩法、任务拆解到结构化 GameSpec 和可玩 Demo 的自动化生成链路。

## 本地启动说明

### Java 后端

```bash
cd backend-java
mvn spring-boot:run
```

默认端口：

```text
http://localhost:8080
```

### Python Agent 服务

Python Agent 服务为独立 FastAPI 服务，默认端口：

```text
http://localhost:8000
```

Java 后端通过配置项调用 Python：

```yaml
app:
  python-service:
    base-url: http://localhost:8000
```

### 前端页面

当前前端为轻量静态页面：

```text
frontend/index.html
```

可以直接用浏览器打开，或使用本地静态服务运行。

## 当前状态说明

本项目目前处于快速迭代阶段，重点是完成 AI Workflow 主链路和项目展示能力。

短期优先级：

```text
稳定现有接口
-> 整理 README 和简历描述
-> 完善 Apifox 测试用例
-> 准备 GitHub 展示
-> 投递 Java 后端 / AI 应用开发实习
```

中期升级方向：

```text
GameSpec
-> Phaser 游戏预览
-> SSE 流式输出
-> Workflow 可配置化
-> 失败重试和执行日志增强
```

