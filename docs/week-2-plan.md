# 第二周 AI 游戏工具流冲刺计划

第二周不再只是补基础设施，而是开始把项目升级成一个真正可展示的 AI 游戏开发工具流。

第一周已经完成：

```text
登录
-> JWT 鉴权
-> Agent run
-> Java 调 Python mock
-> 保存 agent_run
-> 查询执行记录
```

第二周的目标是把它升级成：

```text
创建游戏项目
-> 选择游戏开发工具流
-> 多 Agent 分步骤生成结果
-> 保存每一步执行记录
-> 保存可复用的项目产物
-> 查看项目、历史和生成文档
```

## 第二周总目标

本周目标不是继续写零散接口，而是完成一条更像真实产品的 AI 游戏开发流程。

最终希望可以演示：

```text
用户登录
-> 创建一个游戏项目
-> 输入游戏想法
-> 一键运行游戏设计工作流
-> 生成游戏概念、核心循环、开发任务
-> 保存为项目产物
-> 查询执行历史和产物详情
```

## 本周开发原则

- 每天至少保留 1 小时做 Apifox 测试和复盘
- 每天完成一个可独立演示的小闭环
- 优先做能串起主链路的功能
- 暂时不做真实 LLM、复杂前端、流式输出
- 能用 mock 跑通的地方先用 mock，先保证结构稳定
- 需要理解的核心业务代码自己写一遍，重复模板代码可以交给 AI

## 每天 6 小时建议分配

```text
第 1 小时：设计表、接口、字段和链路
第 2-4 小时：实现核心代码
第 5 小时：Apifox 测试和数据库验证
第 6 小时：整理文档、复盘问题、提交代码
```

## 自己写和 AI 辅助的原则

### 必须自己写一遍

这些代码最能训练后端工程思维，需要自己动手：

- DTO 字段设计
- Service 主业务流程
- 资源归属校验
- 状态流转逻辑
- Java 根据类型分发调用 Python
- 数据库字段设计和 SQL
- Apifox 测试用例

### 可以让 AI 起草

这些代码模板性较强，可以让 AI 先生成，再自己读懂和调整：

- Entity 基础字段
- VO 转换代码
- Mapper 接口
- Controller 基础接口
- Python mock 返回内容
- README 文档初稿
- 重复的参数校验注解

### 可以主要交给 AI

这些内容更适合作为交付辅助：

- mock 文案
- 示例 JSON
- Markdown 表格
- Apifox 用例说明
- README 润色
- 演示脚本草稿

## P0 必做功能

第二周最核心的功能是：

1. `game_project` 游戏项目模块
2. `agent_run` 绑定游戏项目
3. `agent_artifact` Agent 产物模块
4. Python 游戏 Agent 扩展
5. 一键游戏设计工作流

这 5 个功能完成后，项目会从“单次 Agent 调用器”升级成“AI 游戏开发工作台 MVP”。

## P1 可选增强

时间允许再做：

- 项目下执行历史查询
- 项目下产物列表查询
- 工作流执行历史
- 简单前端页面
- 查询分页和筛选
- 更清晰的日志和错误码

## P2 暂缓

本周先不做：

- 真实模型 API
- SSE 流式输出
- LangGraph
- RAG
- 多人协作
- 复杂权限
- 文件上传
- 复杂前端编辑器

这些功能很有价值，但不适合在第二周冲刺阶段展开。

---

# Day 8：游戏项目模块

## 目标

让系统有“游戏项目”的概念。后续所有 Agent 执行和产物都围绕某个游戏项目展开。

## 为什么要做

第一周的 `agent_run` 只记录了一次 Agent 调用，但真实工具流需要围绕一个项目持续生成内容。

例如：

```text
项目名：2D 横版 Roguelike 动作游戏
类型：动作 / Roguelike
目标平台：Web
描述：玩家控制角色闯关、攻击敌人、收集道具
```

## 建议数据表

