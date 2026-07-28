# V4-06 Phaser Adapter

## 前置条件

V4-05 已通过人工 Review。

## 目标

让现有 Phaser Runtime 使用 Simulation Core 作为唯一玩法状态源，同时保持用户可见行为和遥测协议兼容。

## 允许修改

- `frontend-vue/src/features/demo/runtime/topDownCollectRuntime.js`
- `frontend-vue/src/features/demo/runtime/simulation/**`
- `frontend-vue/tests/runtime.browser.e2e.js`
- 与 Runtime 直接相关的新测试

## 禁止修改

- Vue 页面布局与样式
- GameConfig 2.0 合约
- 后端遥测 API
- 在 Phaser Adapter 中保留另一套碰撞、计分或胜负判断
- Java、Python和数据库

## 工作内容

1. 将键盘/触摸输入转换为协议 Action。
2. 每个固定 tick 调用 Simulation Core。
3. 将状态快照投影到 Phaser sprite、HUD、音频和回调。
4. 从 `StepResult` 产生现有遥测事件，保持一次且仅一次。
5. 清理场景中已经迁入 Core 的重复玩法规则。

## 验收标准

- 原有 GamePreview 正常启动、移动、受伤、收集、胜负和重开；
- 浏览器结果可由相同 config、seed、动作序列在 Core 中重放；
- 遥测事件名称和必要字段不变且无重复；
- Adapter 只包含输入、渲染、音频和遥测映射。

## 必须执行

```powershell
cd frontend-vue
npm run test:unit
npm run build
npm run test:runtime-smoke
```
