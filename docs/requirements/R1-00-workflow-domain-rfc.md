# R1-00：Workflow 数据与领域地基 RFC

> 状态：`DONE`
>
> 前置任务：`R0-ACCEPTANCE`
>
> 推荐模型：`gpt-5.5`
>
> 任务类型：阶段设计冻结 / 高风险数据建模

## 背景

R0 已经固定 Baseline，当前系统可以通过 quick Harness，并且 Redis、Workflow、GameConfig、安全配置都有基础回归保护。

R1 的目标不是重写执行流程，而是在现有可运行链路上建立可版本化、可追踪、可迁移的数据地基：

```text
WorkflowDefinitionVersion
+ WorkflowStepRun
+ PromptVersion
+ WorkflowRun snapshot
+ 状态机合法转换策略
+ Flyway 增量迁移
```

## 目标

产出一份可执行的 R1 设计契约，冻结：

- R1 新增表和字段。
- 旧表兼容策略。
- WorkflowRun 和 WorkflowStepRun 状态机。
- PromptVersion 不可变规则。
- Flyway baseline 和迁移顺序。
- R1 子任务依赖关系。
- 每个子任务的验证命令。

## 范围

允许：

- 阅读现有实体、Mapper、SQL 初始化脚本和 R0 报告。
- 新增或更新 R1 设计文档。
- 明确 R1 子任务边界。
- 画出数据关系和状态流转。
- 给出迁移命名和执行顺序。

## 非目标

- 不修改业务代码。
- 不新增 Flyway 依赖。
- 不创建数据库表。
- 不实现 Runner、MQ、Outbox、RAG、评测。
- 不删除旧接口、旧 Entity 或旧 SQL。
- 不改前端页面。

## 输入材料

- `docs/README.md` 的 R1、数据模型、状态机章节。
- `docs/AI_COLLABORATION.md` 的任务卡和审查规范。
- `docs/reports/R0-baseline-report.md`。
- `docs/game-config-schema.md`。
- `backend-java/src/main/resources/db/`
- `backend-java/src/main/java/com/example/gameworkbench/entity/`
- `backend-java/src/main/java/com/example/gameworkbench/service/impl/WorkflowServiceImpl.java`

## 交付物

- `docs/requirements/R1-workflow-domain-design.md`

建议包含：

```text
背景
目标
范围
非目标
现有行为
目标行为
表结构草案
字段兼容策略
状态机
PromptVersion 规则
迁移顺序
验证命令
风险与回滚
```

## 验收标准

- [ ] R1 新增表、字段、索引和唯一约束有明确草案。
- [ ] 旧表兼容策略清楚，说明哪些字段本阶段只新增不强制使用。
- [ ] WorkflowRun 状态机和 StepRun 状态机有合法转换表。
- [ ] PromptVersion 创建后不可变，ACTIVE 切换只影响未来运行。
- [ ] 说明 GameConfig schema version 如何与 Artifact 或 Evaluation 关联。
- [ ] 明确每个 R1 子任务的依赖顺序。
- [ ] 明确哪些能力推迟到 R2/R3/R5。
- [ ] 不产生业务代码 diff。

## 验证命令

```powershell
git status --short
git diff -- docs/requirements/R1-workflow-domain-design.md
```

## 审查清单

- 是否把 R2 Runner 或 R3 MQ 偷偷塞进 R1。
- 是否一次性要求删除旧表或旧接口。
- 是否存在不可回滚的破坏性迁移。
- 是否遗漏历史数据升级路径。
- 是否能解释 R1 为什么先做数据地基，而不是先改执行器。

## 完成定义

- 设计文档已保存。
- R1 子任务可以按文档独立执行。
- 你能用自己的话说明 R1 的边界：只建数据与领域地基，不改完整执行模型。
