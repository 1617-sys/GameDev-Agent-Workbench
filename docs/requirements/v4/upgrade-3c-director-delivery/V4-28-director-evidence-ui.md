# V4-28 Director Evidence UI

## 前置条件

V4-27 已通过人工 Review。

## 目标

提供最小 Director Run 页面，展示计划、工具、候选、实验指标和人工审批，而不是聊天气泡动画。

## 允许修改

- `frontend-vue/src/features/director/**`
- `frontend-vue/src/shared/api/**` 的 Director API
- 路由、导航和相关测试

## 禁止修改

- 全局 UI 重构或新图表库
- 在前端重新计算正式指标
- 展示完整 Prompt、密钥、内部堆栈或未脱敏工具正文
- 从 UI 绕过审批 API 修改 PrototypeVersion 状态

## 页面能力

- 创建受限 DesignGoal 和预算；
- 展示状态时间线、剩余预算和当前等待原因；
- 展示每轮决策摘要、工具版本、输入/输出 digest、耗时和错误；
- 展示基线/候选参数及 Persona 指标；
- 在 WAITING_APPROVAL 时允许真实用户批准或拒绝；
- 链接到已有 PlayerRun 和 Episode Trace。

## 验收标准

- 页面刷新后完全从持久化状态恢复；
- 重复点击审批不会产生冲突结果；
- 长工具历史分页，失败/空/取消/恢复状态完整；
- 桌面和窄屏可完成核心流程。

## 必须执行

```powershell
cd frontend-vue
npm run test:unit
npm run build
npm run test:e2e:main
```
