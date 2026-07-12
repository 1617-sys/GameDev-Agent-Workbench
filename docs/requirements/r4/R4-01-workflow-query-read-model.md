# R4-01: WorkflowRun 查询 Read Model 与读取 API

> 状态：`TODO`
>
> 前置任务：`R4-00`
>
> 推荐模型：`gpt-5.5`
>
> 任务类型：后端查询契约 / 权限与回归测试

## 背景

R3 能异步创建、执行和恢复 WorkflowRun，但前端缺少基于持久化状态的统一读取接口。旧 `WorkflowRunVO` 只服务同步旧入口，不能稳定表达步骤、attempt、错误、Artifact 和运行详情。

## 目标

新增只读的 v1 查询模型与 API：

```http
GET /api/v1/workflow-runs/{workflowRunUuid}
GET /api/v1/workflow-runs/{workflowRunUuid}/steps
GET /api/v1/workflow-runs/{workflowRunUuid}/artifacts
```

主详情 API 可以按设计返回嵌套步骤/Artifact 摘要，也可以保留拆分端点；无论形式如何，所有数据必须来自 MySQL 持久化事实，不触发 Runner、重试或 Agent 调用。

## 范围

允许：

- 新增 QueryService、Controller、专用 Read DTO/VO、Mapper 查询和必要索引优化。
- 返回 WorkflowRun 当前状态、attempt、时间、错误码/脱敏消息、definition/schema 版本、StepRun 顺序/状态/耗时/Artifact 摘要。
- 提供按 userId + workflowRunUuid 的项目归属校验和标准未授权/不存在响应。
- 支持 R3 状态（PENDING、QUEUED、RUNNING、SUCCESS、FAILED、TIMEOUT、CANCELED 等）与历史可空快照字段。
- 添加 Controller/Service/Mapper 测试：授权、历史数据、终态/非终态、空 Artifact、步骤排序和只读行为。

## 非目标

- 不创建或执行 WorkflowRun，不调用 Python Agent、R2 Runner、MQ 或 Outbox。
- 不实现 SSE 订阅、取消、重试或 Dashboard 列表。
- 不返回完整 Prompt、原始敏感模型输入、内部 stack trace 或私密配置。
- 不修改旧 `/api/workflow/{workflowRunUuid}` 的兼容语义。
- 不做 R5 模型指标、评测或 R6 RAG 引用详情。

## 约束

- 查询接口必须恒等只读；多次 GET、轮询、SSE 重连不可新增 StepRun、Artifact、OutboxEvent 或 Agent 调用。
- 所有数据按当前认证用户进行归属校验；不能仅凭 UUID 返回跨用户项目数据。
- StepRun 顺序使用稳定 `stepOrder` 与 `stepKey`，不能依赖数据库默认返回顺序。
- 错误信息必须是对用户安全的脱敏摘要；内部异常保留在日志/审计，不进 API。
- 兼容历史运行的 nullable 字段，不能因旧数据没有 snapshot/Artifact 而抛出 500。

## 验收标准

- [ ] 已提交的异步 WorkflowRun 可通过 v1 详情 API 查询到持久化状态与 run UUID。
- [ ] 步骤按定义顺序返回，包含状态、attempt、耗时、错误摘要和可用 Artifact 引用。
- [ ] SUCCESS、FAILED、RUNNING、QUEUED、历史空字段都能被安全读取。
- [ ] 未认证、非所有者、未知 UUID 均得到稳定错误响应，且不泄露存在性细节。
- [ ] 查询不会调用 Runner/Agent 或改变 WorkflowRun、StepRun、Outbox 数据。
- [ ] 旧 API 回归、R3 相关 Maven 测试和 quick Harness 均通过。

## 验证命令

```powershell
cd backend-java
mvn -Dtest=*WorkflowRunQuery*Test,*WorkflowRunController*Test,*WorkflowStepRun*Test test
mvn test

cd ..
.\tools\verify.ps1 -Profile quick
```

## 审查清单

- 是否在 GET 中触发 Runner、恢复、重试或写数据库。
- 是否未按 userId 做资源归属校验。
- 是否将旧 WorkflowRunVO 直接暴露，导致 R4 字段与旧 API 互相耦合。
- 是否读取步骤/Artifact 时出现 N+1 查询或无稳定顺序。
- 是否向客户端输出完整内部异常、Prompt 或 Secret。

## 完成定义

- 前端可仅依赖 v1 Read Model 恢复任何有权限 WorkflowRun 的真实状态。
- 查询链路与异步执行链路完全解耦。
