# 第三周：真实 LLM 接入和企业级打磨计划

第三周的目标不是继续堆 CRUD，而是把项目从“可运行的 AI 游戏工具流 MVP”升级成“更接近企业级项目的 AI Workflow 平台”。

本周主线是：

```text
真实 LLM 接入
-> Prompt 模板管理
-> Agent 执行增强
-> SSE 流式输出
-> Vue3 工作台
-> Workflow 可配置化雏形
-> 项目包装和简历表达
```

## 本周总目标

本周结束时，项目需要达到：

- Python Agent 可以调用真实 LLM
- Java 仍然通过统一 AgentRun 链路调用 Python
- Prompt 不再散落在代码里，而是有模板管理思路
- AgentRun 支持更清晰的失败原因、重试次数和执行日志
- 至少完成一个 SSE 流式输出最小版本
- Vue3 工作台可以演示核心流程
- Workflow 从固定三步流程，开始向可配置流程过渡
- README 和简历描述可以体现真实 AI 项目价值

## 本周暂不追求

为了赶进度，本周先不展开：

- LangGraph 完整编排
- RAG 知识库
- 多人协作
- 复杂权限系统
- 文件上传
- 复杂前端编辑器
- 完整后台管理系统

这些功能有价值，但不适合作为第三周冲刺的主线。

## 每天 6 小时建议分配

```text
第 1 小时：设计当天表、接口、链路和风险点
第 2-4 小时：实现核心代码
第 5 小时：Apifox / 前端 / 数据库测试
第 6 小时：文档、Git 提交、复盘和问题记录
```

## 自己写和 AI 辅助原则

### 必须自己写

这些部分最能锻炼后端工程能力，建议自己手写一遍：

- 真实 LLM 调用主链路
- Prompt 模板如何选择和渲染
- AgentRun 状态流转
- 失败重试逻辑
- SSE 接口链路
- Workflow step 执行顺序
- Java / Python 调用边界
- Apifox 测试用例
- README 启动流程
- 简历项目描述初稿

### 可以让 AI 起草

这些内容模板性较强，可以让 AI 先生成，再自己检查：

- Entity / VO / Mapper 基础代码
- SQL 初稿
- Prompt 文案初稿
- Vue3 页面布局
- CSS 样式
- README 表格
- 示例 JSON
- 测试用例说明

### 可以主要交给 AI

这些内容偏展示和辅助交付，可以多用 AI：

- 前端视觉样式
- 空状态、Loading、错误提示文案
- README 润色
- 演示脚本
- 简历表达优化
- 项目亮点总结

---

# Day 15：合并修复和企业级基础整理

## 目标

先把合并分支后可能出现的问题清理干净，让项目重新稳定运行。

## 任务

- 修复 main 分支当前运行问题
- 确认 Java 后端可以启动
- 确认 Python 服务可以启动
- 确认前端可以打开
- 确认 CORS 配置正常
- 确认 MyBatis-Plus 分页插件正常
- 确认 Swagger / OpenAPI 可以访问
- 整理 Git 分支命名
- 补充 README 启动步骤

## 自己写

- Git 分支整理
- 三端启动验证
- README 启动流程
- Apifox 基础链路复测

## AI 辅助

- README 润色
- 接口清单整理
- 环境变量说明

## 完成标准

可以完整跑通：

```text
启动 Java
-> 启动 Python
-> 启动前端
-> 注册登录
-> 创建项目
-> 运行 workflow
-> 查看 AgentRun 和 Artifact
```

---

# Day 16：接入真实 LLM

## 目标

让 Python Agent 从 mock 返回，升级成真实模型调用。

## 任务

- Python 新增 LLM client
- 通过环境变量读取 API Key
- 保留 mock fallback
- 给 3 个核心 Agent 接入真实模型：
  - `GAME_CONCEPT`
  - `CORE_LOOP_DESIGN`
  - `TASK_BREAKDOWN`
- 统一 LLM 请求和响应结构
- 记录模型调用耗时
- 记录模型调用错误

## 自己写

- LLM client 主流程
- 环境变量读取逻辑
- LLM 异常处理
- Java 调 Python 的联调验证

## AI 辅助

- Prompt 文案
- Python 响应结构
- 示例 JSON
- README 说明

## 完成标准

Apifox 可以演示：

