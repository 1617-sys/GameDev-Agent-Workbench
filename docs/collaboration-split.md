# GameDev Agent Workbench 协作分工文档

这份文档用于明确：在项目早期开发阶段，哪些部分更适合你自己写，哪些部分更适合交给 Codex 生成或搭骨架。

目标很简单：
- 你负责那些最能体现项目价值、最需要你在面试中讲清楚的部分。
- Codex 负责重复性强、样板化明显、文档和脚手架类的工作。

## 协作原则

可以用下面这条规则判断归属：

- 你来写：核心业务逻辑、关键设计决策、面试时必须能自信解释的代码。
- Codex 来写：项目脚手架、重复 CRUD、DTO/VO 样板、文档、mock 数据、首版页面骨架。

对于这个项目，最合适的分工是：
- 你负责“项目的灵魂”。
- Codex 负责“项目的体力活”。

## 推荐仓库结构

```text
gamedev-agent-workbench/
├─ backend-java/
│  └─ src/main/java/com/example/gameworkbench/
├─ backend-python/
│  └─ app/
├─ frontend-web/
│  └─ src/
└─ docs/
```

## 建议你自己写的部分

这些文件或模块最值得你亲自参与，因为它们最能体现你的理解深度，也最适合面试讲解。

### 1. Java 核心业务编排

优先级：最高

建议由你主写的文件：
- `backend-java/src/main/java/com/example/gameworkbench/service/AgentRunService.java`
- `backend-java/src/main/java/com/example/gameworkbench/service/impl/AgentRunServiceImpl.java`
- `backend-java/src/main/java/com/example/gameworkbench/integration/PythonAgentClient.java`
- `backend-java/src/main/java/com/example/gameworkbench/integration/AigcGameflowClient.java`

为什么建议你写：
- 这是项目的主工作流。
- 最能体现你如何拆分服务职责。
- 面试时最容易被追问。

你最好亲自处理的内容：
- `agentType` 如何映射到 Python 接口
- Prompt 模板如何选择
- `agent_run` 执行记录如何创建和更新
- 成功、超时、失败如何处理
- Prompt 结果何时发送给 `AIGC-GameFlow`

### 2. 登录和鉴权流程

优先级：最高

建议由你主写的文件：
- `backend-java/src/main/java/com/example/gameworkbench/controller/AuthController.java`
- `backend-java/src/main/java/com/example/gameworkbench/service/AuthService.java`
- `backend-java/src/main/java/com/example/gameworkbench/service/impl/AuthServiceImpl.java`
- `backend-java/src/main/java/com/example/gameworkbench/security/*`

为什么建议你写：
- JWT 是经典后端面试题。
- 这部分能证明你理解鉴权流程，而不是只会调库。

你最好亲自处理的内容：
- 注册逻辑
- 登录校验
- Token 生成与解析
- 当前用户上下文提取
- 路由保护策略

### 3. 数据库设计和枚举定义

优先级：最高

建议由你主写的文件：
- `docs/database.md`
- `backend-java/src/main/resources/db/migration/V1__init.sql`
- `backend-java/src/main/java/com/example/gameworkbench/common/enums/AgentTypeEnum.java`
- `backend-java/src/main/java/com/example/gameworkbench/common/enums/RunStatusEnum.java`

为什么建议你写：
- 表设计最能体现后端基础。
- 字段命名、索引设计这些点很适合面试展开讲。

你最好亲自处理的内容：
- 每张表的作用
- 每个字段的含义
- 索引为什么这样建
- 枚举值怎么定义
- 逻辑删除怎么做

### 4. Agent 输出结构设计

优先级：高

建议由你主写的文件：
- `backend-python/app/schemas/requirement_breakdown.py`
- `backend-python/app/schemas/api_design.py`
- `backend-python/app/schemas/bug_analysis.py`
- `backend-python/app/schemas/prompt_generate.py`
- `backend-java/src/main/java/com/example/gameworkbench/vo/agent/*`

为什么建议你写：
- 这些输出决定了工具“到底有没有用”。
- 它们最能体现你的产品理解和业务思考。

