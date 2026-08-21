# ADR-001：Java 是 Agentic 小游戏生成的控制平面与事实权威

> 状态：PROPOSED
> 日期：2026-07-29
> 适用版本：V5+

## 背景

V4 使用 TypeScript Simulation Core 和 Phaser 执行 `arcade_collect`，这是已发布的历史事实。V5 决定停止扩展 Phaser，采用 Cocos Creator 3.8 LTS 构建视觉质量更高、可导出到多个小游戏平台的 Runtime。

项目仍是 Java 实习求职项目。Java不能退化成调用 Python/Cocos 的薄 CRUD 层，也没有必要重写渲染引擎。

## 决策

Java拥有 GenerationRun 状态、GameSpec 语义、Capability Registry、Agent Tool Gateway、Cocos 构建编排、Artifact 血缘和发布门禁；Python/LangGraph 负责计划和修复建议；Cocos负责运行、渲染、表现和本地 Web Mobile 构建。

```text
Python Director
      │ typed decision/tool request
      v
Java Control Plane
├── GenerationRun state machine
├── GameSpec semantic compiler
├── Capability / permission / budget
├── evidence and artifact lineage
└── isolated Cocos Build Worker orchestration
      │ validated IR + frozen build request
      v
Cocos Creator Runtime Shell
└── Local Cocos Web Mobile Package
```

Agent不得直接编辑 Cocos scene、prefab、meta、Runtime 源码、shader 或构建模板，也不得直接执行 Cocos 进程。Java不重复实现 Cocos 的渲染、动画和逐帧引擎。

## 结果

正面结果：

- Java承担领域状态、类型系统、语义诊断、可靠编排和治理等核心问题；
- 模型不确定性被限制在不可执行 GameSpec；
- Cocos 的编辑器、表现系统和 Web Mobile 构建用于提升游戏完成度；
- V5 保持平台无关核心，微信、抖音、支付宝等 target adapter 延后到 V6；
- V4 Phaser 代码和证据可以冻结保留，不污染 V5。

代价：

- Java capability registry、simulation projection 与 Cocos Runtime 需要 conformance tests；
- Cocos CLI 依赖安装好的 GUI 环境，需要受控 Build Worker；
- Runtime Shell、Asset Pack 和表现预设必须由人工维护，不能完全自动生成；
- 首个切片需要重新建设 Cocos Runtime，不能把 V4 页面直接迁移后冒充升级。

## 被否决方案

- 继续扩展 Phaser：迁移成本最低，但用户已经否决其呈现效果和产品方向。
- LLM 直接生成 Cocos/TypeScript 源码：难以复现、安全审计和稳定修复。
- Java 重写完整游戏引擎：重复造轮子，偏离 Agentic 小游戏生成目标。
- Unity：表现能力强，但引入 C# 和更重的构建链，稀释 Java/Agent 主线。
- 删除 Java、全 TypeScript：产品实现更直接，但失去本项目最重要的 Java 展示价值。
