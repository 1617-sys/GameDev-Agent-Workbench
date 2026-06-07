# GameDev Agent Workbench

面向小游戏开发流程的 AI Agent 工作台。项目以 **Spring Boot + Python FastAPI + LLM + SSE + Vue3** 为核心，完成从“游戏创意输入”到“AI 生成设计方案、沉淀项目产物、返回可试玩 Demo 链接”的最小闭环。

这个项目不是单纯调用大模型接口，而是把 AI 能力接入到一个完整的后端业务系统中：用户认证、项目管理、Prompt 模板管理、Agent 执行记录、Workflow 编排、产物沉淀、SSE 流式进度和前端演示。

## 项目定位

```text
AI 游戏设计工作台
-> AI 小游戏原型生成平台
```

当前版本已经支持：

```text
用户输入游戏想法
-> 三步 Agent Workflow
-> 生成游戏概念、核心循环、任务拆解
-> 保存 AgentRun 和 Artifact
-> SSE 实时推送执行进度
-> 返回可试玩 Demo URL
```

## 技术栈

| 模块 | 技术 |
| --- | --- |
| Java 后端 | **Java 21**, **Spring Boot 3**, **Spring Security**, **JWT**, **MyBatis-Plus**, **MySQL** |
| Python Agent | **FastAPI**, **Pydantic**, **LangChain**, **LLM API**, mock fallback |
| 前端 | **Vue3**, **Vite**, 原生 SSE, Fetch API |
| 工具 | **Maven**, **Git**, **Apifox**, **DataGrip**, **IntelliJ IDEA** |

## 核心功能

- 用户注册、登录和 JWT 鉴权
- 游戏项目创建、查询和更新
- Agent 单步运行和执行记录查询
- Prompt 模板管理，支持按 AgentType 选择 ACTIVE 模板
- Java 后端调用 Python FastAPI Agent 服务
- Python Agent 调用真实 LLM，并保留 mock fallback
- 三步游戏设计 Workflow：游戏概念、核心循环、任务拆解
- Agent 输出保存为 Artifact，形成项目产物库
- SSE 流式推送 Demo 生成进度
- 返回可试玩小游戏 Demo 链接
- Vue3 工作台页面用于项目演示

## 系统架构

```text
Vue3 Frontend
    |
    | HTTP / SSE
    v
Spring Boot Backend
    |
    | MyBatis-Plus
    v
MySQL
    |
    | HTTP
    v
Python FastAPI Agent
    |
    | LangChain / HTTP Client
    v
LLM Provider
```

## 核心业务链路

### 单个 Agent 运行

```text
POST /api/agent/run
-> JWT 鉴权
-> 校验项目归属
-> 创建 AgentRun
-> 根据 AgentType 查询 ACTIVE PromptTemplate
-> Java 调用 Python FastAPI
-> Python 调用 LLM
-> Java 更新 AgentRun 状态和输出
-> 返回统一结构结果
```

### 三步 Demo Workflow

```text
POST /api/demo/game/stream
-> 建立 SSE 连接
-> GAME_CONCEPT
-> CORE_LOOP_DESIGN
-> TASK_BREAKDOWN
-> 保存 3 条 AgentRun
-> 保存 3 条 Artifact
-> GameBuildClient 返回 demoUrl
-> 前端打开可试玩 Demo
```

## 主要接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/auth/register` | 注册 |
| POST | `/api/auth/login` | 登录 |
| GET | `/api/auth/me` | 当前用户 |
| POST | `/api/projects` | 创建项目 |
| GET | `/api/projects` | 项目列表 |
| GET | `/api/projects/{projectUuid}` | 项目详情 |
| PUT | `/api/projects/{projectUuid}` | 更新项目 |
| POST | `/api/agent/run` | 单步运行 Agent |
| GET | `/api/agent/runs` | 分页查询 AgentRun |
| GET | `/api/agent/runs/{runUuid}` | AgentRun 详情 |
| GET | `/api/projects/{projectUuid}/artifacts` | 项目产物列表 |
| GET | `/api/artifacts/{artifactUuid}` | 产物详情 |
| POST | `/api/workflow/game-design/run` | 运行三步工作流 |
| GET | `/api/workflow/{workflowRunUuid}` | 工作流详情 |
| POST | `/api/demo/game/stream` | SSE 生成可试玩 Demo |
| POST | `/api/promptTemplate/modify` | 创建 Prompt 模板 |
| GET | `/api/promptTemplate/get` | 查询 Prompt 模板 |
| PUT | `/api/promptTemplate/{templateUuid}` | 更新 Prompt 模板 |