`game_project`

核心字段：

- `id`
- `project_uuid`
- `user_id`
- `name`
- `game_type`
- `target_platform`
- `description`
- `status`
- `created_at`
- `updated_at`
- `deleted`

## 建议接口

- `POST /api/projects`
- `GET /api/projects`
- `GET /api/projects/{projectUuid}`
- `PUT /api/projects/{projectUuid}`

## 任务清单

- [ ] 设计 `game_project` 建表 SQL
- [ ] 创建 `GameProject` 实体
- [ ] 创建 `GameProjectRequest`
- [ ] 创建 `GameProjectVO`
- [ ] 创建 `GameProjectMapper`
- [ ] 创建 `GameProjectService`
- [ ] 实现创建项目
- [ ] 实现项目列表
- [ ] 实现项目详情
- [ ] 实现项目更新
- [ ] Apifox 测通项目 CRUD

## 自己写

- `game_project` 字段设计
- `GameProjectRequest`
- `GameProjectServiceImpl.createProject`
- `listProjects`
- 当前用户只能查看自己的项目这一段校验

## AI 辅助

- Entity / VO 基础代码
- Controller 基础代码
- Mapper 基础代码
- README 接口说明

## 完成标准

Apifox 可以完成：

```text
登录
-> 创建游戏项目
-> 查询项目列表
-> 查看项目详情
-> 修改项目描述
```

---

# Day 9：AgentRun 绑定 Project

## 目标

让每次 Agent 执行都属于某个游戏项目。

## 为什么要做

如果 `agent_run` 不绑定项目，后面就只能看到一堆散乱的执行记录。绑定项目后，才能形成：

```text
某个游戏项目
-> 多次 Agent 执行
-> 多个生成产物
```

## 数据库调整

给 `agent_run` 增加字段：

- `project_id`
- `project_uuid`

实际存储可以优先用 `project_id`，接口层使用 `project_uuid`。

## 接口调整

`POST /api/agent/run` 请求体增加：

- `projectUuid`

执行时需要校验：

```text
这个 projectUuid 是否存在
这个项目是否属于当前登录用户
```

## 任务清单

- [ ] 修改 `agent_run` 表结构
- [ ] 修改 `AgentRun` 实体
- [ ] 修改 `AgentRunRequest`，增加 `projectUuid`
- [ ] 在执行 Agent 前查询项目
- [ ] 校验项目归属当前用户
- [ ] 保存 `agent_run` 时写入项目 ID
- [ ] 增加项目下执行记录查询接口
- [ ] Apifox 测通项目内执行 Agent

## 自己写

- 项目归属校验
- `run` 方法里的项目绑定逻辑
- 查询某个项目下 `agent_run` 的逻辑

## AI 辅助

- SQL 字段补充
- VO 字段同步
- Controller 接口补齐
- Apifox 示例请求体

## 完成标准

Apifox 可以演示：

```text
创建项目
-> 在项目下运行 Agent
-> 查询这个项目下的执行记录
```

---

# Day 10：Agent 产物模块

## 目标

把 Python 返回结果保存成项目产物，而不只是存在 `agent_run.output_content` 里。

## 为什么要做

`agent_run` 适合记录执行过程，`agent_artifact` 适合保存可复用结果。

比如：

- 游戏概念文档
- 核心循环设计
- 需求拆解文档
- API 设计文档
- Prompt 文档
- Bug 分析报告

## 建议数据表

`agent_artifact`

核心字段：

- `id`
- `artifact_uuid`
- `project_id`
- `agent_run_id`
- `artifact_type`
- `title`
- `content`
- `created_at`
- `updated_at`
- `deleted`

## 建议接口

- `GET /api/projects/{projectUuid}/artifacts`
- `GET /api/artifacts/{artifactUuid}`

## 任务清单

