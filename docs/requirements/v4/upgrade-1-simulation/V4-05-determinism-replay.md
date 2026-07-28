# V4-05 Determinism 与 Replay

## 前置条件

V4-04 已通过人工 Review。

## 目标

让 Simulation Core 在相同协议版本、GameConfig、seed 和动作序列下得到相同终态与状态哈希。

## 允许修改

- `frontend-vue/src/features/demo/runtime/simulation/**`
- `frontend-vue/tests/simulation*.test.*`
- 必要的测试 fixture

## 禁止修改

- Phaser Adapter、Vue 页面、Java 和 Python
- 使用 `Math.random()`、系统时间或对象遍历偶然顺序作为仿真输入
- 为通过测试对状态哈希排除实际玩法字段

## 工作内容

1. 实现显式 seeded PRNG；没有随机行为时仍保存 seed 和算法版本。
2. 实现 canonical state snapshot 与稳定 hash。
3. 实现动作序列 replay。
4. 检测协议、config hash 或动作不兼容并返回明确错误。
5. 添加相同输入一致、不同 seed 差异、篡改动作失败测试。

## 验收标准

- 同一输入连续运行至少 100 次得到相同终态哈希；
- replay 能逐 step 比较状态哈希并指出首次偏差；
- 终止后继续 step 的行为有明确协议和测试；
- 不依赖浏览器环境。

## 必须执行

```powershell
cd frontend-vue
npm run test:unit
npm run build
```
