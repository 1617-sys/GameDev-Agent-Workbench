# R4-06: 工作台提交入口与 WorkflowRun 详情页

> 状态：`TODO`
>
> 前置任务：`R4-04`、`R4-05`
>
> 推荐模型：`gpt-5.4`（页面搬运） / `gpt-5.5`（状态流审查）
>
> 任务类型：前端页面迁移 / 交互与响应式测试

## 背景

有了查询 API、命令 API 与 Store 后，用户需要一个真正的异步提交和运行查看入口。R4 不再让用户停留在一个长 SSE 生成面板等待结果，而是提交后立即进入可恢复的运行详情页。

## 目标

实现两个主视图：

```text
WorkbenchView
-> collect workflow idea/input
-> POST async workflow submit with generated Idempotency-Key
-> navigate to /workflow-runs/{workflowRunUuid}

WorkflowRunView
-> render Store snapshot
-> status / steps / attempt / time / error
-> artifacts and playable Demo link
-> cancel / retry actions based on server-provided capability
```

## 范围

允许：

- 从 App.vue 抽取 WorkbenchView、WorkflowRunView、步骤/状态/错误/Artifact 等展示组件。
- 通过 R4-05 API/Store 提交异步请求并路由跳转；生成并复用一次提交的 Idempotency-Key。
- 展示 WorkflowRun 状态、步骤依赖/顺序、attempt、耗时、错误摘要、Artifact 与 Demo 打开入口。
- 接入 cancel/retry action，提供 loading/disabled/成功后的 Store 刷新状态。
- 保留 Legacy Demo 入口的清晰兼容入口或迁移提示，但不删除旧 API。
- 添加组件测试和浏览器 smoke test，覆盖提交跳转、运行中、成功、失败、取消、重试、空 Artifact。

## 非目标

- 不在前端直接轮询/调用 Runner、MQ、Redis 或 Python Agent。
- 不把旧 Demo POST SSE 请求作为新运行详情页的主要数据源。
- 不实现 R5 的 Prompt/Token/评测展示，也不实现 R6 RAG 来源页面。
- 不重新设计 Phaser 游戏运行时或修改 GameConfig 结构。
- 不删除旧同步/旧 Demo 页面，兼容移除留在后续阶段。

## 约束

- 提交成功的唯一跳转依据是服务端返回的 workflowRunUuid；页面不能自造 Run ID 或假设任务已 RUNNING。
- 按后端 snapshot 的状态和 capability 决定按钮可用性，不能在 UI 硬编码完整状态转移表。
- 相同用户重复点击提交时使用同一个 pending Idempotency-Key；新一次明确提交才生成新 key。
- 任何动作返回后都必须刷新/合并服务器真相，不能只乐观修改终态。
- 错误、空数据和无权限页面须有可读状态，不得覆盖前后内容或在移动端溢出。
- Artifact 展示使用服务端安全 URL/UUID，不将模型输出当作可执行 HTML。

## 验收标准

- [ ] 提交异步工作流后立即跳转到对应 WorkflowRun 详情路由。
- [ ] 页面在 RUNNING、SUCCESS、FAILED、CANCELED、空 Artifact、网络错误时均有稳定展示。
- [ ] 刷新详情页可通过 URL 和 Store 重新加载，不再依赖提交页内存。
- [ ] 取消/重试仅在服务端允许时发起，动作完成后显示新的持久化状态。
- [ ] Artifact/Demo 入口仅在服务端声明可用时显示，且可打开现有 Phaser 页面。
- [ ] 桌面与 375px 宽移动视口中无文字遮挡、横向溢出或互相覆盖的控件。

## 验证命令

```powershell
cd frontend-vue
npm run test:unit
npm run test:e2e
npm run test:game-config
npm run build

cd ..
.\tools\verify.ps1 -Profile quick
```

## 审查清单

- 是否提交后仍等待长 SSE 而不跳转详情页。
- 是否自行猜测 RUNNING/SUCCESS 或只用乐观 UI 覆盖服务端状态。
- 是否重复点击产生不同 Idempotency-Key 和多条任务。
- 是否隐藏失败/取消/空 Artifact 状态或在手机端内容重叠。
- 是否把 Artifact 模型内容直接插入 HTML。

## 完成定义

- 用户可从提交、跳转、查看到刷新恢复完成一次异步运行体验。
- App.vue 不再承担提交、详情、事件和展示的全部责任。