- [ ] 设计 `agent_artifact` 建表 SQL
- [ ] 创建 `AgentArtifact` 实体
- [ ] 创建 `AgentArtifactVO`
- [ ] 创建 `AgentArtifactMapper`
- [ ] 创建 `ArtifactType` 枚举
- [ ] Agent 执行成功后生成 artifact
- [ ] 实现项目产物列表
- [ ] 实现产物详情
- [ ] Apifox 测通产物查询

## 自己写

- `agent_artifact` 表设计
- `ArtifactType` 枚举
- Agent 成功后保存产物的业务逻辑
- 项目产物查询的归属校验

## AI 辅助

- Entity / VO / Mapper
- mock 产物标题生成规则
- 文档说明

## 完成标准

Apifox 可以演示：

```text
运行 Agent
-> agent_run 保存执行记录
-> agent_artifact 保存生成文档
-> 查询项目产物列表
-> 查看产物详情
```

---

# Day 11：Python 游戏 Agent 扩展

## 目标

把 Python mock Agent 从通用开发助手扩展成游戏开发工具 Agent。

## 新增 Python 接口

优先新增：

- `POST /agent/game-concept`
- `POST /agent/core-loop-design`
- `POST /agent/task-breakdown`

可选新增：

- `POST /agent/level-design`
- `POST /agent/system-design`
- `POST /agent/narrative-design`

## Java 端同步

扩展 `AgentType`：

- `GAME_CONCEPT`
- `CORE_LOOP_DESIGN`
- `TASK_BREAKDOWN`

并映射到对应 Python 路径。

## 任务清单

- [ ] Python 新增游戏概念 Agent
- [ ] Python 新增核心循环设计 Agent
- [ ] Python 新增任务拆解 Agent
- [ ] Java 扩展 `AgentType`
- [ ] Java 调用新 Python 路径
- [ ] 为新 Agent 生成对应 artifact
- [ ] Apifox 测通 3 个新 Agent

## 自己写

- `AgentType` 扩展
- Java 根据新类型调用 Python 的验证
- 新 Agent 的输出结构设计

## AI 辅助

- Python mock 返回内容
- Pydantic 响应字段样例
- Apifox 示例请求

## 完成标准

Apifox 可以演示：

```text
选择 GAME_CONCEPT
-> Java 调 Python /agent/game-concept
-> 返回游戏概念 mock
-> 保存 agent_run
-> 保存 agent_artifact
```

---

# Day 12：游戏设计工作流

## 目标

实现一个“一键生成游戏设计方案”的固定工作流。

## 为什么要做

单个 Agent 调用只能展示一个点，工作流能展示完整工具链。

建议工作流：

```text
输入游戏想法
-> 生成游戏概念
-> 生成核心循环
-> 拆解开发任务
-> 保存 3 次 agent_run
-> 保存 3 个 artifact
-> 返回 workflow 结果
```

## 建议接口

- `POST /api/workflows/game-design/run`
- `GET /api/workflows/{workflowRunUuid}`

## 可选数据表

如果时间够，新增 `workflow_run`：

- `id`
- `workflow_run_uuid`
- `project_id`
- `user_id`
- `workflow_type`
- `status`
- `input_content`
- `summary`
- `error_message`
- `time_taken_ms`
- `created_at`
- `updated_at`
- `deleted`

如果时间紧，可以先不建表，只返回本次运行结果，并依靠多个 `agent_run` 和 `agent_artifact` 记录过程。

## 任务清单

- [ ] 设计游戏设计工作流输入 DTO
- [ ] 实现固定 3 步工作流
- [ ] 第一步调用 `GAME_CONCEPT`
- [ ] 第二步调用 `CORE_LOOP_DESIGN`
- [ ] 第三步调用 `TASK_BREAKDOWN`
- [ ] 每一步保存 `agent_run`
- [ ] 每一步保存 `agent_artifact`
- [ ] 返回聚合结果
- [ ] Apifox 测通一键工作流

## 自己写

