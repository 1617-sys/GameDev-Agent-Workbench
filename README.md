# GameDev Agent Workbench

一个面向实习求职展示的 **Agentic 小游戏生成工程实验台**。项目当前已经完成 V4 的自主试玩与实验编排基座；V5 将把自然语言创意收敛为受约束的 `GameSpec`，由 Java 控制生成、语义校验、构建编排和发布门禁，再由 Cocos Creator 3.8 LTS 构建可下载、可在本地独立运行的游戏包。

```text
Brief
→ Director Agent 制订计划并调用受控工具
→ GameSpec（版本化 DSL）
→ Java Compiler / Validator / Capability Gate
→ Cocos Runtime IR + Frozen Build Request
→ Local Cocos Web Mobile Package
→ Headless + Player Agent 验收
→ Agent 根据诊断修复
→ 人工批准可玩产物
```

这不是“让模型随意生成并执行代码”。LLM 负责目标理解、规划、工具选择和基于诊断的修复；Java 是 GenerationRun、规格、能力、工具、证据和产物门禁的权威层；Cocos Creator 负责运行、渲染、表现与本地构建；Python 承载 LangGraph Agent 和 Player 策略。

## 当前状态

- `v4.0.0`：已发布。具备确定性 Simulation Core、Replay、Headless Runner、Player Persona、逐步 LLM Player、Director + typed tools、DRAFT 审批和证据 UI。
- V4 的 Agentic 对照仅为 **MOCK 小样本基线**，证明闭环可运行、可审计，不证明真实模型优于固定工作流。
- V5：产品与契约设计阶段。Cocos Creator 3.8 LTS 是唯一活跃引擎；目标是先交付一个明显优于 V4 的完整小游戏切片。
- V5 只生成本地 Cocos Web Mobile 包；微信、抖音、支付宝等所有小游戏平台适配统一延后到 V6。

## 为什么保留 Java

Java 不是外围 CRUD 装饰，而将成为生成正确性的事实源：

- 解析并版本化 GameSpec；
- 执行语义校验、能力检查、安全白名单和确定性规范化；
- 编译为 Cocos Runtime IR、冻结构建请求和 Artifact Manifest；
- 持久化 Agent 运行、工具调用、诊断、审批与产物版本；
- 在发布前执行 smoke、可玩性和可追溯门禁。

模型出现幻觉时，系统不“相信提示词”：未知组件、非法事件、越界参数、缺失资源和不支持的能力都会变成稳定诊断码，Agent 只能在有限重试预算内修复 GameSpec，不能绕过 Java 门禁。

## V4 已实现能力

| 能力 | 实现边界 |
| --- | --- |
| 可靠编排 | Spring Boot、MySQL、Redis、RabbitMQ、Outbox、幂等、恢复与审计 |
| 可复现仿真 | `arcade_collect` Simulation Core、固定步长、seed、state hash、Replay、Headless Runner |
| Player Agent | 确定性策略、Persona、逐步读取环境反馈的 LLM 策略、轨迹持久化 |
| Director Agent | Spring AI 用户控制 Tool Calling、Java typed tool registry、持久化检查点/预算/权限、候选实验、DRAFT 人工审批；Python LangGraph 仅作显式回滚 |
| 可玩产物 | V4 为 GameConfig 2.0 驱动的 Phaser Runtime；V5 已有 GameSpec 编译器、Java 控制平面、Cocos Runtime Shell 与本地 Web Mobile 构建链路 |
| RAG | 已有知识生命周期与 provenance；真实语义检索质量尚未完成和证明 |

## V5 产品边界

首个垂直切片必须同时满足：

1. 用户输入一个受限游戏创意；
2. Agent 生成 GameSpec，而不是自由代码；
3. Java 能给出可定位的编译诊断并拒绝不支持的能力；
4. 通过编译后由隔离 Cocos Build Worker 生成可下载的本地 Web Mobile 包；
5. Headless 与 Player Agent 能实际运行并产生证据；
6. 至少展示一次“失败诊断 → Agent 修复 → 重新编译 → 可玩”的闭环；
7. 未经人工批准不得标记为发布候选。

首版不追求“一句话生成任意游戏”，也不同时维护多个引擎。V4 Phaser 冻结为历史；V5 只建设 Cocos Runtime Shell，再通过 recipe、Asset Pack、表现 Profile 和 archetype 扩展产量。

## RAG 的位置

RAG 不作为项目主卖点，也没有被删除。它只负责检索带来源的设计约束、组件说明、资产许可、历史失败和实验结论；检索内容不能绕过 GameSpec/Java 校验。是否启用 RAG 必须通过固定数据集、引用命中和 on/off 对照证明收益。

## 快速启动

```powershell
# Windows 10/11 + Docker Desktop
.\start-docker.ps1
# 浏览器打开 http://127.0.0.1:5173/
```

常用验证：

```powershell
cd backend-java
mvn test

cd ..\frontend-vue
npm run test:unit

cd ..\python-agent
python -m pytest -q
```

完整 Compose、集成与演示脚本仍以 V4 环境为准：

```powershell
.\tools\verify.ps1 -Profile quick
.\tools\verify.ps1 -Profile integration
.\tools\verify.ps1 -Profile e2e
```

## 技术栈

- Java 21、Spring Boot 3.5.16、Spring AI 1.1.8、Spring Security/JWT、MyBatis-Plus、Flyway、Micrometer
- MySQL 8.4、Redis 7.4、RabbitMQ 3.13、Outbox + Consumer
- Python 3.13、FastAPI、Pydantic、LangGraph
- Vue 3、Vite、Phaser 3（V4 legacy）、Playwright
- Cocos Creator 3.8 LTS（V5 Runtime Shell）
- Docker Compose、Maven、npm、PowerShell Harness

## 文档导航

- [V5 产品文档与实施入口](docs/requirements/v5/README.md)
- [V5 Agentic Mini-Game Factory PRD](docs/requirements/v5/game-generation-studio-prd.md)
- [GameSpec 语言契约](docs/requirements/v5/game-spec-language.md)
- [Java GameSpec 编译器设计](docs/requirements/v5/java-gamespec-compiler.md)
- [Cocos Runtime Target](docs/requirements/v5/cocos-runtime-target.md)
- [可玩产物契约](docs/requirements/v5/playable-artifact-contract.md)
- [V4 封版索引](docs/requirements/v4/README.md)
- [系统架构与当前事实](docs/architecture/system-architecture.md)
- [报告与证据索引](docs/reports/README.md)
- [完整文档导航](docs/README.md)

## 已知限制

- V5 当前只支持首个 `arcade_collect` 垂直切片；GameSpec、Spring AI 结构化 Spec Author、可恢复 Tool Calling Director、Java 控制平面和 Cocos Runtime Shell 已实现，但正式 Asset Pack、统一 Gate、Player 切流和生产级语义 RAG 尚未关闭。
- V4 Director 评测为 mock、每组 `N=6`，不能外推真实模型质量、成本或稳定性。
- Python 稳定性测试曾因 `policyDurationMs` 的墙钟抖动出现偶发失败，动作序列一致；应在后续把确定性决策与观测耗时分离。
- RAG 当前仍含 fake embedding / 内存检索基线，不能宣称已具备生产级语义检索。
- 项目是单机 Compose 工程样例，不是生产高可用、多租户 SaaS 或商业用户规模证明。

## AI 协作说明

AI 用于实现与生成 diff，人工负责需求边界、Review 和最终验收。简历和面试只陈述代码、测试和报告能证明的事实；规划中的 V5 能力必须明确标注为“设计中/待实现”。
