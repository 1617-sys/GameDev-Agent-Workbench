# R4-07: 前端 SSE 生命周期、重连与去重

> 状态：`TODO`
>
> 前置任务：`R4-03`、`R4-05`、`R4-06`
>
> 推荐模型：`gpt-5.5`
>
> 任务类型：实时状态同步 / 前端竞态测试

## 背景

运行详情页已经能读取快照，但需要实时显示后台状态变化。网络抖动、浏览器休眠、重复 EventSource 回调和路由切换都可能造成重复事件、乱序覆盖或连接泄漏；这些都不能改变后台任务本身。

## 目标

在 WorkflowRun Store 中实现受控 SSE 生命周期：

```text
enter detail route
-> loadRun(snapshot)
-> connect(runUuid, lastSequence)
-> apply only newer events
-> reconnect with bounded backoff
-> reload snapshot after reconnect/error gap
-> terminal or route leave -> close EventSource
```

## 范围

允许：

- 实现 EventSource/SSE 客户端封装、Last-Event-ID/sequence 传递、连接状态和 bounded exponential backoff。
- 将 snapshot/incremental/heartbeat/terminal/error 事件映射为 Store action。
- 在重连后强制或按协议重新读取快照，处理事件缺口、重复、乱序与服务端重放。
- 实现路由切换、组件卸载、终态 Run、用户登出时的统一关闭和 timer 清理。
- 添加 Fake EventSource/Store 单元测试与浏览器集成测试，覆盖断线、重复、乱序、快速切换 Run、终态、403/404。

## 非目标

- 不修改后端 WorkflowRun 状态、重试/取消语义或 RabbitMQ Consumer。
- 不通过 SSE 发送提交请求、认证 Secret、完整 Prompt 或模型原始输出。
- 不使用 SSE 连接状态判断后台任务是否存在或是否成功。
- 不实现 WebSocket、多人协作或全局通知中心。
- 不改变 Phaser Runtime。

## 约束

- 同一 workflowRunUuid 在单个浏览器 session 中最多一个活动 EventSource；新连接前必须关闭旧连接。
- 所有增量事件必须满足 `sequence > lastSequence` 才可应用；重复或过期事件只记录调试信息。
- 连接异常不能把 Run 标记 FAILED；错误状态来自后端 snapshot/event。
- 重连次数、最大退避和是否停止重连需可配置；认证失败/不存在/终态需停止无意义重连。
- snapshot 先于事件合并；当发现 gap 或重连完成后以 GET 快照纠偏。
- 清理 EventSource、retry timer、event listener 时不得影响其他 Run 或 Phaser 页面。

## 验收标准

- [ ] 刷新或进入详情页先加载 snapshot，再订阅后续事件。
- [ ] 重复/乱序事件不会重复增加步骤、回退终态或覆盖更高 sequence 状态。
- [ ] 临时断线后按受限退避重连并恢复到服务端真实状态。
- [ ] 关闭/离开页面、切换 Run、终态完成、登出后连接和 timer 均被释放。
- [ ] 403/404/终态等不可恢复场景停止重连并显示可理解状态。
- [ ] 连接失败从不影响后台 WorkflowRun/Consumer，测试明确证明这一点。

## 验证命令

```powershell
cd frontend-vue
npm run test:unit
npm run test:e2e
npm run build

cd ..
.\tools\verify.ps1 -Profile quick
```

## 审查清单

- 是否为同一 Run 打开多个 EventSource 或遗留 retry timer。
- 是否按到达顺序盲目应用事件，不比较 sequence。
- 是否断线时把后台任务错误标记为 FAILED/停止。
- 是否重连后不拉取快照，长期停在过期状态。
- 是否在 route leave 时关闭了错误 Run 的订阅或影响 Phaser 页面。

## 完成定义

- 前端实时状态体验能耐受断线、重复和乱序，同时始终以持久化快照为准。
- SSE 被限制为可释放的订阅通道，而不是业务执行通道。
