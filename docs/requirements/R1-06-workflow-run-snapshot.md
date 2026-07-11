# R1-06：WorkflowRun 定义与 Prompt 快照字段

> 状态：`DONE`
>
> 前置任务：`R1-03`、`R1-05`
>
> 推荐模型：`gpt-5.5`
>
> 任务类型：数据库迁移 / 兼容改造

## 背景

R1 引入 WorkflowDefinitionVersion 和 PromptVersion 后，WorkflowRun 需要冻结一次运行使用的定义版本和 Prompt 版本，否则历史运行仍不可复现。

本任务只扩展 WorkflowRun 保存快照，不要求现有执行器完全按定义驱动。

## 目标

扩展 WorkflowRun：

```text
workflow_definition_version_id
workflow_definition_snapshot
prompt_version_snapshot
schema_version
attempt
status_version
```

并在创建 WorkflowRun 时写入必要快照。

## 范围

允许：

- 新增 Flyway migration。
- 扩展 WorkflowRun Entity/Mapper/VO。
- 在现有 Workflow 创建路径中写入定义和 Prompt 快照。
- 增加创建成功路径测试。
- 保留旧字段兼容。

## 非目标

- 不改提交接口为 202。
- 不接 Outbox。
- 不接 MQ。
- 不实现恢复扫描。
- 不实现前端快照展示。
- 不移除旧同步执行流程。

## 约束

- 新增字段先允许 nullable，避免破坏历史数据。
- 写快照失败时不能伪装为成功创建。
- Snapshot 是历史证据，不应被后续 ACTIVE 切换修改。
- GameConfig schema version 使用 `docs/game-config-schema.md` 当前契约，后续 R5 再进入评测。

## 验收标准

- [ ] WorkflowRun 表具备定义版本和快照字段。
- [ ] 新建 WorkflowRun 会保存当前定义版本引用或快照。
- [ ] PromptVersion 切换后，旧 WorkflowRun 快照不变。
- [ ] 历史数据兼容，空字段不会导致查询接口崩溃。
- [ ] R0 Workflow 状态测试仍通过。
- [ ] quick Harness 通过。

## 验证命令

```powershell
cd backend-java
mvn -Dtest=WorkflowServiceImplTest,*WorkflowRun*Test test

cd ..
.\tools\verify.ps1 -Profile quick
```

## 审查清单

- 是否把快照字段设计成每次查询动态读取 ACTIVE。
- 是否新增非空字段导致旧数据升级失败。
- 是否把 R2 Runner 抽象提前塞进来。
- 是否把 schema version 和 GameConfig 真实字段搞混。
- 是否前端或 Python 被无关修改。

## 完成定义

- WorkflowRun 快照能力可被测试证明。
- 旧 API 查询仍兼容。
- R1 数据地基已能支撑 R2 Runner。