你最好亲自处理的内容：
- 每种 Agent 输出哪些字段
- 哪些字段是必须的
- 输出要详细到什么程度才有演示价值

### 5. 前端核心交互逻辑

优先级：高

建议由你主写的文件：
- `frontend-web/src/pages/agent-workbench/index.vue`
- `frontend-web/src/pages/run-history/index.vue`
- `frontend-web/src/pages/integration/index.vue`

为什么建议你写：
- 这是面试官最容易直接看到的页面。
- 它们体现了你对用户流程的理解。

你最好亲自处理的内容：
- Agent 类型切换流程
- 输入和输出如何交互
- 历史记录筛选行为
- 联动页状态如何展示

## 更适合交给 Codex 写的部分

这些部分更适合用来提速，因为它们偏机械、重复，或者首版实现即可。

### 1. 项目脚手架

建议由 Codex 主写的文件：
- `backend-java/pom.xml`
- `backend-java/src/main/resources/application-example.yml`
- `backend-python/requirements.txt`
- `backend-python/app/main.py`
- `frontend-web/package.json`
- `docker-compose.yml`
- `.env.example`

为什么适合交给 Codex：
- 这些文件主要是配置和约定。
- 比较耗时间，但不是项目价值核心。

### 2. DTO / VO / Entity / Mapper 样板代码

建议由 Codex 主写的文件：
- `backend-java/src/main/java/com/example/gameworkbench/dto/**/*`
- `backend-java/src/main/java/com/example/gameworkbench/vo/**/*`
- `backend-java/src/main/java/com/example/gameworkbench/entity/**/*`
- `backend-java/src/main/java/com/example/gameworkbench/mapper/**/*`

为什么适合交给 Codex：
- 这部分非常重复。
- 结构重要，但逐行手写的收益不高。

### 3. 通用后端基础设施

建议由 Codex 主写的文件：
- `backend-java/src/main/java/com/example/gameworkbench/common/ApiResponse.java`
- `backend-java/src/main/java/com/example/gameworkbench/common/PageResponse.java`
- `backend-java/src/main/java/com/example/gameworkbench/common/exception/BusinessException.java`
- `backend-java/src/main/java/com/example/gameworkbench/common/handler/GlobalExceptionHandler.java`
- `backend-java/src/main/java/com/example/gameworkbench/config/*`

为什么适合交给 Codex：
- 这部分属于标准工程设施。
- 你需要理解，但不必每一行都自己敲。

### 4. Python mock 服务和首版客户端

建议由 Codex 主写的文件：
- `backend-python/app/api/*`
- `backend-python/app/services/*`
- `backend-python/app/clients/llm_client.py`
- `backend-python/app/core/logging.py`

为什么适合交给 Codex：
- FastAPI mock 服务很适合快速搭骨架。
- 现阶段目标是先打通 Java 和前端联调。

### 5. 文档和演示材料

建议由 Codex 主写的文件：
- `README.md`
- `docs/architecture.md`
- `docs/api.md`
- `docs/database.md`
- `docs/demo-flow.md`
- `docs/interview-qa.md`

为什么适合交给 Codex：
- 文档初稿非常适合由 AI 快速产出。
- 你后续再按自己的表达习惯微调即可。

### 6. UI 骨架和 mock 内容

建议由 Codex 主写的文件：
- `frontend-web/src/pages/login/index.vue`
- `frontend-web/src/pages/prompt-templates/index.vue`
- `frontend-web/src/components/**/*`
- `frontend-web/src/mock/**/*`

为什么适合交给 Codex：
- 首版页面布局和示例数据适合快速生成。
- 你再接业务交互和视觉细节会更高效。

## 最适合“你定方向，我来落代码”的部分

这些部分最适合协作式开发：你负责定义方向，我负责把实现铺开。

### 接口契约

你来决定：
- 接口语义
- 请求字段
- 返回字段
- 错误码

Codex 来写：
- DTO 类
- Controller 骨架
- OpenAPI / Apifox 初稿

### SQL 和表结构落地

