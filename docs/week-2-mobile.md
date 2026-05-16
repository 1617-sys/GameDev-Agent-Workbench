# 第二周手机速读版

用途：手机上快速查看每天做什么。  
完整说明看：[第二周 AI 游戏工具流冲刺计划](./week-2-plan.md)

## 本周一句话目标

把第一周的单次 Agent 调用，升级成一个可演示的 AI 游戏开发工具流：

```text
创建游戏项目
-> 运行多 Agent 工作流
-> 保存执行记录
-> 保存生成产物
-> 查询历史和详情
```

## 每天 6 小时节奏

```text
1 小时：设计表、接口、字段
3 小时：写核心代码
1 小时：Apifox 测试
1 小时：文档、复盘、提交
```

## 自己写 vs AI 写

### 自己必须写

- DTO 字段设计
- Service 主流程
- 用户资源归属校验
- 状态流转逻辑
- SQL 表设计
- Java 根据类型分发 Python 接口
- Apifox 测试用例

### AI 可以起草

- Entity
- VO
- Mapper
- Controller 基础结构
- Python mock 文案
- 示例 JSON
- README 表格和说明

## 本周 P0

- [ ] `game_project`
- [ ] `agent_run` 绑定项目
- [ ] `agent_artifact`
- [ ] 新增游戏 Agent mock
- [ ] 一键游戏设计工作流

## 本周暂缓

- 真实模型 API
- SSE
- LangGraph
- RAG
- 多人协作
- 复杂权限
- 文件上传
- 复杂前端编辑器

---

# Day 8：游戏项目模块

## 目标

让系统可以管理一个游戏项目。

## 要做

- [ ] 建 `game_project` 表
- [ ] 写 `GameProject`
- [ ] 写 `GameProjectRequest`
- [ ] 写 `GameProjectVO`
- [ ] 写 `GameProjectMapper`
- [ ] 写 `GameProjectService`
- [ ] `POST /api/projects`
- [ ] `GET /api/projects`
- [ ] `GET /api/projects/{projectUuid}`
- [ ] `PUT /api/projects/{projectUuid}`
- [ ] Apifox 测通

## 自己写

- 表字段
- `GameProjectRequest`
- `createProject`
- `listProjects`
- 当前用户只能查自己的项目

## AI 写

- Entity / VO / Mapper
- Controller 基础代码
- 接口说明

## 完成标准

```text
登录
-> 创建项目
-> 查项目列表
-> 查项目详情
-> 修改项目
```

---

# Day 9：AgentRun 绑定项目

## 目标

每次 Agent 执行都属于某个游戏项目。

## 要做

- [ ] `agent_run` 加 `project_id`
- [ ] 可选加 `project_uuid`
- [ ] `AgentRun` 实体同步字段
- [ ] `AgentRunRequest` 加 `projectUuid`
- [ ] 执行前查询项目
- [ ] 校验项目属于当前用户
- [ ] 保存 `agent_run` 时绑定项目
- [ ] 增加项目下执行记录查询
- [ ] Apifox 测通

## 自己写

- 项目归属校验
- `run` 里的项目绑定逻辑
- 项目下执行记录查询

## AI 写

- SQL 字段补充
- VO 字段同步
- Controller 基础接口

## 完成标准

```text
创建项目
-> 在项目下运行 Agent
-> 查询项目下的 Agent 历史
```

---

# Day 10：Agent 产物模块

## 目标

把 AI 输出保存成可复用的项目文档。

## 要做

- [ ] 建 `agent_artifact` 表
- [ ] 写 `AgentArtifact`
- [ ] 写 `AgentArtifactVO`
- [ ] 写 `AgentArtifactMapper`
- [ ] 写 `ArtifactType`
- [ ] Agent 成功后保存 artifact
- [ ] `GET /api/projects/{projectUuid}/artifacts`
- [ ] `GET /api/artifacts/{artifactUuid}`
- [ ] Apifox 测通

## 自己写

- 表设计
- `ArtifactType`
- Agent 成功后保存产物
- 产物归属校验

## AI 写

- Entity / VO / Mapper
- mock 标题生成
- 文档说明

## 完成标准

```text
运行 Agent
-> 保存 agent_run
-> 保存 agent_artifact
-> 查询项目产物
-> 查看产物详情
```

---

# Day 11：新增游戏 Agent

## 目标

