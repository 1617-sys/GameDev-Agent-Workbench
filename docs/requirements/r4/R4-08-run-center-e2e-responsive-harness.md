# R4-08: 运行中心 E2E、响应式与可访问性 Harness

> 状态：`TODO`
>
> 前置任务：`R4-01`、`R4-03`、`R4-04`、`R4-05`、`R4-06`、`R4-07`
>
> 推荐模型：`gpt-5.4`（测试脚本） / `gpt-5.5`（失败审查）
>
> 任务类型：E2E 验证 / 前端质量基线

## 背景

R4 的交付不是“页面看起来能打开”，而是用户从异步提交、进入运行页、断线/刷新恢复、查看产物到取消/重试的关键路径均有自动化证据，并且桌面和移动端不会出现遮挡或不可操作控件。

## 目标

建立 R4 前端 Harness：

```text
browser test
-> login/test auth context
-> submit async workflow
-> route to run detail
-> snapshot + SSE updates
-> refresh/reconnect/dedup
-> terminal/artifact/cancel/retry UI
-> desktop + mobile screenshot/layout assertions
```

优先使用可控的测试 Agent 或真实本地 R3 测试链路，不调用付费 LLM。

## 范围

允许：

- 新增/配置前端单元测试、Vue component test、浏览器 E2E 工具和 `npm` scripts。
- 扩展 `tools/verify.ps1 -Profile e2e` 或创建清晰的 R4 前端测试入口。
- 添加测试专用后端 profile/Fake Agent、测试数据清理、稳定等待和事件注入能力。
- 用 Playwright 或同等浏览器工具验证 1440px 桌面与 375px 移动视口，检查截图、关键元素可见性、无横向溢出与无重叠。
- 覆盖 submit、refresh、SSE reconnect/dedup、终态、错误、cancel/retry、Artifact/Demo link、无权限。

## 非目标

- 不引入真实生产 LLM、云 MQ、真实用户凭证或付费外部服务。
- 不做大规模性能压测、视觉营销页或无关 UI 重设计。
- 不通过手工观察替代可重复的自动化验证。
- 不把测试 hook 暴露给生产环境。
- 不实现 R5/R6 详情页。

## 约束

- E2E 等待必须基于明确 API/DOM/事件条件并设置上限，禁止依赖任意长 `sleep`。
- 测试运行前后要隔离/清理 WorkflowRun、Redis、消息队列与浏览器 state，不能依赖执行顺序。
- 截图和 DOM 断言要检查运行状态、步骤、操作按钮、错误/空状态和 Artifact，而不仅是页面非空。
- 375px 与 1440px 均不得有横向滚动、文本溢出、控件遮挡或点击目标重叠。
- 测试需要在失败时输出 run UUID、当前 URL、关键请求/事件与截图路径，方便 AI 定位。

## 验收标准

- [ ] 自动化 E2E 能提交测试 Workflow 并导航到正确 run UUID 详情页。
- [ ] 刷新和 SSE 断线重连后页面最终与后端 snapshot 一致，重复事件不重复显示步骤。
- [ ] 成功、失败、取消、可重试、无 Artifact、无权限路径均有断言。
- [ ] Artifact/Demo 链接在可用时可打开；不可用时不显示虚假成功入口。
- [ ] 桌面和 375px 移动视口截图/DOM 检查无重叠、溢出或不可用控件。
- [ ] `verify.ps1 -Profile e2e` 或等价命令可重复通过，不依赖人工页面观察。

## 验证命令

```powershell
cd frontend-vue
npm run test:unit
npm run test:e2e
npm run test:game-config
npm run build

cd ..
.\tools\verify.ps1 -Profile e2e
.\tools\verify.ps1 -Profile quick
```

## 审查清单

- 是否只断言页面有内容而未断言真实 WorkflowRun 状态。
- 是否用任意 sleep 掩盖异步竞态。
- 是否让测试访问真实模型、真实密钥或生产 MQ。
- 是否只测桌面而漏掉移动端文本/控件重叠。
- 是否通过 Mock 掉 Store/SSE/后端就宣称完成端到端测试。

## 完成定义

- R4 的主用户链路、实时状态同步与响应式体验都有可重复 Harness 保护。
- 页面回归能在进入主分支前被自动发现，而非依赖人工点击。