```text
POST /api/agent/run
-> Java 保存 RUNNING 状态
-> Java 调 Python
-> Python 调真实 LLM
-> Python 返回真实生成内容
-> Java 保存 SUCCESS 和 output_content
```

---

# Day 17：Prompt 模板管理

## 目标

让 Prompt 不再散落在代码里，形成可维护的模板管理结构。

## 建议数据表

`prompt_template`

核心字段：

- `id`
- `template_uuid`
- `agent_type`
- `name`
- `system_prompt`
- `user_prompt_template`
- `version`
- `status`
- `created_at`
- `updated_at`
- `deleted`

## 任务

- 设计 `prompt_template` 建表 SQL
- 创建 `PromptTemplate` 实体
- 创建 `PromptTemplateMapper`
- 创建 `PromptTemplateVO`
- 创建基础查询逻辑
- 根据 `agentType` 选择 Prompt 模板
- 支持默认模板
- 完成 3 个核心 Agent 的模板：
  - 游戏概念
  - 核心循环
  - 任务拆解

## 自己写

- 表字段设计
- `agentType` 和模板的匹配逻辑
- 模板渲染逻辑
- 默认模板策略

## AI 辅助

- SQL 初稿
- Entity / VO / Mapper
- Prompt 内容优化
- README 说明

## 完成标准

能够说明：

```text
同一个 AgentType 如何找到对应 Prompt
Prompt 如何渲染用户输入
后续为什么可以不改代码就调整 Prompt
```

---

# Day 18：失败重试和执行日志增强

## 目标

让 AgentRun 更像生产系统中的任务执行记录，而不只是简单保存输入输出。

## 建议增强字段

可根据实际情况选择是否加表字段：

- `retry_count`
- `max_retry_count`
- `fail_reason`
- `request_summary`
- `response_summary`

## 任务

- Java 调 Python 失败时支持最多重试 2 次
- 区分错误类型：
  - Python 服务不可用
  - LLM API 失败
  - 参数错误
  - 超时
- 增强日志：
  - 开始执行
  - 每次重试
  - 最终成功
  - 最终失败
- 补充 ErrorCode
- 数据库记录失败原因

## 自己写

- 重试逻辑
- 状态流转
- 错误分类
- 数据库字段取舍

## AI 辅助

- ErrorCode 枚举补全
- 日志文案
- README 说明

## 完成标准

可以演示：

```text
关闭 Python 服务
-> 调用 AgentRun
-> Java 自动重试
-> 最终失败
-> agent_run 记录 FAILED、retry_count、error_message
```

---

# Day 19：SSE 流式输出最小版本

## 目标

做出 AI 项目的强展示点：模型结果逐段输出，而不是一次性返回。

## 建议接口

优先实现一个最小版本：

- `POST /api/agent/run/stream`

也可以先做 Python SSE，再由 Java 转发。不要一开始追求完美。

## 任务

- Java 新增 SSE 接口
- 或 Python 新增 SSE 输出接口
- 前端支持流式追加文本
- 最终完成后仍保存完整结果到 `agent_run`
- 处理连接断开
- 记录流式任务状态

## 自己写

- SSE 基础链路
- 流式输出和落库的关系
- 前端如何接收流式内容

## AI 辅助

- SSE 示例代码
- 前端流式 UI
- README 说明

## 完成标准

前端可以演示：

```text
点击运行 Agent
-> 输出区域逐段出现文本
-> 结束后保存完整 AgentRun
```

---

# Day 20：Vue3 工作台启动

## 目标

开始替换当前静态前端，让项目更像真实工程。

## 任务

- 初始化 Vue3 + Vite
- 建立页面结构：
  - 登录页
  - 项目列表
  - Agent 工作台
  - 运行记录
  - 产物库
- 封装 request
- 封装 token 管理
- 接入已有接口：
  - `POST /api/auth/login`
  - `GET /api/auth/me`
  - `GET /api/projects`
  - `POST /api/projects`
  - `POST /api/agent/run`
  - `POST /api/workflow/game-design/run`
  - `GET /api/agent/runs`
  - `GET /api/projects/{projectUuid}/artifacts`

## 自己写

- 接口调用逻辑
- token 存储逻辑
- 页面状态流转
- 核心调试

## AI 辅助

- 页面布局
- CSS 样式
- 组件拆分
- Loading / Empty / Error 状态