让 Python mock 更像游戏开发工具，而不是通用助手。

## 新增接口

- [ ] `POST /agent/game-concept`
- [ ] `POST /agent/core-loop-design`
- [ ] `POST /agent/task-breakdown`

## Java 同步

- [ ] `AgentType.GAME_CONCEPT`
- [ ] `AgentType.CORE_LOOP_DESIGN`
- [ ] `AgentType.TASK_BREAKDOWN`
- [ ] 映射到 Python 路径
- [ ] 新 Agent 也能保存 artifact
- [ ] Apifox 测通

## 自己写

- `AgentType` 扩展
- 输出结构设计
- Java 调用验证

## AI 写

- Python mock 内容
- Pydantic 示例
- Apifox 示例请求

## 完成标准

```text
选择 GAME_CONCEPT
-> Java 调 Python
-> 保存 run
-> 保存 artifact
```

---

# Day 12：游戏设计工作流

## 目标

一个请求连续跑多个 Agent，生成一套游戏设计方案。

## 固定流程

```text
输入游戏想法
-> 生成游戏概念
-> 生成核心循环
-> 拆解开发任务
-> 保存 3 个 run
-> 保存 3 个 artifact
```

## 要做

- [ ] 设计 workflow 输入 DTO
- [ ] `POST /api/workflows/game-design/run`
- [ ] 第一步调用 `GAME_CONCEPT`
- [ ] 第二步调用 `CORE_LOOP_DESIGN`
- [ ] 第三步调用 `TASK_BREAKDOWN`
- [ ] 每一步保存 `agent_run`
- [ ] 每一步保存 `agent_artifact`
- [ ] 返回聚合结果
- [ ] Apifox 测通

## 自己写

- 工作流主流程
- 失败处理策略
- 多 Agent 结果聚合

## AI 写

- DTO / VO 初稿
- Python mock 内容
- README 演示文案

## 完成标准

```text
一个请求
-> 连续跑 3 个 Agent
-> 生成 3 个产物
-> 项目产物列表能查到
```

---

# Day 13：演示层或后端增强

## 二选一

### 方向 A：简单前端

- [ ] 登录
- [ ] 项目列表
- [ ] 创建项目
- [ ] 运行工作流
- [ ] 查看产物列表
- [ ] 查看产物详情

### 方向 B：后端增强

- [ ] 项目下 AgentRun 查询
- [ ] 项目下 Artifact 查询
- [ ] Workflow 历史查询
- [ ] AgentRun 分页
- [ ] 按类型筛选
- [ ] 按状态筛选

## 自己写

- 前端接口调用和状态管理
- 或后端分页、筛选、归属校验

## AI 写

- 页面布局初稿
- Apifox 用例整理
- README 截图说明

## 完成标准

至少有一种稳定演示方式：

- Apifox
- 或简单前端

---

# Day 14：整合演示

## 目标

把第二周成果整理成能展示、能讲、能复盘的版本。

## 演示主线

```text
注册登录
-> 创建游戏项目
-> 输入游戏想法
-> 运行游戏设计工作流
-> 查看执行记录
-> 查看生成产物
```

## 要做

- [ ] 修遗留 bug
- [ ] Java 服务可启动
- [ ] Python 服务可启动
- [ ] Apifox 跑完整链路
- [ ] README 补第二周成果
- [ ] 数据库文档补新表
- [ ] 整理接口清单
- [ ] 整理演示脚本
- [ ] 写第三周计划

## 自己写

- 演示脚本
- 项目介绍
- 面试讲述版本
- 第三周计划

## AI 写

- README 润色
- 接口表格
- 演示文案优化

## 完成标准

你能 5 分钟讲清楚：

```text
项目是什么
Java 和 Python 怎么协作
游戏项目怎么创建
工作流怎么运行
产物怎么保存和查看
```

---

# 最小成功标准

时间紧就保住这 5 件事：

- [ ] `game_project`
- [ ] `agent_run` 绑定项目
- [ ] `agent_artifact`
- [ ] 3 个游戏 Agent mock
- [ ] 固定 game-design workflow

# 简历表达方向

第二周完成后可以说：

> 在第一周完成登录鉴权、Java 调 Python mock Agent、执行记录落库和历史查询的基础上，第二周将项目扩展为 AI 游戏开发工具流，支持游戏项目管理、多 Agent 工作流、项目产物沉淀和历史追踪。

