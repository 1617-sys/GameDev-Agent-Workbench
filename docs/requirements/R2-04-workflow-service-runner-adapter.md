# R2-04: GAME_DESIGN 旧入口迁移为 WorkflowRunner 适配器

> 状态：`DONE`
>
> 前置任务：`R2-03`
>
> 推荐模型：`gpt-5.5`
>
> 任务类型：兼容改造 / API 契约回归

## 背景

`WorkflowServiceImpl` 当前既创建 WorkflowRun，又硬编码三步 Agent 调用、上下文拼接、StepRun 写入和最终状态处理。R2 要将它收敛为旧 API 的兼容适配器，调用统一 Runner 而不是保留第二套流程实现。

## 目标

使旧入口保持可用：

```text
POST /api/workflow/game-design/run
-> WorkflowServiceImpl (权限、项目校验、创建运行、VO 兼容)
-> WorkflowRunner
-> 既有 WorkflowRunVO 响应
```

`WorkflowServiceImpl` 保留必要的授权、项目查询、创建快照和 VO 转换职责，但不再拥有三步编排或 `buildStepContext(...)` 实现。

## 范围

允许：

- 将 GAME_DESIGN 三步的执行委托给 WorkflowRunner。
- 按 R1 快照创建 WorkflowRun 和初始 StepRun，复用 Runner 结果填充旧 `WorkflowRunVO`。
- 删除或迁走仅属于旧三步编排的重复私有方法。
- 增加 Controller/Service 回归测试，固定正常响应、业务失败、未知异常、无权限、查询历史运行行为。
- 仅在必要时调整 Mapper/VO 以携带 Runner 已持久化的步骤结果。

## 非目标

- 不修改旧 API 路径、请求字段、响应主结构或 HTTP 语义。
- 不改为 `202 Accepted`，不接 MQ/Outbox。
- 不迁移 Demo SSE 流程。
- 不引入前端改造、SSE 订阅或新的 Dashboard 查询。
- 不删除旧接口或标记为删除。

## 约束

- 权限和项目归属检查必须发生在创建/执行 WorkflowRun 前。
- 新入口与旧入口对相同输入产生相同的步骤顺序、AgentType、Artifact 语义和错误分类。
- `getWorkflowRun` 必须只读持久化状态，不能因为查询再次调用 Runner。
- 保留 R1 Snapshot 写入；迁移不得重新生成或覆盖已存在快照。
- 不允许用测试里的 Mock 返回值掩盖真实 StepRun 状态没有落库的问题。

## 验收标准

- [ ] 旧 GAME_DESIGN API 仍返回兼容的 WorkflowRunVO 和三步结果。
- [ ] WorkflowServiceImpl 不再直接维护三步执行顺序或手工拼接前置步骤上下文。
- [ ] 成功、业务失败、系统异常、无权限和查询路径已有回归测试。
- [ ] 查询 SUCCESS/FAILED WorkflowRun 不会再次调用 AgentRunService/Runner。
- [ ] 新旧 API 契约测试、R0/R1 测试和 quick Harness 均通过。

## 验证命令

```powershell
cd backend-java
mvn -Dtest=WorkflowServiceImplTest,*WorkflowController*Test,*WorkflowRunner*Test test
mvn test

cd ..
.\tools\verify.ps1 -Profile quick
```

## 审查清单

- 是否只是新增 Runner，却保留旧 Service 的第二套三步流程。
- 是否在查询接口或失败重试路径重复调用 Agent。
- 是否破坏旧 VO 的字段、步骤排序或异常响应。
- 是否绕过 R1 StepRun/WorkflowRun 状态持久化。
- 是否混入 R3 的异步提交或幂等键行为。

## 完成定义

- GAME_DESIGN 旧入口已成为薄适配器，唯一执行内核是 WorkflowRunner。
- 原有用户可不改前端继续使用该流程。
