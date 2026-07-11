# R2-00: WorkflowRunner 设计冻结

> 状态：`DONE`
>
> 前置任务：`R1-验收`
>
> 推荐模型：`gpt-5.5`
>
> 任务类型：架构契约 / 只写文档

## 背景

R1 已经为 WorkflowRun、WorkflowStepRun、WorkflowDefinitionVersion 和 PromptVersion 建立了可追踪的数据地基，但当前业务仍有两套重复的步骤编排：

```text
WorkflowServiceImpl       -> 同步三步 GAME_DESIGN
DemoStreamServiceImpl     -> 异步线程四步 Demo + SSE + GameBuild
```

R2 必须先冻结统一 Runner 的边界，避免在改造时把状态推进、Agent 调用、SSE 推送、GameConfig 校验和后续 RabbitMQ 责任混在同一个 Service 中。

## 目标

新增 `docs/requirements/R2-workflow-runner-design.md`，定义以下可实施契约：

```text
旧 Controller / Service Adapter
-> WorkflowRunner.run(command, listener)
-> WorkflowExecutionContext
-> WorkflowStepExecutor
-> WorkflowStepRun / AgentRun / Artifact 持久化
-> WorkflowRun 最终状态
```

文档至少明确：

- `WorkflowRunner`、`WorkflowStepExecutor`、`WorkflowExecutionContext`、`WorkflowExecutionListener` 的职责、输入和输出。
- 从 `WorkflowDefinitionVersion` 快照读取步骤顺序与依赖的规则。
- StepRun 状态推进、失败短路、已成功步骤跳过和 WorkflowRun 汇总规则。
- 两条旧入口如何分别适配 Runner，且保持响应与 SSE 事件兼容。
- GameConfig Step 的 Schema 校验、Artifact 写入、Evaluation hook 边界。
- R2 与 R3 的责任分界和迁移回退策略。

## 范围

允许：

- 阅读 R1 领域模型、旧 WorkflowServiceImpl、DemoStreamServiceImpl、AgentRunService 与现有测试。
- 新增上述设计文档及必要 Mermaid 图、依赖图和伪代码。
- 在设计文档中列出后续 R2 子任务的文件范围、测试策略与风险。

## 非目标

- 不修改 Java、Python、Vue 业务代码。
- 不创建 RabbitMQ、Outbox、重试、幂等提交或恢复扫描。
- 不修改 HTTP API 为 `202 Accepted`。
- 不删除或重命名旧接口。
- 不改变 `docs/game-config-schema.md` 的既有契约。

## 约束

- Runner 本身必须是同步、可直接单测的 Java 调用；Demo 的线程和 SSE 只属于外层适配器。
- 一个步骤的外部 Agent 调用不得被数据库事务长时间包裹。
- Runner 不依赖 `SseEmitter`、`HttpServletRequest`、RabbitMQ 消息或 Controller DTO。
- R1 快照是本次运行的事实来源；ACTIVE 定义或 Prompt 后续切换不得改变已创建运行的语义。
- 状态合法性必须复用 `WorkflowStatusPolicy`，不能在新类中复制一套状态机。

## 验收标准

- [ ] 设计文档能解释从旧入口到 Runner、StepExecutor、持久化和返回值的完整调用链。
- [ ] 明确每个核心接口的最小方法签名、错误语义和禁止依赖。
- [ ] 明确成功、Agent 业务失败、未知异常、依赖未满足、已成功步骤五种行为。
- [ ] 明确 Demo SSE 的 listener 适配方案，且不让 SSE 连接决定业务是否继续执行。
- [ ] 明确 GameConfig 输出必须在写入可试玩产物前通过当前 Schema 契约。
- [ ] 明确 R2 不引入的 R3 能力及回退到旧实现的方式。

## 验证命令

```powershell
git diff --check
rg -n "WorkflowRunner|WorkflowStepExecutor|WorkflowExecutionContext|WorkflowExecutionListener|R3" docs\requirements\R2-workflow-runner-design.md
```

## 审查清单

- 是否将 SSE、MQ、HTTP 或 Redis 锁泄漏进 Runner 核心接口。
- 是否要求 Runner 读取当前 ACTIVE 定义而非使用 WorkflowRun 快照。
- 是否遗漏 StepRun 的 `SUCCESS` 跳过语义。
- 是否让 GameConfig 未校验就进入 Phaser/GameBuild 产物链路。
- 是否把 R3 的可靠投递、重试、ACK 设计混入 R2。

## 完成定义

- R2 的核心接口与迁移顺序已被文档冻结。
- 后续实现任务能只依赖该文档和 R1 契约开始工作。
- R2-01 至 R2-06 的边界没有重叠或责任空洞。
