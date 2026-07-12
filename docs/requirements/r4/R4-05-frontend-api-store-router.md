# R4-05: Vue API Client、WorkflowRun Store 与路由地基

> 状态：`TODO`
>
> 前置任务：`R4-00`、`R4-01`、`R4-03`
>
> 推荐模型：`gpt-5.5`
>
> 任务类型：前端状态架构 / 单元测试

## 背景

当前 `App.vue` 直接使用 `fetch`、维护请求状态和旧 SSE 事件；应用没有围绕 `workflowRunUuid` 的可恢复前端状态。R4 先抽离 API、Store 和路由基础，再拆 UI，避免只是把一个巨型组件拆成几个仍互相修改状态的组件。

## 目标

形成清晰的前端分层：

```text
src/api/httpClient.js
src/api/workflowApi.js
src/stores/workflowRunStore.js
src/router/... or tested route adapter
src/views/WorkbenchView.vue
src/views/WorkflowRunView.vue
```

Store 以 `workflowRunUuid` 为 key 管理 snapshot、steps、artifacts、lastSequence、connectionState、loading/error 和可用动作；组件只能通过 Store/明确 action 读写状态。

## 范围

允许：

- 新增 API Client、统一认证/错误处理、workflow query/submit/cancel/retry/SSE API 封装。
- 新增 Vue Composition API Store；可按设计引入 `vue-router` 或轻量、可测试的 History adapter 以支持 `/workflow-runs/{uuid}` 深链接。
- 将 API 基地址、认证 token、请求超时和错误码处理从 App.vue 移出。
- 实现 Store 的 `loadRun`、`applySnapshot`、`applyEvent`、`connect`/`disconnect` action 声明或桩位。
- 添加前端单元测试，覆盖 API 错误、状态合并、事件 sequence 去重、路由参数解析和 Store 清理。
- 保持 Phaser `GameDemoPage.vue` 与 GameConfig Runtime 可独立访问。

## 非目标

- 不在本任务完成完整运行详情 UI、响应式布局或视觉重设计。
- 不直接调用旧 `/api/demo/game/stream` 作为新 Store 数据源。
- 不在组件里实现后端状态机、重试策略或消息可靠性。
- 不做 R5 指标/评测/RAG 展示。
- 不修改 Java API、SSE 协议或 Python Agent。

## 约束

- API 层是唯一 `fetch`/HTTP 边界；Vue 组件不得散落拼接 API URL 和 Authorization Header。
- Store 是 WorkflowRun 前端单一事实源；同一 Run 不允许 App.vue、Stepper、详情页各自维护可变副本。
- Store 合并事件时只接受 `sequence > lastSequence`；snapshot 可以替换旧状态并更新 lastSequence。
- 路由刷新必须能从 URL 得到 workflowRunUuid 再调用 `loadRun`，不能依赖上一页内存。
- 组件卸载/路由切换必须通过 Store 统一清理订阅和 listener。
- 新依赖必须有明确理由、锁定版本和对应测试，不为“看起来工程化”重复引入状态库。

## 验收标准

- [ ] 新运行 API 均通过统一 Client 调用，App.vue 不再直接拼接这些 v1 请求。
- [ ] Store 可按 UUID 加载/保存快照，sequence 重复或倒退事件不改变状态。
- [ ] 浏览器访问运行详情 URL 或刷新后能恢复读取对应 Run，而不依赖旧 SSE 请求。
- [ ] API 401/403/404/网络异常被转换为可展示的统一错误状态，不泄露内部响应。
- [ ] 路由切换/组件卸载不会遗留 EventSource/listeners。
- [ ] GameConfig 测试和 Vue build 继续通过，新增 Store/API 测试可独立运行。

## 验证命令

```powershell
cd frontend-vue
npm run test:unit
npm run test:game-config
npm run build

cd ..
.\tools\verify.ps1 -Profile quick
```

## 审查清单

- 是否仍在组件中直接 fetch、组装 Header 或解析全局错误。
- 是否存在多个可变 WorkflowRun 副本导致 UI 互相覆盖。
- 是否接收重复/乱序事件后仍追加步骤或回退终态。
- 是否刷新路由后需要依赖上一页 state 才能工作。
- 是否加入大型依赖却没有清晰职责和测试。

## 完成定义

- R4 前端拥有 API、状态和路由三层清晰边界。
- 后续页面和 SSE 连接只需调用 Store，不再直接操纵全局请求状态。
