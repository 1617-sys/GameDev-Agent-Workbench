# V4-07 Node Headless Runner

## 前置条件

V4-05 已通过人工 Review。

## 目标

在不启动浏览器、Phaser 或后端的情况下执行单个及批量 Episode，为 Player Agent 和优化器提供低成本环境工具。

## 允许修改

- `frontend-vue/src/features/demo/runtime/headless/**`
- `frontend-vue/src/features/demo/runtime/simulation/**`，仅修复已确认的协议缺口
- `frontend-vue/tests/headlessRunner.test.*`
- `frontend-vue/package.json` 的最小脚本调整

## 禁止修改

- Phaser Runtime 和 Vue 页面
- Java、Python、数据库与 Docker
- 复制 Simulation Core 的玩法规则
- 默认执行无最大步数、无超时或无限批量任务

## 工作内容

1. 实现 JSON 可序列化的单 Episode 调用入口。
2. 实现带数量、并发、最大步数和超时上限的批量入口。
3. 首版使用测试中的确定性策略驱动动作，不实现 LLM Player。
4. 返回 Episode RFC 定义的结果、轨迹或轨迹引用。
5. 单局失败不丢弃同批次已完成结果。

## 验收标准

- Node 环境可运行，不加载 DOM 或 Phaser；
- 相同请求重复执行结果一致；
- 100 局批量测试完成并输出成功、失败和终止原因统计；
- 非法配置、非法动作、超时和最大步数均有测试。

## 必须执行

```powershell
cd frontend-vue
npm run test:unit
npm run build
```

如新增专用脚本，同时执行该脚本对应的测试命令并写入交付结果。
