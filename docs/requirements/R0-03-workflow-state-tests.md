# R0-03：Workflow 状态测试

> 状态：`DONE`
>
> 前置任务：`R0-01`，建议先完成 `R0-02B`
>
> 推荐模型：`gpt-5.4`
>
> 任务类型：Baseline 行为测试

## 背景

`WorkflowServiceImpl` 当前同步执行三个 Agent 步骤，并更新 `workflow_run` 状态，但项目只有一个 `contextLoads` 测试。

后续 `R2` 会抽取 Workflow Runner。如果现在不固定成功、失败和权限行为，重构后无法判断是行为保持还是引入回归。

## 目标

使用纯单元测试固定当前 Workflow 的核心业务语义：

```text
合法用户和项目
-> 创建 RUNNING WorkflowRun
-> 顺序执行三个 Agent
-> 成功后标记 SUCCESS

任一步骤失败
-> 标记 FAILED
-> 保存错误和耗时
-> 保留原异常语义
```

## 代码入口

- `backend-java/src/main/java/com/example/gameworkbench/service/impl/WorkflowServiceImpl.java`
- `backend-java/src/main/java/com/example/gameworkbench/service/AgentRunService.java`
- `backend-java/src/main/java/com/example/gameworkbench/entity/WorkflowRun.java`
- `backend-java/src/main/java/com/example/gameworkbench/mapper/WorkflowRunMapper.java`
- `backend-java/src/main/java/com/example/gameworkbench/mapper/GameProjectMapper.java`
- `backend-java/src/main/java/com/example/gameworkbench/mapper/AgentArtifactMapper.java`
- `backend-java/src/test/java/`

## 范围

允许：

- 新增 `WorkflowServiceImplTest`。
- 使用 Mockito mock Mapper 和 AgentRunService。
- 新增测试 fixture、builder 和 ArgumentCaptor helper。
- 验证 WorkflowRun 与 Artifact 的关键落库参数。
- 为测试可读性做不改变生产行为的极小可测试性调整，但必须先说明必要性。

## 非目标

- 不抽取 WorkflowRunner。
- 不新增 WorkflowStepRun。
- 不修改数据库结构。
- 不接入 RabbitMQ。
- 不改变同步执行方式。
- 不合并普通 Workflow 和 Demo Workflow。
- 不测试真实 MySQL、Redis、Python 或 LLM。
- 不修改前端。

## 约束

- 以 `WorkflowServiceImpl.run` 公共入口为主要被测入口。
- 不直接测试私有方法。
- 每个测试只表达一个业务规则。
- 测试不得依赖执行顺序。
- 不使用真实时间做精确毫秒断言，只检查非负或已设置。
- 不为了测试方便改变现有 API。

## 必测场景

### 成功

- 用户拥有项目。
- 三次 Agent 调用依次成功。
- 创建一条初始 `RUNNING` WorkflowRun。
- 三个 AgentType 的顺序正确。
- 创建三个 Artifact。
- 最终状态为 `SUCCESS`。
- summary、耗时和更新时间被写入。

建议测试名：

```text
shouldCompleteWorkflowWhenAllStepsSucceed
```

### 业务失败

- 第二或第三步骤抛出 `BusinessException`。
- WorkflowRun 最终为 `FAILED`。
- 保存原业务错误信息。
- 不执行后续步骤。
- 原 `BusinessException` 继续向上抛出。

建议测试名：

```text
shouldMarkWorkflowFailedWhenAgentStepThrowsBusinessException
```

### 未知异常

- Agent 或 Mapper 抛出非业务异常。
- WorkflowRun 标记 `FAILED`。
- 对外转换为 `SYSTEM_ERROR`。
- 日志与数据库不保存敏感实现细节。

建议测试名：

```text
shouldConvertUnexpectedExceptionToSystemError
```

### 权限

- `userId == null` 时拒绝。
- 项目不存在或不属于用户时拒绝。
- 拒绝路径不创建 WorkflowRun。

建议测试名：

```text
shouldRejectUnauthorizedUser
shouldRejectProjectNotOwnedByUser
```

## 验收标准

- [ ] 至少覆盖成功、业务失败、未知异常、未认证、无项目权限。
- [ ] 成功场景验证三个 Agent 的顺序和类型。
- [ ] 失败场景验证后续步骤不再执行。
- [ ] WorkflowRun 的初始与最终状态都有断言。
- [ ] Artifact 数量和关联 AgentRun 被验证。
- [ ] 测试不依赖外部服务。
- [ ] 不引入 Workflow Runner 或新表。
- [ ] 目标测试与 quick Harness 全部通过。

## 验证命令

```powershell
cd backend-java
mvn -Dtest=WorkflowServiceImplTest test

cd ..
.\tools\verify.ps1 -Profile quick
```

## 审查清单

- 测试是否验证业务状态而不只是返回值。
- 是否真的验证步骤顺序。
- BusinessException 与未知异常是否分别覆盖。
- Mapper mock 是否掩盖了需要验证的状态更新。
- 是否出现为了测试而扩大的生产重构。
- 测试数据是否清楚表达 user/project 归属。

## 完成定义

- 验收标准全部通过。
- 目标测试可重复运行。
- quick Harness 返回 0。
- diff 主要位于测试目录。
- 已记录当前 Workflow 的受保护行为。
- 后续 R2 重构可以直接使用这些测试判断兼容性。
- 你能说明成功和失败时 WorkflowRun 分别如何变化。