你来决定：
- 建哪些表
- 哪些字段必须有
- 为什么要建这些索引

Codex 来写：
- migration SQL
- Entity 类
- Mapper 样板

### Prompt 模板管理

你来决定：
- 模板生命周期规则
- 启用 / 禁用语义
- 默认模板选取规则

Codex 来写：
- CRUD 接口
- DTO
- Mapper 和 Service 骨架

### 与 AIGC-GameFlow 的联动

你来决定：
- 什么时机提交任务
- 失败如何处理
- 任务状态如何轮询或查询

Codex 来写：
- HTTP 客户端壳子
- 请求响应模型
- 持久化接线代码

## 文件级分工清单

开发过程中可以直接按下面的清单来判断归属。

### 建议你优先亲自写的文件

- `backend-java/src/main/java/com/example/gameworkbench/service/impl/AgentRunServiceImpl.java`
- `backend-java/src/main/java/com/example/gameworkbench/service/impl/AuthServiceImpl.java`
- `backend-java/src/main/java/com/example/gameworkbench/integration/PythonAgentClient.java`
- `backend-java/src/main/java/com/example/gameworkbench/integration/AigcGameflowClient.java`
- `backend-java/src/main/resources/db/migration/V1__init.sql`
- `backend-python/app/schemas/requirement_breakdown.py`
- `backend-python/app/schemas/api_design.py`
- `backend-python/app/schemas/bug_analysis.py`
- `backend-python/app/schemas/prompt_generate.py`
- `frontend-web/src/pages/agent-workbench/index.vue`
- `frontend-web/src/pages/run-history/index.vue`
- `frontend-web/src/pages/integration/index.vue`

### 建议由 Codex 先生成首版的文件

- `backend-java/pom.xml`
- `backend-java/src/main/resources/application-example.yml`
- `backend-java/src/main/java/com/example/gameworkbench/common/**/*`
- `backend-java/src/main/java/com/example/gameworkbench/dto/**/*`
- `backend-java/src/main/java/com/example/gameworkbench/vo/**/*`
- `backend-java/src/main/java/com/example/gameworkbench/entity/**/*`
- `backend-java/src/main/java/com/example/gameworkbench/mapper/**/*`
- `backend-python/app/main.py`
- `backend-python/app/api/**/*`
- `backend-python/app/services/**/*`
- `backend-python/app/clients/**/*`
- `backend-python/requirements.txt`
- `frontend-web/src/pages/login/index.vue`
- `frontend-web/src/pages/prompt-templates/index.vue`
- `frontend-web/src/components/**/*`
- `README.md`
- `docs/*`

## 推荐开发顺序

建议按这个顺序推进，效率最高，也最适合你保留关键部分的掌控力。

1. 你先定义 MVP 范围、数据库意图和接口语义。
2. Codex 先搭仓库结构和基础文档。
3. Codex 先搭通用后端基础设施和 Python 骨架。
4. 你实现鉴权主流程和 `agent/run` 核心编排。
5. Codex 再根据你的设计补齐 DTO/VO/Entity/Mapper 样板。
6. 你实现前端核心页面的最终交互逻辑。
7. Codex 协助做 UI 补全、文档补强和演示材料整理。

## 实际协作时的判断规则

在让 Codex 生成某个文件之前，可以先问自己：

- 这个文件我以后要不要在面试里深入解释？
- 这个文件是不是项目最核心的业务价值？
- 这个文件是不是主要由重复性代码组成？

判断规则：
- 如果前两个问题答案是“是”，优先你自己写。
- 如果第三个问题答案是“是”，优先交给 Codex 先写首版。

## 最终建议

对于这个项目，最值得你亲自完成的核心部分是：

- 登录鉴权主流程
- `agent run` 主业务流程
- 初始化 SQL 设计
- Agent 输出结构设计
- 与 `AIGC-GameFlow` 的联动流程

除此之外，尤其是脚手架、通用基础设施、文档、mock 服务和重复样板代码，交给 Codex 先生成首版，是最省时间、也最适合求职项目推进的方式。
