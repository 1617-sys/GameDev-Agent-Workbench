# R0-02A：Redis 锁分析与失败测试

> 状态：`RED`
>
> 前置任务：`R0-01`
>
> 推荐模型：`gpt-5.5`
>
> 任务类型：高风险并发行为分析 + TDD 红灯阶段

## 背景

`DemoStreamServiceImpl` 使用 Redis 防止同一用户重复启动 Demo Workflow，但当前实现存在明显风险：

- `setIfAbsent` 返回值与进入执行流程的条件可能相反。
- 未获得锁的请求也会在 `finally` 中删除锁。
- 锁 value 不是稳定的 owner token。
- `RedisService` 只有无条件 `delete`，不能表达“仅 owner 释放”。
- 用户鉴权发生在 Redis key 构造和抢锁之后。
- 当前没有任何 Redis 锁或 DemoStream 单元测试。

本任务只建立能够稳定暴露这些问题的失败测试，不修复生产实现。

## 目标

通过测试明确并发锁的目标语义：

```text
只有抢锁成功的请求可以执行 Workflow
未抢到锁的请求不能执行，也不能删除其他请求的锁
只有实际获得锁的请求才进入释放流程
空 userId 不得访问 Redis
```

测试应在当前错误实现上失败，并为 `R0-02B` 提供可重复的修复证据。

## 代码入口

- `backend-java/src/main/java/com/example/gameworkbench/service/impl/DemoStreamServiceImpl.java`
- `backend-java/src/main/java/com/example/gameworkbench/service/RedisService.java`
- `backend-java/src/main/java/com/example/gameworkbench/service/impl/RedisServiceImpl.java`
- `backend-java/src/main/java/com/example/gameworkbench/config/RedisConfig.java`
- `backend-java/src/test/java/`

## 范围

允许：

- 新增 `DemoStreamServiceImpl` 的单元测试。
- 新增测试专用 fixture、builder 或 helper。
- 使用项目已有 JUnit 5、Mockito 和 Spring Test。
- 使用同步 Executor 代替真实异步线程，使测试稳定执行。
- 记录失败测试名称、断言差异和根因分析。

## 非目标

- 不修改 `DemoStreamServiceImpl` 生产实现。
- 不修改 `RedisService` 或 `RedisServiceImpl`。
- 不新增 Lua 解锁脚本。
- 不启动真实 Redis。
- 不接入 Testcontainers。
- 不接入 RabbitMQ。
- 不修改 Workflow Runner、前端或 Python Agent。
- 不为了让测试变绿而降低断言标准。

## 约束

- 测试必须是纯单元测试，不依赖本机 Redis。
- 不使用 `Thread.sleep` 等不稳定等待方式。
- 不测试私有方法本身，而是通过 `streamGameDemo` 的可观察行为验证。
- 测试名称必须表达业务语义。
- 当前生产代码造成的失败必须被保留并报告。
- 不覆盖或还原工作区中的其他已有修改。

## 必测场景

### 1. 抢锁成功

Given：

- `userId` 合法。
- `setIfAbsent` 返回 `true`。

Then：

- Workflow 应进入第一个 Agent 步骤。
- 当前实现若没有执行，应测试失败。

建议测试名：

```text
shouldExecuteWorkflowWhenLockIsAcquired
```

### 2. 抢锁失败

Given：

- `setIfAbsent` 返回 `false`。

Then：

- 不得调用 `AgentRunService`。
- 不得调用 `GameBuildClient`。
- 应以明确的重复执行结果结束 SSE。

建议测试名：

```text
shouldRejectWorkflowWhenLockIsNotAcquired
```

### 3. 未持锁请求不得释放

Given：

- `setIfAbsent` 返回 `false`。

Then：

- 不得调用当前无条件 `delete`。

该测试先证明“未持锁请求不能进入释放路径”；原子 owner compare-and-delete 由 `R0-02B` 补齐。

建议测试名：

```text
shouldNotDeleteLockWhenAcquisitionFails
```

### 4. 未认证请求

Given：

- `userId` 为 `null`。

Then：

- 不调用 Redis。
- 不调用 Agent。
- SSE 以未认证错误结束。

建议测试名：

```text
shouldRejectUnauthorizedRequestBeforeAccessingRedis
```

## 验收标准

- [ ] 新增至少四个上述业务测试。
- [ ] 测试不依赖真实 Redis、MySQL、Python 或浏览器。
- [ ] 测试稳定复现当前锁判断或释放问题。
- [ ] 至少一个测试在当前实现上失败，形成明确红灯证据。
- [ ] 失败报告包含测试名、期望、实际行为和对应生产代码位置。
- [ ] 未修改任何生产代码。
- [ ] 没有改动任务范围之外的文件。

## 验证命令

```powershell
cd backend-java
mvn -Dtest=DemoStreamServiceImplTest test
```

本任务预期：

```text
目标测试失败
-> 证明当前实现违反锁语义
-> 将失败证据交给 R0-02B
```

由于本任务是 TDD 红灯阶段，不要求 `quick` Harness 通过；不得把失败测试描述成“项目验证通过”。

## 审查清单

- 测试是否真的经过公开入口触发行为。
- Mockito 是否只 mock 外部依赖，没有 mock 被测业务本身。
- 抢锁成功和失败的返回值是否没有写反。
- 未持锁释放是否有独立断言。
- 测试是否会因异步时序偶发通过或失败。
- 是否意外修改了生产实现。

## 完成定义

- 所有验收标准已逐条核对。
- 失败测试可以连续运行并得到一致结果。
- 已记录当前失败证据和根因假设。
- Git diff 只包含测试和必要测试 helper。
- 没有提交或合并一个声称“全绿”的错误 baseline。
- 任务状态更新为 `RED`，下一步唯一动作是执行 `R0-02B`。
