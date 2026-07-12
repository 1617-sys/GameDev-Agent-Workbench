# R5-08: 评测详情与 PromptVersion 指标展示 Harness

> 状态：`TODO`
>
> 前置任务：`R4-验收`、`R5-06`、`R5-07`
>
> 推荐模型：`gpt-5.4`（页面与图表） / `gpt-5.5`（数据语义审查）
>
> 任务类型：前端展示 / E2E 与可访问性验证

## 背景

R4 已有运行详情页和 API/Store 基础。R5 需要把评测报告、mock 标记和 PromptVersion 对比变成用户能理解的工程证据，而不是只藏在数据库或日志中。

## 目标

在运行中心增加两类可读视图：

```text
WorkflowRun / Artifact detail
-> schema / rule / runtime evaluation status
-> violation summary and safe evidence link
-> model/provider/promptVersion/mock/latency/token/cost summary

PromptVersion analytics view
-> filter window / project / agentType / includeMock
-> sample count + success/evaluation pass + latency/cost comparison
-> explicit zero/insufficient/missing-data states
```

## 范围

允许：

- 扩展 R4 API client、WorkflowRun Store 或新增 analytics/evaluation Store 与专用 views/components。
- 调用 R5-06 EvaluationReport 与 R5-07 analytics API，展示分层状态、violation code/摘要、Artifact eligibility、PromptVersion 元数据和 mock badge。
- 实现两个 PromptVersion 的并排对比、统一筛选条件、空/错误/无权限/样本不足状态。
- 使用图表库或轻量 SVG/CSS 展示；新增依赖必须有明确理由并提供静态/无数据回退。
- 添加单元、组件、浏览器 E2E 与桌面/375px 截图测试，覆盖 mock、失败报告、零样本、长 violation 文本和权限失败。

## 非目标

- 不在浏览器计算成本/成功率/P95，也不修改后端聚合口径。
- 不展示完整 Prompt、原始模型输出、Secret、Authorization、内部 stack trace 或其他用户数据。
- 不实现 Prompt 编辑/激活工作台、复杂 A/B 自动选优或支付计费。
- 不实现 R6 RAG 引用与知识库页面。
- 不重写 R4 路由、SSE 连接或 Phaser Runtime。

## 约束

- mock 必须视觉上明确标识，并默认不与真实模型比较结论混合。
- 所有数值展示带单位/样本量/过滤条件；缺失 usage、零样本、未评测必须明确呈现，不用 0 或绿色成功伪装。
- 运行详情优先展示 Artifact 是否可用、失败层级和可行动错误摘要；完整敏感证据只通过受控权限路径访问。
- 图表/表格在 375px 宽度应可阅读或转为纵向摘要，不得横向溢出/覆盖按钮。
- 组件只消费 API/Store 数据，不自己判断评测通过、计算 aggregate 或改写 Workflow 状态。
- 路由/组件卸载时清理请求状态，不影响 R4 SSE subscription。

## 验收标准

- [ ] WorkflowRun/Artifact 详情可区分 Schema、Rule、Runtime 的 PASSED/FAILED/SKIPPED/ERROR，并展示安全的 violation 摘要。
- [ ] 真实模型与 mock fallback 在运行详情和统计对比中明确区分。
- [ ] 两个 PromptVersion 可在相同过滤条件下对比成功率、评测通过率、延迟、token、成本和样本数。
- [ ] 零样本、缺失 usage、未评测、无权限、网络错误和超长 violation 都有稳定 UI。
- [ ] 桌面和 375px 移动视口无溢出、重叠或不可点击控件。
- [ ] 前端单元/E2E、Vue build 与 R4 运行中心测试通过。

## 验证命令

```powershell
cd frontend-vue
npm run test:unit
npm run test:e2e
npm run test:runtime-smoke
npm run test:game-config
npm run build

cd ..
.\tools\verify.ps1 -Profile e2e
.\tools\verify.ps1 -Profile quick
```

## 审查清单

- 是否将 mock 结果伪装成真实模型质量。
- 是否由页面自行聚合/计算指标，造成与后端口径漂移。
- 是否将完整 Prompt、原始输出或内部异常直接展示。
- 是否把零样本/缺失 usage 用 0 或绿色状态误导用户。
- 是否在移动端让表格/图表溢出或遮挡操作。

## 完成定义

- R5 的质量、成本、版本和 mock 证据可在运行中心中被安全、清晰地阅读和比较。
- 前端展示不改变后端评测/指标的唯一事实来源。