## 完成标准

Vue3 页面可以完成：

```text
登录
-> 选择项目
-> 运行 workflow
-> 查看结果
-> 查看运行记录
```

---

# Day 21：Workflow 可配置化雏形和项目包装

## 目标

让项目从固定三步工作流，开始向可配置工作流过渡，并整理成可投简历的版本。

## 任务

- 设计 workflow 配置结构
- 暂时可以用枚举或 JSON 配置，不急着做复杂表
- 支持配置：
  - `workflowType`
  - `steps`
  - `stepOrder`
  - `agentType`
  - 是否把上一步结果传给下一步
- 把现有固定三步工作流改造成读取配置
- 整理 README
- 写简历项目描述
- 准备演示脚本
- 准备截图

## 自己写

- workflow 配置结构
- step 执行顺序
- 上下文传递逻辑
- 简历描述初稿

## AI 辅助

- README 润色
- 演示文案
- 简历表达优化
- 截图说明

## 完成标准

你能在 5 分钟内讲清楚：

```text
项目为什么不是普通 CRUD
Java 和 Python 怎么协作
真实 LLM 如何接入
Prompt 模板为什么重要
Workflow 如何编排多个 Agent
AgentRun 如何记录执行过程
前端如何展示 AI 工作流
```

---

# 中间件增强路线：Redis 和 RabbitMQ

这部分不是为了“硬塞技术栈”，而是把当前 AI 工作流项目继续往企业级后端方向推进。

建议顺序：

```text
先接 Redis
-> 再接 RabbitMQ
-> 最后把两者组合到 AgentRun / Workflow 主链路
```

原因是 Redis 接入成本低，能马上服务于 Prompt 模板缓存和防重复提交；RabbitMQ 会改变执行模型，更适合在主链路稳定后再接入。

## Redis 接入目标

Redis 在本项目里优先解决三个问题：

1. 缓存 ACTIVE Prompt 模板
2. 防止用户重复点击导致重复调用 LLM
3. 为后续异步任务状态查询做准备

不要一上来缓存所有列表接口。项目列表、运行记录列表这类数据目前访问量不大，缓存收益有限，还会增加一致性问题。

## Redis 推荐落点一：ACTIVE PromptTemplate 缓存

当前链路：

```text
POST /api/agent/run
-> 根据 agentType 查 prompt_template
-> 找 ACTIVE 模板
-> 传给 Python
-> 调用 LLM
```

可以改成：

```text
POST /api/agent/run
-> 先查 Redis：prompt:active:{agentType}
-> 命中：直接使用缓存模板
-> 未命中：查 MySQL
-> 查到后写入 Redis
-> 继续调用 Python
```

建议 key：

```text
prompt:active:GAME_CONCEPT
prompt:active:CORE_LOOP_DESIGN
prompt:active:TASK_BREAKDOWN
```

建议 TTL：

```text
10 分钟到 30 分钟
```

为什么不要永久缓存：

```text
Prompt 模板会被修改
如果永久缓存，数据库更新后 Java 可能还在用旧模板
```

模板更新时需要做缓存失效：

```text
PUT /api/promptTemplate/{templateUuid}
-> 更新 MySQL
-> 删除 Redis 中 prompt:active:{agentType}
```

简历表达：

> 使用 Redis 缓存 ACTIVE Prompt 模板，减少 AgentRun 高频执行时对 prompt_template 表的重复查询，并在模板更新后主动失效缓存，保证模板数据一致性。

## Redis 推荐落点二：AgentRun 防重复提交

用户在前端连续点击“运行 Agent”时，可能短时间内创建多个重复 AgentRun，并重复消耗 LLM API。

可以用 Redis 做短期锁：

```text
agent:run:lock:{userId}:{projectUuid}:{agentType}
```

执行流程：

```text
用户点击运行 Agent
-> Java 尝试写入 Redis lock，过期时间 5 到 10 秒
-> 写入成功：允许执行
-> 写入失败：说明短时间内重复提交，直接返回业务错误
-> AgentRun 结束后可以删除 lock，也可以等 TTL 自动过期
```

这个功能比“缓存项目列表”更适合你的项目，因为它直接关联 LLM 成本控制。

简历表达：

