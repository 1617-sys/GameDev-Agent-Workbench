# 第一周 MVP 开发计划

第一周目标不是做完整平台，而是做出一个能跑通的 MVP。

本周最重要的一条主线是：

```text
登录
-> 调用 Agent
-> Java 调 Python mock
-> 保存 agent_run
-> Apifox 查看结果
```

只要这条链路跑通，第一周就是成功的。

## 本周总目标

第一周结束时，项目需要达到：

- Spring Boot 后端可以启动
- Python FastAPI 服务可以启动
- 用户可以注册和登录
- JWT 鉴权可以使用
- `POST /api/agent/run` 可以调用
- Java 可以请求 Python mock Agent
- Python 可以返回结构化 JSON
- Java 可以保存 `agent_run`
- Apifox 可以完成一次完整调用演示

## 本周暂不追求

为了保证 MVP 能完成，本周先不做：

- Vue3 完整前端
- 真实 LLM API
- Prompt 模板管理完整 CRUD
- AIGC-GameFlow 真实联动
- LangGraph / RAG / SSE
- 复杂权限和后台管理

这些功能后续再加，不影响第一周 MVP 成立。

## Day 1：收敛 MVP 和整理文档

### 目标

把项目目标从“大而全”收敛成“先跑通核心闭环”。

### 任务

- 明确 MVP 功能范围
- 修改 README，区分 MVP 和后续增强
- 确认第一周只做登录、Agent run、Python mock、执行记录
- 明确第一阶段先用 Apifox 调试，不强依赖完整前端
- 确认核心表优先级：先 `sys_user` 和 `agent_run`

### 完成标准

你能清楚说出：

- MVP 做什么
- MVP 不做什么
- 为什么先用 mock Agent
- 为什么先不做完整前端

## Day 2：数据库设计

### 目标

先设计 MVP 必需的数据表。

### 任务

- 设计 `sys_user`
- 设计 `agent_run`
- 预留后续表设计：
  - `agent_session`
  - `prompt_template`
  - `external_task_ref`
- 写初始化 SQL
- 确认字段命名规范
- 确认状态枚举

### MVP 表优先级

第一优先级：

- `sys_user`
- `agent_run`

第二优先级：

- `agent_session`
- `prompt_template`
- `external_task_ref`

### 完成标准

你能解释：

- `sys_user` 存什么
- `agent_run` 为什么是核心表
- 为什么其他表可以后做

## Day 3：完成认证模块骨架

### 目标

完成用户注册、登录和当前用户接口。

### 任务

- 创建用户实体、DTO、VO、Mapper
- 实现注册接口
- 实现登录接口
- 实现获取当前用户接口
- 接入密码加密
- 接入 JWT 生成
- 接入统一返回结构
- 接入统一异常处理

### 接口

- `POST /api/auth/register`
- `POST /api/auth/login`
- `GET /api/auth/me`

### 完成标准

Apifox 可以完成：

- 注册用户
- 登录并拿到 token
- 带 token 获取当前用户

## Day 4：完成 JWT 鉴权和后端基础设施

### 目标

让后端具备基本工程结构。

### 任务

- 完成 JWT 过滤器
- 完成当前用户上下文
- 完成 Spring Security 配置
- 完成统一错误码
- 完成参数校验
- 完成基础日志
- 准备 Agent 模块包结构

### 完成标准

- 不带 token 访问受保护接口会失败
- 带合法 token 可以访问受保护接口
- 参数错误能返回统一结构
- 系统异常能返回统一结构

## Day 5：完成 Python FastAPI mock Agent

### 目标

先让 Python 服务具备可调用能力。

### 任务

- 创建 FastAPI 工程
- 创建 4 个 Agent mock 接口
- 定义 Pydantic 请求模型
- 定义 Pydantic 响应模型
- 返回固定结构化 JSON
- 打印基础日志

### 接口

- `POST /agent/requirement-breakdown`
- `POST /agent/api-design`
- `POST /agent/bug-analysis`
- `POST /agent/prompt-generate`

### 完成标准

Apifox 可以直接调用 Python 服务，并拿到结构化 JSON。

## Day 6：打通 Agent Run 主链路

### 目标

完成 MVP 最核心的后端链路。

### 任务

- 创建 `agent_run` 实体、DTO、VO、Mapper
- 实现 `POST /api/agent/run`
- Java 根据 `agentType` 选择 Python 接口
- Java 调用 Python FastAPI
- 记录执行耗时
- 保存输入、输出、状态、错误信息
- 返回统一结构结果

### 完成标准

Apifox 可以演示：

```text
提交 Agent 请求
-> Java 调 Python
-> Python 返回 mock
-> Java 保存 agent_run
-> 接口返回执行结果
```

## Day 7：补查询接口和整理演示

### 目标

让 MVP 可以被完整演示。

### 任务

- 实现 `GET /api/agent/runs`
- 实现 `GET /api/agent/runs/{runUuid}`
- 整理 Apifox 测试用例
- 补充 README
- 补充数据库文档
- 记录已完成内容和下周计划

### 完成标准

可以完成一次完整演示：

1. 注册
2. 登录
3. 获取当前用户
4. 发起 Agent 执行
5. 查询执行记录
6. 查看执行详情

## 第一周最小成功标准

如果时间不够，只保住下面 5 件事：

1. 登录能跑通
2. JWT 能跑通
3. Python mock 能跑通
4. `POST /api/agent/run` 能跑通
5. `agent_run` 能落库

这 5 件事完成，MVP 就成立。

## 第二周再做什么

第一周 MVP 完成后，第二周建议做：

- Prompt 模板表和基础 CRUD
- Prompt 模板接入 Agent Run
- AIGC-GameFlow 联动接口
- Vue3 最小工作台页面
- README 截图和演示流程

## 学习重点

这一周你最应该理解的是：

- JWT 鉴权怎么流转
- Controller / Service / Mapper 怎么分层
- Java 怎么调用 Python 服务
- 为什么要保存执行记录
- Agent mock 为什么能先替代真实 LLM
- MVP 为什么要克制范围
