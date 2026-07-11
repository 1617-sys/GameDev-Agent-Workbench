# R2-05: Demo SSE 旧入口迁移与事件桥接

> 状态：`DONE`
>
> 前置任务：`R2-03`、`R2-04`
>
> 推荐模型：`gpt-5.5`
>
> 任务类型：兼容改造 / SSE 回归

## 背景

`DemoStreamServiceImpl` 当前在独立线程里重复四步 Agent 编排、字符串上下文拼接、GameBuild 调用、Redis 锁管理和 SSE 推送。R2 要让它复用 Runner，同时暂时保留现有 SSE 体验，直到 R4 才将 SSE 升级为持久化状态订阅。

## 目标

改造后的职责如下：

```text
DemoStreamServiceImpl
-> 创建 SseEmitter 与现有 Redis 互斥保护
-> 创建/取得 DEMO 工作流运行
-> 在 demoStreamExecutor 中调用 WorkflowRunner
-> WorkflowExecutionListener 映射为兼容的 GameDemoStreamEventVO
-> GameBuild 后处理事件
-> 完成或失败后释放 owner-aware Redis 锁
```

Demo 的四步 Agent 流程必须由同一 Runner 驱动，SSE 只观察并转译事件，不能成为步骤编排的第二份事实来源。

## 范围

允许：

- 新增 Demo 专用的 listener/adapter，把 Runner 领域事件映射为现有 SSE event VO。
- 将四步 Demo（含 `GAME_CONFIG_GENERATE`）迁移为使用 WorkflowRunner 和 R1 的 DEMO 定义快照。
- 保留现有 `demoStreamExecutor`、SseEmitter timeout、Redis owner token 获取/释放和 GameBuild 调用。
- 在 Runner 完成且 GameConfig 合格后执行现有 GameBuild，保持 `GAME_BUILD` 与 `COMPLETED` 事件兼容。
- 增加服务级测试覆盖事件顺序、成功、步骤失败、SSE 发送异常、锁未获得、锁 owner 释放。

## 非目标

- 不将 SSE 改造成 R4 的持久化订阅 API。
- 不让 SSE 断线后自动恢复或新增事件 sequence。
- 不接 MQ、Outbox、重试、DLQ、分布式执行抢占。
- 不删除 Redis 锁；R3 才补数据库/消息级最终幂等。
- 不重写 Phaser Runtime 或 GameBuild Python 服务。

## 约束

- Runner 必须完全不知道 `SseEmitter`；仅 listener adapter 处理 SSE I/O。
- SSE 发送失败、超时、客户端断开不得中断已开始的同步 Runner 业务执行；只记录可定位日志。
- Redis 未取得锁时不可创建或执行 Demo Agent 流程。
- 只有成功取得锁的 owner 才能释放，继续复用 R0 的原子 owner-aware 释放。
- 只有所有四步成功且 GameConfig 通过当前契约时，才允许调用 GameBuild 并发出 `COMPLETED/SUCCESS`。
- 保持当前事件阶段、状态、projectUuid、artifactUuid、demoUrl 的兼容字段语义。

## 验收标准

- [ ] Demo 四步流程不再在 DemoStreamServiceImpl 中手工排序或拼接上下文。
- [ ] 现有客户端仍能收到 `WORKFLOW_STARTED`、每步进度、`GAME_BUILD`、`COMPLETED` 或失败事件。
- [ ] 已成功步骤不会因 listener 发送失败或重复订阅而重新调用 Agent。
- [ ] 未取得 Redis 锁时不调用 Runner 或 GameBuild，且不会释放他人的锁。
- [ ] GameConfig 步骤失败或校验失败时不调用 GameBuild。
- [ ] Demo 成功路径仍得到可试玩 URL，R0 Redis 测试与相关 SSE 测试通过。

## 验证命令

```powershell
cd backend-java
mvn -Dtest=DemoStreamServiceImplTest,*DemoStream*Test,*WorkflowRunner*Test,*RedisService*Test test
mvn test

cd ..
.\tools\verify.ps1 -Profile quick

cd frontend-vue
npm run test:game-config
npm run build
```

## 审查清单

- 是否把 `SseEmitter` 注入 Runner 或 StepExecutor。
- 是否因 SSE 客户端断开而提前中断业务并留下 RUNNING 状态。
- 是否因 listener 异常重复执行 Agent 或 GameBuild。
- 是否在迁移时弱化 R0 Redis owner token/TTL 规则。
- 是否改变旧 SSE 事件字段、顺序或成功/失败语义而没有回归测试。

## 完成定义

- Demo 入口已成为 Runner + SSE 事件桥接，步骤逻辑不再复制。
- 保留可试玩体验与锁保护，同时为 R4 的订阅式 SSE 留出干净边界。