> 基于 Redis 实现用户级 AgentRun 防重复提交，避免前端重复点击造成多次模型调用和重复任务落库。

## Redis 推荐落点三：任务状态缓存

如果后面接 RabbitMQ 或 SSE，任务不会总是同步返回结果。

这时可以把运行状态写入 Redis：

```text
agent:run:status:{runUuid}
workflow:run:status:{workflowRunUuid}
```

前端可以快速查询：

```text
RUNNING
SUCCESS
FAILED
```

但这一步不急。只有当你开始做异步执行或流式输出时，它的价值才明显。

## RabbitMQ 接入目标

RabbitMQ 在本项目里最适合解决：

1. AgentRun 从同步执行升级为异步执行
2. Java 和 Python 调用链路解耦
3. LLM 调用失败后支持重试
4. Workflow 多步骤任务可以排队执行

当前同步链路：

```text
前端
-> Java POST /api/agent/run
-> Java 创建 AgentRun RUNNING
-> Java 同步调用 Python
-> Python 调 LLM
-> Java 等待结果
-> Java 更新 SUCCESS / FAILED
-> 返回结果
```

RabbitMQ 版本：

```text
前端
-> Java POST /api/agent/run
-> Java 创建 AgentRun RUNNING
-> Java 投递消息到 RabbitMQ
-> Java 立即返回 runUuid
-> 消费者异步调用 Python
-> 更新 AgentRun SUCCESS / FAILED
-> 前端轮询 / SSE 查看结果
```

## RabbitMQ 推荐交换机和队列

建议从简单版本开始：

```text
Exchange:
agent.run.exchange

Queue:
agent.run.queue

Routing Key:
agent.run.execute
```

消息体建议只放必要字段，不要把整个大对象塞进去：

```json
{
  "runUuid": "xxx",
  "userId": 1,
  "projectUuid": "xxx",
  "agentType": "GAME_CONCEPT"
}
```

为什么不把完整 prompt 和 content 都放进 MQ：

```text
消息应该轻量
真正的任务详情可以从 MySQL 根据 runUuid 查询
这样消息失败重试时也更稳定
```

## RabbitMQ 第一个版本怎么做

先不要直接重构所有 Workflow。

建议先改造单 AgentRun：

```text
POST /api/agent/run
-> 创建 agent_run，状态 RUNNING
-> 发送 MQ 消息
-> 返回 runUuid

消费者 AgentRunConsumer
-> 根据 runUuid 查询 agent_run
-> 查询 PromptTemplate
-> 调用 Python
-> 更新 agent_run 为 SUCCESS / FAILED
```

前端查询：

```text
GET /api/agent/runs/{runUuid}
```

这样你能清楚讲出异步任务模型。

## RabbitMQ 第二个版本：失败重试

失败重试可以先做简单版本：

```text
消费者调用 Python 失败
-> retry_count + 1
-> 如果 retry_count < max_retry_count，重新投递消息
-> 如果达到最大次数，更新 FAILED
```

后续再升级：

```text
死信队列 DLQ
延迟重试
失败原因分类
人工重新执行
```

不要第一天就上死信队列，否则很容易被配置复杂度拖住。

简历表达：

> 基于 RabbitMQ 将 AgentRun 改造成异步任务执行模型，接口提交后立即返回 runUuid，消费者异步调用 Python Agent，并支持 RUNNING / SUCCESS / FAILED 状态流转和失败重试。

## Redis + RabbitMQ 组合链路

最终比较企业级的链路是：

```text
用户提交 AgentRun
-> Redis 防重复提交
-> MySQL 创建 AgentRun RUNNING
-> RabbitMQ 投递执行消息
-> Consumer 消费消息
-> Redis / MySQL 查询 ACTIVE PromptTemplate
-> 调用 Python LLM Agent
-> MySQL 更新 AgentRun 结果
-> Redis 更新任务状态缓存
-> 前端轮询或 SSE 展示结果
```

这条链路非常适合面试讲述，因为它包含：

```text
鉴权
数据库建模
缓存
消息队列
异步任务
状态流转
失败重试
LLM 调用
前后端联调
```

## 每日任务安排建议

如果本周主线已经完成，可以把 Redis / RabbitMQ 拆成 4 天完成。

### Day A：Redis 基础接入

任务：

