# R2 验收: 统一 Workflow Runner 总验收

> 状态：`TODO`
>
> 前置任务：`R2-00`、`R2-01`、`R2-02`、`R2-03`、`R2-04`、`R2-05`、`R2-06`
>
> 推荐模型：`gpt-5.5`
>
> 任务类型：阶段验收 / 只验证与记录

## 背景

R2 的目标是消除同步 GAME_DESIGN 与 SSE Demo 之间重复的步骤编排，使后续 R3 能在一个稳定 Runner 上接入 MQ、可靠投递、消费幂等和恢复能力。本任务不增加业务功能，只证明 R2 的语义、兼容性和边界成立。

## 目标

生成 `docs/reports/R2-workflow-runner-report.md`，以可复现证据证明：

```text
R1 数据快照
+ 可单测的同步 WorkflowRunner
+ 统一 StepExecutor / ExecutionContext
+ WorkflowService 适配器
+ Demo SSE 事件适配器
+ 经校验的 GameConfig Artifact
= R3 可依赖的执行内核
```

## 范围

允许：

- 运行 R2、R1、R0 的相关测试和 quick Harness。
- 审查 WorkflowServiceImpl、DemoStreamServiceImpl 与 Runner 的职责边界。
- 审查 Git diff、迁移影响、测试质量和文档同步。
- 新增 R2 验收报告、更新 R2 任务卡状态、记录已知风险与 R3 准入结论。
- 在发现明确回归时，仅修复阻断验收的最小问题并补回归测试。

## 非目标

- 不引入 RabbitMQ、Outbox、重试队列、DLQ、限流或提交幂等。
- 不将旧接口改为 `202 Accepted`。
- 不做 R4 前端运行中心或订阅式 SSE。
- 不实施 R5 评测报告、模型指标或 RAG。
- 不进行破坏性数据库回滚或大范围重构。

## 验收项目

### Runner Core

- Runner 使用被冻结的 WorkflowRun 快照解析步骤与依赖。
- Runner 不依赖 HTTP、SSE、Redis 或 RabbitMQ 类型。
- StepExecutor 只执行一个步骤，Runner 负责顺序与失败短路。
- SUCCESS StepRun 和终态 WorkflowRun 不会触发重复 Agent 调用。

### Old Path Compatibility

- GAME_DESIGN 旧 API 仍返回兼容数据。
- Demo SSE 旧入口仍提供可识别的进度、失败与完成事件。
- Demo 可在 GameConfig 通过校验后生成可试玩 URL。

### Failure Boundaries

- Agent 业务失败、系统异常、非法快照、缺失依赖、无效 GameConfig 都有可验证的失败路径。
- SSE listener 失败和客户端断开不应改变已经可靠推进的 Workflow/Step 状态。
- 未获得 Redis Demo 锁时不执行 Runner 或 GameBuild。

### R3 Boundary

- 代码中没有新增 RabbitMQ Consumer、Outbox publisher、ACK、重试/DLQ 或 `202` 异步提交行为。
- R3 需要的任务消息、幂等、执行抢占、恢复扫描仍明确保留为后续范围。

## 验收标准

- [ ] R2-00 至 R2-06 全部完成并有独立测试。
- [ ] WorkflowServiceImpl 与 DemoStreamServiceImpl 不再分别复制步骤排序和上下文拼接。
- [ ] 任一步骤失败时 WorkflowRun/StepRun 状态与下游执行行为正确。
- [ ] SUCCESS StepRun/终态 WorkflowRun 不会因为重复调用、查询或 SSE 回调而二次调用 Agent。
- [ ] 四步 Demo 的 GameConfig 通过当前契约并能生成可试玩 URL。
- [ ] `mvn test`、前端 GameConfig 测试、Vue build 与 quick Harness 通过。
- [ ] 生成 R2 报告，并给出明确的 R3 准入结论。

## 验证命令

```powershell
git status --short
git diff --check

cd backend-java
mvn test

cd ..\frontend-vue
npm run test:game-config
npm run build

cd ..
.\tools\verify.ps1 -Profile quick

rg -n "SseEmitter|RabbitTemplate|@RabbitListener|Outbox|Acknowledgment|WorkflowRunner|buildStepContext" backend-java\src\main\java
rg -n "161764|password:\s*123456|local-secret-key|change-this-to-a-strong-secret-key|\bsk-[A-Za-z0-9]{20,}" `
  backend-java frontend-vue python-agent .env.example docker-compose.yml
```

## R2 报告模板

```markdown
# R2 Workflow Runner Report

## 环境
- 日期：
- 分支：
- 基线 commit：

## Runner 结果
- 定义快照与 ExecutionContext：
- StepExecutor 与 ArtifactWriter：
- WorkflowRun / StepRun 状态：

## 旧入口兼容
- GAME_DESIGN：
- Demo SSE：
- GameConfig / GameBuild：

## 验证命令
| 命令 | 结果 | 证据 |

## 已知风险
- 风险：
- 归属阶段：R3 / R4 / R5

## R3 准入结论
- PASS / BLOCKED
- 原因：
```

## 审查清单

- 是否存在第二套未迁移的步骤循环或上下文拼接逻辑。
- 是否让 SSE 细节泄漏进 Runner/Executor。
- 是否把失败状态覆盖、忽略或在重复调用后再次执行。
- 是否把 R3 的消息可靠性工作混进 R2，导致回归来源不清。
- 是否只凭页面可打开或 Mock 返回判断 Demo 成功。
- 是否遗漏 GameConfig 的真实结构化契约验证。

## 完成定义

- R2 验收报告已保存，R2 任务卡状态已更新。
- 同步 WorkflowRunner 已是两条旧路径唯一的步骤执行内核。
- R3 可以在不重写 Runner 的前提下接入异步任务与可靠性能力。
