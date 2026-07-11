# R1 验收：数据与领域地基总验收

> 状态：`DONE`
>
> 前置任务：`R1-01`、`R1-02`、`R1-03`、`R1-04`、`R1-05`、`R1-06`
>
> 推荐模型：`gpt-5.5`
>
> 任务类型：阶段验收 / 数据迁移审查

## 背景

R1 完成后，项目应该从“现有流程可运行”升级为“后续 Runner、MQ、评测可以依赖的数据地基已存在”。

本任务只做验收和证据整理，不继续增加功能。

## 目标

证明：

```text
Flyway 可用
+ Workflow 状态机可测试
+ WorkflowDefinitionVersion 可查询
+ WorkflowStepRun 可追踪
+ PromptVersion 不可变
+ WorkflowRun 可冻结定义和 Prompt 快照
+ R0 Baseline 未回归
```

## 范围

允许：

- 运行 R1 全部验证命令。
- 检查 migration 文件顺序和不可变性。
- 检查 Git diff 和 commit 分组。
- 新增 `docs/reports/R1-workflow-domain-report.md`。
- 更新 R1 任务卡状态。
- 记录剩余风险和 R2 准入结论。

## 非目标

- 不新增业务功能。
- 不抽 WorkflowRunner。
- 不接 RabbitMQ。
- 不优化前端 bundle。
- 不修复与 R1 验收无关的问题。
- 不执行破坏性数据库回滚。

## 验收项目

### Migration

- Flyway migration 可以执行。
- migration 文件命名连续且语义清楚。
- 已提交 migration 没有被修改覆盖。
- 空库和已有库策略有文档。

### Domain

- WorkflowRun 状态机测试通过。
- WorkflowStepRun 状态机测试通过。
- 非法转换被拒绝。

### Data

- WorkflowDefinitionVersion 默认定义存在。
- WorkflowStepRun 能关联 WorkflowRun。
- PromptVersion V1 回填存在。
- WorkflowRun 保存定义和 Prompt 快照。

### Baseline

- R0 Redis、Workflow、GameConfig、安全测试仍通过。
- quick Harness 返回 0。

## 验收标准

- [ ] 所有 R1 子任务完成。
- [ ] `.\tools\verify.ps1 -Profile quick` 返回 0。
- [ ] R1 相关 Maven 测试通过。
- [ ] migration 审查通过。
- [ ] 没有真实 Secret 进入 Git diff。
- [ ] 生成 R1 验收报告。
- [ ] R2 准入结论明确。

## 验证命令

```powershell
git status --short
git diff --check

cd backend-java
mvn test

cd ..
.\tools\verify.ps1 -Profile quick

rg -n "161764|password:\\s*123456|local-secret-key|change-this-to-a-strong-secret-key|\\bsk-[A-Za-z0-9]{20,}" `
  backend-java frontend-vue python-agent .env.example docker-compose.yml
```

## R1 报告模板

```markdown
# R1 Workflow Domain Report

## 环境
- 日期：
- 分支：
- Java/Maven/MySQL：

## Migration 结果
| 文件 | 目的 | 验证 |

## 领域模型结果
- Workflow 状态机：
- StepRun：
- PromptVersion：
- WorkflowRun Snapshot：

## 验证命令
| 命令 | 结果 |

## 已知风险
- 风险：
- 归属阶段：

## R2 准入结论
- PASS / BLOCKED
- 原因：
```

## 审查清单

- 是否还有旧 SQL 和 Flyway 互相冲突。
- 是否存在未测试的状态转换。
- 是否历史运行仍无法追踪定义或 Prompt。
- 是否 R1 偷偷做了 R2/R3 功能。
- 是否 quick Harness 真实运行。

## 完成定义

- R1 验收报告保存。
- R2 准入结论明确。
- 你能解释 R1 为 R2 Runner 提供了哪些地基。
