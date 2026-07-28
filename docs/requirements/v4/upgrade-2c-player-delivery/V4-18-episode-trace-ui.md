# V4-18 Episode Trace UI

## 前置条件

V4-16 已通过人工 Review。

## 目标

提供最小 Episode 证据页面，让人工能查看 Player Run、Persona 差异和逐步决策轨迹。

## 允许修改

- `frontend-vue/src/features/episodes/**`
- `frontend-vue/src/shared/api/**` 中 episode API
- 路由和导航的最小调整
- `frontend-vue/tests/**` 对应单测/E2E

## 禁止修改

- 全局视觉重构
- 新图表库
- 在浏览器重新计算正式指标
- 展示完整 Prompt、密钥或未经脱敏的模型响应

## 工作内容

- Player Run/Batch 列表和状态；
- Episode 摘要、Persona/Policy/Model 版本与成本；
- Observation digest、Action、反馈、状态哈希和错误的逐步轨迹；
- 基础 Persona 指标对比；
- 加载、空、部分失败和权限错误状态。

## 验收标准

- 从 PrototypeVersion 能进入关联 Player Runs；
- 轨迹来自持久化证据，不在前端伪造；
- 1000 Step 使用分页或虚拟化，不一次渲染全部内容；
- 桌面与窄屏核心信息可读。

## 必须执行

```powershell
cd frontend-vue
npm run test:unit
npm run build
npm run test:e2e:main
```
