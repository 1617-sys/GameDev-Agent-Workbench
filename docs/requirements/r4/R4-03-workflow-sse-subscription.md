# R4-03: WorkflowRun SSE 订阅 API

> 状态：`TODO`
>
> 前置任务：`R4-01`、`R4-02`
>
> 推荐模型：`gpt-5.5`
>
> 任务类型：SSE 服务端订阅 / 生命周期测试

## 背景

R4-02 已将状态变化持久化为有序事件。此任务提供新的 SSE 订阅端点，让浏览器在不触发任务执行的前提下获得当前快照与后续增量。

## 目标

新增：

```http
GET /api/v1/workflow-runs/{workflowRunUuid}/events
Accept: text/event-stream
Last-Event-ID: <sequence> (optional)
```

连接成功后按以下语义工作：

```text
authorize owner
-> send snapshot event (current persistent read model + last sequence)
-> replay events after Last-Event-ID when applicable
-> subscribe to in-process publisher for new persisted events
-> cleanup on completion/timeout/error/client disconnect
```

## 范围

允许：

- 新增 SSE Controller、SubscriptionService、SseEmitter registry/adapter 和事件序列化 DTO。
- 复用 R4-01 的权限与快照 Read Model、R4-02 的事件查询/publisher。
- 实现 snapshot、incremental、heartbeat/comment、终态完成、Last-Event-ID 或等价 sequence 重连协议。
- 增加多订阅者、无权限、断开、终态、事件回放、发送失败和资源清理测试。
- 记录订阅连接/断连日志，但不记录敏感 payload。

## 非目标

- 不在 SSE 端点中创建、取消、重试或执行 WorkflowRun。
- 不继续使用旧 `POST /api/demo/game/stream` 作为新运行页事件来源。
- 不实现 WebSocket、轮询替代页面或跨服务实时总线。
- 不把 SseEmitter 放入 R2 Runner、R3 Consumer 或领域实体。
- 不做前端 EventSource/Store 改造。

## 约束

- 建连与重连均必须提供持久化 snapshot；不能只依赖内存中“之后的事件”。
- 所有订阅先验证资源归属；未知/无权限 Run 不得建立连接或泄露事件。
- 事件只来自 R4-02 已持久化记录；发送失败仅影响该连接，不得回写 WorkflowRun 状态或中断业务。
- Emitter 必须在 timeout、completion、error、客户端断开和终态后清理；不能无限保存。
- Last-Event-ID 非法、过旧或未来 sequence 必须有明确回退到 snapshot 的规则。
- 同一事件可因网络重连重复到达，协议允许重复，前端由 sequence 去重。

## 验收标准

- [ ] 有权限用户连接后先收到包含当前状态和 lastSequence 的 snapshot。
- [ ] 提供 Last-Event-ID 时可收到之后的持久化增量事件，顺序稳定。
- [ ] 多个订阅者都能收到同一 Run 的新事件，断开一个不影响其他订阅者或任务执行。
- [ ] 发送失败/客户端断开后 Emitter 被清理，WorkflowRun/Consumer/R2 Runner 不受影响。
- [ ] 终态 Run 可得到快照并在策略允许时完成连接，不保持无意义长连接。
- [ ] 无权限、未知 UUID、错误 Last-Event-ID 均有测试和安全响应。

## 验证命令

```powershell
cd backend-java
mvn -Dtest=*WorkflowSse*Test,*WorkflowSubscription*Test,*WorkflowRunEvent*Test test
mvn test

cd ..
.\tools\verify.ps1 -Profile quick
.\tools\verify.ps1 -Profile integration
```

## 审查清单

- 是否在 SSE Controller 触发 Runner、消息发布或数据库状态更新。
- 是否只做内存广播、重连后没有 snapshot/回放。
- 是否没有清理 timeout/error/completion 的 Emitter。
- 是否未检查用户归属或给终态 Run 保留无限连接。
- 是否让发送异常影响异步工作流状态。

## 完成定义

- 新 SSE 端点成为持久化 WorkflowRun 的只读订阅投影。
- 浏览器断线与后台异步执行彻底解耦。