## 本地启动

### 1. 启动 MySQL

创建数据库：

```sql
CREATE DATABASE gamedev_agent_workbench DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

按 `backend-java/src/main/resources/db` 下的 SQL 初始化表结构和基础数据。

### 2. 启动 Java 后端

```bash
cd backend-java
mvn spring-boot:run
```

默认地址：

```text
http://localhost:8080
```

### 3. 启动 Python Agent

Python Agent 项目位于：

```text
F:\coe\python\python-agent
```

启动方式：

```bash
cd F:\coe\python\python-agent
.\.venv\Scripts\activate
uvicorn app.main:app --reload --host 127.0.0.1 --port 8000
```

默认地址：

```text
http://127.0.0.1:8000
```

### 4. 启动 Vue3 前端

```bash
cd frontend-vue
npm install
npm run dev
```

默认地址：

```text
http://localhost:5173
```

### 5. 可试玩 Demo 静态页面

当前最小 Demo 页面位于：

```text
frontend-vue/public/demo/mock-game.html
```

启动 Vue3 前端后，可以直接访问：

```text
http://localhost:5173/demo/mock-game.html
```

## 演示流程

1. 登录或注册账号
2. 创建一个游戏项目
3. 配置三个 ACTIVE Prompt 模板：
   - `GAME_CONCEPT`
   - `CORE_LOOP_DESIGN`
   - `TASK_BREAKDOWN`
4. 在 Vue3 工作台选择项目
5. 输入游戏想法
6. 点击“一键生成可玩 Demo”
7. 页面通过 SSE 展示三步执行进度
8. Workflow 完成后打开 `demoUrl`
9. 试玩最小小游戏 Demo

## 项目亮点

- 不是普通 CRUD，而是围绕 AI Agent 执行链路构建完整业务系统
- 使用 Java 管理业务状态，使用 Python 承接模型调用和 Agent 能力
- 使用 PromptTemplate 把提示词从代码中抽离，便于后续调试和版本管理
- 使用 AgentRun 记录模型调用输入、输出、状态、耗时和错误信息
- 使用 Workflow 编排多步 Agent，让后一步继承前一步输出
- 使用 Artifact 将 AI 输出沉淀为项目产物
- 使用 SSE 展示 AI 生成过程，而不是只等待最终结果
- 保留 mock fallback，方便无 API Key 或模型异常时继续演示
- 初步打通“自然语言创意 -> AI 设计结果 -> 可试玩 Demo URL”的闭环

## 下一步计划

- 将当前固定小游戏页面升级为读取 `GameSpec JSON`
- 新增 `GAME_SPEC_GENERATE` Agent
- 让玩家速度、地图、胜利条件等由 AI 结构化配置驱动
- Vue3 前端增加 Prompt 模板编辑页
- 增加 Workflow 历史和 Demo 历史
- 增强失败重试、执行日志和模型调用成本统计

## 简历描述参考

> 基于 Spring Boot + FastAPI + Vue3 构建 AI 小游戏开发工作台，实现 JWT 鉴权、项目管理、Prompt 模板管理、Agent 执行记录、Workflow 编排、SSE 流式输出和产物沉淀。系统通过 Java 后端管理业务状态，通过 Python Agent 服务接入 LLM，并实现从游戏创意输入到设计方案生成和可试玩 Demo 链接返回的最小闭环。
