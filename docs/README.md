# GameDev Agent Workbench 文档导航

> 当前产品定位：面向单一 `arcade_collect` 玩法的 **LLM 可玩原型与平衡实验平台**。
>
> 当前发布基线：V3 单机 Docker Compose 闭环。
> 下一阶段：深化试玩—建议—候选版本—A/B 对比，并把 RAG 检索桩升级为可评测的持久化语义检索。

本页是当前文档入口。`requirements/` 和 `reports/` 中的 R0–R7/V3 文档记录历史决策与当时证据，不应被自动解释为当前所有能力均已达到生产级。

## 首先阅读

1. [根 README](../README.md)：项目用途、启动方式、当前能力和限制。
2. [B 路线与 RAG 升级路线图](roadmap-balance-lab-rag.md)：下一阶段产品边界、工作包和 Review 决策。
3. [系统架构](architecture/system-architecture.md)：当前代码组件、事实链和明确的技术缺口。
4. [V3 发布验收](reports/V3-release-acceptance.md)：已通过的单玩法闭环及限定条件。

## 当前对外材料

- [3–5 分钟项目讲解](project-narrative.md)
- [简历项目描述](resume-project-description.md)
- [面试问答](interview-qa.md)
- [AI 工程岗位技术栈对照](interview-ai-engineering-stack-analysis.md)
- [Demo 脚本](demo-script.md)

这些文档必须遵循同一口径：

- 项目是受约束的 LLM Workflow，不是自主多 Agent 系统；
- 当前只支持 `arcade_collect`，是参数化可玩原型，不是通用游戏代码生成；
- 当前 RAG 已有生命周期、协议和 provenance，但 embedding/vector search 是测试桩；
- 只有报告中实际执行并通过的 gate 才能写成已验证能力；
- mock、环境 skip 和真实 Provider 数据不得混算。

## 设计与运行

- [V3 轻量游戏原型设计](v3-lightweight-game-prototype-design.md)
- [GameConfig Schema](game-config-schema.md)
- [前端重建设计](frontend-rebuild-design.md)
- [Docker 一键启动](docker-one-click-start.md)
- [Operations Runbook](operations-runbook.md)
- [Redis 集成计划](redis-integration-plan.md)

## 历史需求与报告

- [`requirements/`](requirements)：R0–R7 和 V3 的需求卡、RFC、验收定义。
- [`reports/`](reports)：对应阶段的运行结果、失败证据和发布结论。
- [报告索引](reports/README.md)：验证命令和证据导航。

历史文档保留当时的范围、分支、数字和结论。若历史文档与当前入口冲突，以当前源码、根 README、V3 验收和本页标记的现状为准；不要回写历史报告制造“当时已经完成”的假象。

## RAG 文档阅读顺序

1. [R6 RAG 报告](reports/R6-rag-knowledge-report.md)：当时已经验证的生命周期、隔离和 provenance，以及明确阻断项。
2. [R6 设计契约](requirements/r6/R6-rag-knowledge-design.md)：历史目标设计，不等于当前全部实现。
3. [B 路线与 RAG 升级路线图](roadmap-balance-lab-rag.md)：从 fake/in-memory 基线升级到真实检索与质量评测的当前计划。

在真实 embedding、持久向量检索和固定评测集完成前，仓库只应宣称“RAG 协议与检索证据链已实现，语义检索质量尚未实现和证明”。

## 协作规范

- [AI 协作规范](AI_COLLABORATION.md)
- [工程陷阱记录](PITFALLS.md)
- [Vibe Coding 教程](VIBE_CODING_TUTORIAL.md)

AI 可以辅助探索、实现、测试和文档整理，但能力结论必须由代码、可重复测试和报告共同支持。
