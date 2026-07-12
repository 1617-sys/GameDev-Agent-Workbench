# R4-02: 持久化 Workflow 事件、Sequence 与事件发布端口

> 状态：`TODO`
>
> 前置任务：`R4-00`、`R4-01`
>
> 推荐模型：`gpt-5.5`
>
> 任务类型：事件可追溯性 / 数据迁移与并发测试

## 背景

SSE 只传输瞬时内存事件时，客户端断线会丢失进度，也无法稳定去重、重放或解释状态变化。R4 需要为每个 WorkflowRun 记录可查询、单调有序的事件证据，SSE 只是它的订阅投影。

## 目标

建立如下事件模型：

```text
WorkflowRun / StepRun / Artifact / Recovery transition
-> WorkflowRunEvent persisted after durable state change
-> per-workflowRun sequence (1, 2, 3...)
-> in-process event publisher
-> R4-03 SSE subscribers
```

事件至少覆盖：Run 创建/状态变化、Step 状态变化、Artifact 可用、重试/恢复、取消请求/完成、终态错误。

## 范围

允许：

- 新增 Flyway migration、`workflow_run_event` 表、Entity、Mapper/Repository、事件 DTO 和 publisher port。
- 定义 eventType、sequence、occurredAt、workflowRunUuid、stepKey、status、attempt、artifactUuid、脱敏 payload、traceId。
- 在 R3 Consumer、R2 Runner、Outbox confirm、恢复与命令状态成功持久化后写入事件，或通过统一状态服务集中写入。
- 使用数据库约束/条件更新保证单个 Run 的 sequence 单调且不重复。
- 提供按 run UUID 查询快照后事件、或按 sequence 查询增量的服务方法。
- 添加并发状态更新、重复事件、回放排序、状态持久化失败与事件失败策略测试。

## 非目标

- 不实现 SSE HTTP endpoint 或前端 EventSource。
- 不用事件表替换 WorkflowRun/StepRun 主状态，也不做完整 Event Sourcing。
- 不改变 RabbitMQ 业务消息拓扑或将每个 UI 事件发到 MQ。
- 不记录完整 Prompt、敏感输入、Secret、Authorization 或未脱敏堆栈。
- 不实现 R5 Evaluation/Metric 事件。

## 约束

- WorkflowRun/StepRun 状态先可靠落库，再生成对应事件；不得广播“成功”但数据库仍是旧状态。
- sequence 按 `workflowRunUuid` 单调递增且可由数据库约束/原子分配验证，不能用进程内 AtomicLong。
- 状态更新与事件写入的事务边界必须保证可恢复一致性；若事件写入失败，不得向 SSE 伪造状态变化。
- 重复消费、恢复扫描和重试不能制造同一业务转换的无限重复事件。
- 事件 payload 仅作为 UI 增量提示，客户端重连后仍以 R4-01 快照为准。

## 验收标准

- [ ] Run/Step/Artifact 的关键持久化变化能生成关联 workflowRunUuid 的有序事件。
- [ ] 并发/重复触发下同一 Run 的 sequence 无重复、无倒退，查询结果按 sequence 稳定排序。
- [ ] 事件不会领先于实际数据库状态，也不会泄露敏感字段。
- [ ] 终态、重试、恢复和 Artifact 就绪可被区分而非只发送模糊文本。
- [ ] 事件查询可支持“从 sequence N 之后读取”，供 SSE 重连使用。
- [ ] R2/R3 状态和消息可靠性测试通过。

## 验证命令

```powershell
cd backend-java
mvn -Dtest=*WorkflowRunEvent*Test,*WorkflowEventPublisher*Test,*WorkflowRecovery*Test,*WorkflowMessageConsumer*Test test
mvn test

cd ..
.\tools\verify.ps1 -Profile quick
.\tools\verify.ps1 -Profile integration
```

## 审查清单

- 是否依赖进程内 sequence，重启或多实例后重复。
- 是否先发事件再落库，导致 UI 看见不存在的状态。
- 是否把事件表当作唯一状态源或在事件失败时伪造成功。
- 是否向事件 payload 写入 Prompt、token、完整错误堆栈或敏感输入。
- 是否忽略重复消费/恢复导致的事件风暴。

## 完成定义

- R4 有可重放、可排序、可审计的 Workflow 进度事件事实来源。
- SSE 可以在不参与业务执行的情况下订阅这些事件。
