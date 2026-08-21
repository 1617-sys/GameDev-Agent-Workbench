# V4 Agentic Game Design Lab 封版索引

> 状态：COMPLETE / FROZEN
> 发布版本：`v4.0.0`
> 历史主 PRD：[Agentic Game Design Lab PRD](../agentic-game-design-lab-prd.md)
> 后继版本：[V5 Agentic Mini-Game Factory](../v5/README.md)

V4-01～29 已完成并合入 `main`。本目录冻结为实现契约与人工 Review 历史，不再把 READY/BLOCKED 状态继续向后推进，也不在旧任务卡中回写 V5 架构。

## 批次状态

| 批次 | 任务 | 状态 | 批次产物 |
| --- | --- | --- | --- |
| Upgrade 0 | V4-01～03 | DONE | 基线、Simulation Protocol、Episode Protocol |
| Upgrade 1 | V4-04～08 | DONE | 可复现 Core、Replay、Phaser/Headless、Episode 持久化 |
| Upgrade 2A | V4-09～11 | DONE | Player/Persona 协议、交互环境、Python Client |
| Upgrade 2B | V4-12～15 | DONE | 确定性/Persona/LLM Player 与 Python API |
| Upgrade 2C | V4-16～18 | DONE | Java 编排、对照评测、Episode Trace UI |
| Upgrade 3A | V4-19～22 | DONE | Director 协议、持久化、LangGraph、Java typed tools |
| Upgrade 3B | V4-23～26 | DONE | 执行循环、DRAFT、候选与 Player 实验工具 |
| Upgrade 3C | V4-27～29 | DONE | Goal-to-DRAFT E2E、证据 UI、Agentic 对照报告 |

## 任务目录

- [`upgrade-0-foundation/`](upgrade-0-foundation)：V4-01～03
- [`upgrade-1-simulation/`](upgrade-1-simulation)：V4-04～08
- [`upgrade-2a-player-foundation/`](upgrade-2a-player-foundation)：V4-09～11
- [`upgrade-2b-player-policies/`](upgrade-2b-player-policies)：V4-12～15
- [`upgrade-2c-player-delivery/`](upgrade-2c-player-delivery)：V4-16～18
- [`upgrade-3a-director-foundation/`](upgrade-3a-director-foundation)：V4-19～22
- [`upgrade-3b-experiment-loop/`](upgrade-3b-experiment-loop)：V4-23～26
- [`upgrade-3c-director-delivery/`](upgrade-3c-director-delivery)：V4-27～29

## 核心结果

```text
Design Goal
→ Director 逐轮返回一个结构化决策
→ Java 校验权限、预算和 typed tool schema
→ 创建候选 DRAFT
→ 多 Persona Player 读取真实环境反馈并试玩
→ 持久化决策、工具调用、Episode、比较和恢复证据
→ 停在人工审批，不自动发布
```

V4 将项目从“固定四步提示词工作流”推进为具有动态工具选择、环境反馈、持久化状态、预算和人工审批的 Agentic 闭环；它仍只围绕 `arcade_collect`，没有解决通用游戏生成问题。

## 证据与限定

- [Director E2E 报告](../../reports/V4-director-e2e-report.md)
- [Director Agentic 对照报告](../../reports/V4-director-agentic-evaluation-report.md)
- [Player 评测工具](../../../tools/player-evaluation/README.md)

Agentic 对照为 mock、每组 `N=6` 的小样本基线。它证明评测协议和证据链可运行，不证明真实模型 Agent 已稳定优于固定工作流。模型质量、token、成本、非法调用和恢复能力仍需真实 Provider 与扩充数据集验证。

## V5 继承与改变

V5 继承 Director、Player、Simulation、版本、审批和审计底座，但停止扩展 Phaser。新链路由 Java控制 GameSpec、Agent 工具、生成状态和发布门禁，由 Cocos Creator 构建本地可玩 Web Mobile 包。GameConfig 2.0 保留为 V4 契约和确定性评测投影；小程序/小游戏平台适配延后到 V6。
