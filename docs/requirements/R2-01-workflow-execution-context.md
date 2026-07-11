# R2-01: WorkflowExecutionContext 与步骤计划解析

> 状态：`DONE`
>
> 前置任务：`R2-00`
>
> 推荐模型：`gpt-5.5`
>
> 任务类型：核心领域抽象 / 单元测试

## 背景

现有两套流程通过 `buildStepContext(...)` 把前置步骤的文本手工拼接进后续 Agent 请求。这个方式无法可靠表达步骤 key、Artifact、Schema、定义快照与执行顺序，也是 Runner 难以复用的直接原因。

## 目标

在 `backend-java` 新增与 Web 层解耦的执行上下文和步骤计划读取能力：

```text
WorkflowRun 的 definition snapshot
-> WorkflowStepPlan 列表
-> WorkflowExecutionContext
   - workflowRun / project / user
   - immutable input snapshot
   - completed step outputs keyed by stepKey
   - artifact references keyed by stepKey
   - current step metadata
```

后续 StepExecutor 只能通过显式上下文读取已完成依赖的输出，不再直接接收 `WorkflowRunVO.WorkflowStepVO...` 拼接上下文。

## 范围

允许：

- 新增 `workflow` 领域或应用层包下的 Context、StepPlan、StepOutput 等值对象。
- 从 R1 `workflowDefinitionSnapshot` 或同等冻结快照解析顺序、依赖、agentType、artifact/schema 元信息。
- 新增纯单元测试，覆盖计划解析、依赖输出读取、不可变输入和非法快照。
- 为后续 Runner 预留读取已有 SUCCESS StepRun 输出的查询接口或适配层。

## 非目标

- 不实现完整 `WorkflowRunner` 循环。
- 不迁移 WorkflowServiceImpl 或 DemoStreamServiceImpl。
- 不修改现有 Controller、SSE 事件格式和前端。
- 不引入 JSON Schema 新依赖、MQ、Redis 或数据库迁移。
- 不重写 R1 的 Snapshot 字段语义。

## 约束

- Context 的 key 使用稳定 `stepKey`，不能使用展示名称或 `stepOrder` 作为唯一身份。
- 依赖输出缺失必须显式失败或标记不可执行，禁止返回空字符串继续调用 Agent。
- 输入、定义和 Prompt 快照在本次运行中只读；可变执行结果要通过受控方法写入。
- Context 不能保存 `SseEmitter`、Spring MVC DTO 或 RabbitMQ 对象。
- 输出中需要能区分纯文本、Artifact UUID、schema key/version，不能只保存拼接后的 String。

## 验收标准

- [ ] 给定 R1 保存的定义快照，可以得到稳定排序的步骤计划。
- [ ] 只有依赖步骤全部成功并已写入 Context 时，当前步骤才可获取其输入。
- [ ] 缺失依赖、循环依赖、重复 stepKey、未知 agentType 和非法 JSON 均有失败测试。
- [ ] 已完成步骤输出按 stepKey 可读取，且不会污染原始 request 输入。
- [ ] 新抽象不依赖 Web、SSE、MQ 或 Redis 类型。
- [ ] R0/R1 相关 Maven 测试仍通过。

## 验证命令

```powershell
cd backend-java
mvn -Dtest=*WorkflowExecutionContext*Test,*WorkflowStepPlan*Test test
mvn test

cd ..
.\tools\verify.ps1 -Profile quick
```

## 审查清单

- 是否把 JSON 解析散落在每个 StepExecutor 中。
- 是否允许缺少前置输出时仍然发起 Agent 请求。
- 是否让 Context 可以被任意调用方无约束覆盖历史步骤结果。
- 是否将运行时的 ACTIVE 定义误当作当前运行的快照。
- 是否只测试了对象构造，未测试非法计划和依赖边界。

## 完成定义

- 后续 Runner 可以用 Context 驱动步骤，不再需要手工字符串拼接前置步骤。
- 所有计划和依赖错误都能在调用 Agent 之前被检测。