- 工作流主流程
- 每一步失败时怎么处理
- 多 Agent 结果如何聚合
- 是否继续执行后续步骤的策略

## AI 辅助

- 工作流 DTO / VO 初稿
- Python mock 内容
- README 演示文案

## 完成标准

Apifox 可以演示：

```text
输入一个游戏想法
-> 一次请求连续跑 3 个 Agent
-> 生成 3 个产物
-> 查询项目产物列表能看到结果
```

---

# Day 13：演示层或后端增强

## 目标

根据进度选择做简单前端，或者继续增强后端查询能力。

## 方向 A：简单前端

如果希望作品更直观，做一个最小前端页面：

- 登录
- 项目列表
- 创建项目
- 运行游戏设计工作流
- 查看产物列表
- 查看产物详情

前端不需要复杂，只要能演示主流程。

## 方向 B：后端增强

如果时间不够做前端，就继续增强后端：

- 项目下 AgentRun 查询
- 项目下 Artifact 查询
- Workflow 历史查询
- AgentRun 分页
- AgentRun 按类型筛选
- AgentRun 按状态筛选

## 任务清单

- [ ] 选择前端方向或后端增强方向
- [ ] 完成一个可展示的演示入口
- [ ] 补充 Apifox 用例
- [ ] 补充 README 演示步骤

## 自己写

- 如果做前端：接口调用和页面状态管理
- 如果做后端：分页、筛选、归属校验

## AI 辅助

- 前端页面布局初稿
- README 截图说明
- Apifox 用例整理

## 完成标准

至少具备一种完整演示方式：

- Apifox 演示
- 或简单前端演示

---

# Day 14：整合演示和作品包装

## 目标

把第二周成果整理成可以展示、可以讲、可以复盘的作品版本。

## 演示主线

```text
注册登录
-> 创建游戏项目
-> 输入游戏想法
-> 一键运行游戏设计工作流
-> 查看 Agent 执行记录
-> 查看生成的游戏设计产物
```

## 任务清单

- [ ] 修复第二周遗留 bug
- [ ] 确认 Java 服务可启动
- [ ] 确认 Python 服务可启动
- [ ] Apifox 跑完整链路
- [ ] README 补充第二周成果
- [ ] 数据库文档补充新表
- [ ] 整理接口清单
- [ ] 整理演示脚本
- [ ] 写下第三周计划

## 自己写

- 演示脚本
- 项目介绍
- 面试讲述版本
- 第三周计划

## AI 辅助

- README 润色
- 接口表格
- 演示文案优化
- 面试表达润色

## 完成标准

你能在 5 分钟内演示：

```text
这个项目是什么
Java 和 Python 怎么协作
游戏项目如何创建
工作流如何运行
产物如何保存和查看
```

---

# 第二周最小成功标准

如果时间紧，第二周至少完成：

1. `game_project`
2. `agent_run` 绑定项目
3. `agent_artifact`
4. 新增 3 个游戏 Agent mock
5. 一个固定游戏设计工作流

这 5 件完成，第二周就很有成果。

# 第二周最强展示效果

如果进度顺利，最终演示可以这样讲：

> 我在第一周完成了登录鉴权、Java 调 Python mock Agent、执行记录落库和历史查询。第二周我把它扩展成一个 AI 游戏开发工具流：用户可以创建游戏项目，输入游戏想法后运行固定工作流，系统会连续调用多个 Agent，生成游戏概念、核心循环和开发任务，并把每一步结果保存成项目产物。

这会比单纯说“我做了几个接口”更有作品感。

# 第三周预留方向

第二周完成后，第三周可以考虑：

- 接入真实 LLM
- Prompt 模板管理
- Vue3 工作台页面
- Workflow 可配置化
- SSE 流式输出
- AIGC-GameFlow 旧项目联动
- Agent 结果评分
- 失败重试和执行日志增强

第三周再决定是否冲真实模型或前端，不要在第二周过早分心。
