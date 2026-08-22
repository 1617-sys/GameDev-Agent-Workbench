# GameDev Agent Workbench 文档导航

> 当前发布基线：`v4.0.0` Agentic Game Design Lab。
> 当前产品方向：V5 Agentic Mini-Game Factory，Java控制生成闭环，Cocos Creator 构建本地可玩小游戏包。
> 所有小程序/小游戏平台适配延后到 V6；RAG 是带引用的辅助能力，不是产品主线。

## 首先阅读

1. [根 README](../README.md)：项目用途、现状、启动方式和真实性边界。
2. [V5 黄金链路收敛实施计划](v5-golden-path-convergence-plan.md)：当前唯一执行范围、代码改动与验收标准。
3. [V5 文档入口](requirements/v5/README.md)：目标架构、范围、决策与后续任务批次。
4. [V5 主 PRD](requirements/v5/game-generation-studio-prd.md)：Cocos 小游戏工厂、用户价值、垂直切片和验收标准。
5. [GameSpec 语言契约](requirements/v5/game-spec-language.md)：AI 可以生成什么，不能生成什么。
6. [Java GameSpec 编译器](requirements/v5/java-gamespec-compiler.md)：Java 如何约束幻觉并产出目标包。
7. [Cocos Runtime Target](requirements/v5/cocos-runtime-target.md)：引擎边界、构建 Worker、表现 Gate 与 Phaser 退役。
8. [可玩产物契约](requirements/v5/playable-artifact-contract.md)：什么才算“真的生成了小游戏”。

## 当前事实与历史版本

- [V4 封版索引](requirements/v4/README.md)：已完成的 Simulation、Player、Director 和证据链。
- [V4 Agentic Game Design Lab PRD](requirements/agentic-game-design-lab-prd.md)：V4 历史决策，不再是当前 PRD。
- [GameConfig 2.0](game-config-schema.md)：V4 `arcade_collect` Runtime 契约；未来是 GameSpec 的一个编译目标。
- [系统架构](architecture/system-architecture.md)：当前代码事实与 V5 目标边界。
- [报告索引](reports/README.md)：测试、失败、环境限制与可复现证据。
- [B 路线 / RAG 路线图](roadmap-balance-lab-rag.md)：已被 V4/V5 取代的历史方案。

历史需求卡和报告保留当时范围与结论，不回写成新架构。若历史文档与当前入口冲突，以源码、发布 tag、报告限定条件和本页标注为准。

## 求职展示材料

- [项目讲解](project-narrative.md)
- [简历描述](resume-project-description.md)
- [面试问答](interview-qa.md)
- [Demo 脚本](demo-script.md)

在 V5 代码落地前，上述材料只能把 GameSpec 编译器写成“下一阶段设计”，不能冒充已实现成果。V4 可陈述为：完成可审计 Agentic 闭环和 mock 对照基线；不能陈述为“已证明 Agent 优于固定工作流”。

## 运维与协作

- [Docker 一键启动](docker-one-click-start.md)
- [Operations Runbook](operations-runbook.md)
- [AI 协作规范](AI_COLLABORATION.md)
- [工程陷阱记录](PITFALLS.md)
- [Vibe Coding 教程](VIBE_CODING_TUTORIAL.md)

AI 可以实现任务卡、运行测试并提交 diff；人工负责 Review、产品取舍和发布判断。任何能力结论必须由代码、可重复测试和报告共同支持。
