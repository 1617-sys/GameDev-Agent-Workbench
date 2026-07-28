# V4-04 抽取 Simulation Core

## 前置条件

V4-02 已通过人工 Review。

## 目标

从 Phaser 场景抽取纯 TypeScript Simulation Core，使玩法状态转移不依赖 Phaser、Vue、DOM、网络或系统时间。

## 允许修改

- `frontend-vue/src/features/demo/runtime/simulation/**`
- `frontend-vue/tests/simulationCore.test.*`
- 为运行测试而进行的最小 `frontend-vue/package.json` 调整

## 只读参考

- `frontend-vue/src/features/demo/runtime/topDownCollectRuntime.js`
- `frontend-vue/src/features/demo/runtime/gameConfig.js`
- `docs/requirements/v4/upgrade-0-foundation/V4-simulation-protocol.md`

## 禁止修改

- `topDownCollectRuntime.js` 的现有行为或接线
- Vue 页面和样式
- Java、Python、数据库和 Docker
- GameConfig 2.0 合约
- 引入状态管理或游戏引擎框架

## 工作内容

1. 实现 Simulation 初始化、step 和只读状态快照。
2. 实现移动、边界、障碍、巡逻、接触伤害、无敌窗口、收集、出口与终止判断。
3. 所有时间来自显式 tick/delta 输入。
4. 对外对象不可修改内部状态。
5. 用固定配置覆盖正常、边界、伤害、收集、胜负和超时测试。

## 验收标准

- Simulation 模块搜索不到 `Phaser`、`window`、`document`、Vue 或 HTTP 依赖；
- 单步输入只产生一个确定的 `StepResult`；
- 测试覆盖 RFC 定义的 step 顺序和所有终止原因；
- 本任务不接入现有 Phaser 页面。

## 必须执行

```powershell
cd frontend-vue
npm run test:unit
npm run build
```