- Docker 启动 Redis
- Java 引入 Redis 依赖
- 配置 Redis 连接
- 编写 Redis key 命名规范
- 写一个最小 Redis 读写测试

自己写：

- Redis 配置
- key 命名规则
- 基础读写测试

AI 辅助：

- Docker Compose 片段
- README 启动说明

完成标准：

```text
Java 可以成功连接 Redis
可以 set / get 一个测试 key
```

### Day B：Redis 缓存 PromptTemplate

任务：

- 抽取 PromptTemplate 选择逻辑
- 查询 ACTIVE 模板时先查 Redis
- Redis 未命中再查 MySQL
- MySQL 查到后写入 Redis
- 修改模板后删除缓存
- Apifox 测试模板更新后缓存是否失效

自己写：

- 缓存命中 / 未命中逻辑
- 缓存失效逻辑
- AgentRun 与 PromptTemplate 的边界

AI 辅助：

- RedisTemplate 配置样例
- 测试用例文档

完成标准：

```text
第一次 AgentRun 查 MySQL
第二次 AgentRun 命中 Redis
更新 PromptTemplate 后 Redis 缓存被删除
再次 AgentRun 使用新模板
```

### Day C：RabbitMQ 基础接入

任务：

- Docker 启动 RabbitMQ
- Java 引入 RabbitMQ 依赖
- 配置 exchange / queue / routing key
- 定义 AgentRunMessage
- 写一个生产者和消费者最小 demo

自己写：

- 消息体设计
- exchange / queue / routing key 命名
- 消费者处理流程

AI 辅助：

- RabbitMQ 配置样例
- Docker Compose 片段

完成标准：

```text
调用测试接口
-> Java 发送消息
-> Consumer 收到消息并打印 runUuid
```

### Day D：AgentRun 异步化

任务：

- POST /api/agent/run 创建 RUNNING 记录
- 投递 AgentRunMessage 到 RabbitMQ
- 接口立即返回 runUuid
- Consumer 根据 runUuid 执行 Python 调用
- Consumer 更新 SUCCESS / FAILED
- 支持 retry_count 简单重试
- 前端或 Apifox 通过 GET /api/agent/runs/{runUuid} 查询结果

自己写：

- AgentRun 状态流转
- Consumer 主逻辑
- 失败重试逻辑
- 数据库更新边界

AI 辅助：

- 重复代码整理
- README 演示流程
- 简历表达优化

完成标准：

```text
提交 AgentRun
-> 立即拿到 runUuid
-> 数据库状态 RUNNING
-> Consumer 异步执行
-> 最终变成 SUCCESS 或 FAILED
```

## 什么时候写进简历

Redis 至少完成 PromptTemplate 缓存后，可以写进简历。

RabbitMQ 至少完成 AgentRun 异步化后，才建议写进简历。

不要只因为项目里引入了依赖就写：

```text
使用 Redis / RabbitMQ
```

要写成：

```text
使用 Redis 缓存 ACTIVE Prompt 模板并处理模板更新后的缓存失效。
```

```text
使用 RabbitMQ 将 AgentRun 改造为异步任务执行模型，支持任务状态流转和失败重试。
```

---

# 本周最低成功标准

如果时间不够，至少保住下面 4 件事：

1. 真实 LLM 接入
2. Prompt 模板抽象
3. 失败重试和日志增强
4. Vue3 工作台最小页面

这 4 件事完成后，项目就能明显从“mock MVP”升级成“真实 AI Workflow 项目”。

# 本周最有简历价值的成果

优先级从高到低：

```text
真实 LLM 接入
> SSE 流式输出
> Prompt 模板管理
> Workflow 可配置化
> Vue3 工作台页面
> 失败重试和执行日志增强
```

# 简历表达目标

本周结束后，简历可以写成：

> 基于 Spring Boot + FastAPI + Vue3 构建 AI 游戏开发工作流平台，支持 JWT 鉴权、真实大模型调用、Prompt 模板管理、Agent 编排、SSE 流式输出、执行记录追踪、失败重试和项目产物沉淀。

# 面试讲述目标

你需要能讲出这条链路：

```text
用户登录
-> 创建游戏项目
-> 输入游戏想法
-> Java 创建 AgentRun
-> Python 调用真实 LLM
-> Java 保存执行记录
-> 保存项目产物
-> 前端展示结果
-> 支持失败重试和历史追踪
```

