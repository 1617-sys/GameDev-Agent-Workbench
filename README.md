# GameSpec Agent Workbench

一个以 **Java Agent 可信控制面** 为核心的可验证小游戏生成实验台。游戏是可交互的验证场景；项目重点不是开发游戏引擎，而是约束模型输出、控制工具权限、保存运行证据，并把不确定的 LLM 决策收敛为可编译、可构建的确定产物。

```text
自然语言 Brief
  → Spring AI 生成结构化 GameSpec
  → Java Compiler 执行语义与能力校验
  → 稳定诊断码反馈给模型（有限重试）
  → canonical GameSpec + Runtime IR + Build Request
  → 数据库租约抢占后由固定 Cocos Runtime Shell 构建 Web Mobile 包
  → 内部试玩包 → 人工审批 → 显式发布 → 正式可下载产物
```

模型不能生成或执行任意游戏代码。它只能提出候选规格和受控工具决策；Java 负责校验、授权、预算、状态迁移、构建输入和产物门禁。

## 核心看点

### 1. 从概率输出到确定性规格

当前只支持 `arcade_collect` 垂直切片。`GameSpecCompiler` 对模型输出执行封闭字段、范围、跨字段规则和 Runtime capability 校验，成功后生成规范化 JSON、SHA-256 摘要、Runtime IR 与冻结 Build Request。

Prompt 和结构化输出只是软约束，不能替代 Java 编译器复验。未知组件、非法事件、越界参数和不支持的能力都会变成稳定诊断，供 Agent 在有限预算内修复。

### 2. 受控且可恢复的 Agent 执行

Director 每轮只能选择一个类型化工具或控制决策。Java 对工具版本、闭合 JSON Schema、项目资源权限、幂等键、超时、轮次和预算进行校验；模型不能直接执行工具或伪造审批结果。

运行过程持久化 Decision、ToolCall、Event 和 Checkpoint，并通过 `stateVersion + claim token` 控制并发执行权。人工审批是显式暂停状态，不占用线程等待。

### 3. 可验证的本地游戏产物

构建 Worker 只允许使用仓库内固定的 Cocos Runtime Shell，并把 Java 编译产生的 Runtime IR 写入隔离工作区。产物组装会检查路径、符号链接、文件大小和常见密钥模式，并生成包含来源摘要的 manifest。

## 当前实现边界

| 能力 | 当前事实 |
| --- | --- |
| GameSpec Author | Spring AI 结构化生成，编译诊断驱动有限修复 |
| Java Compiler | `arcade_collect` 语义校验、能力门禁、规范化摘要、Runtime IR、Build Request |
| Director | 类型化工具、预算、检查点、数据库 claim、等待实验与人工审批状态 |
| Cocos 构建 | 固定 Runtime Shell、本地 Web Mobile 构建、日志摘要和可验证 ZIP |
| V5 发布门禁 | 构建租约、人工审批独立留痕、预览/正式下载隔离、显式 RELEASED 状态 |
| 自动试玩 | V4 已有确定性 Player/LLM Player 和 episode 证据；尚未与 V5 GameSpec/Cocos 主链统一 |
| 可靠工作流 | MySQL Outbox、RabbitMQ at-least-once、幂等消费、Redis 快速防重、恢复审计 |

项目目前是单机工程实验台，不是生产高可用 SaaS，也不宣称可以生成任意游戏。V5 的主要缺口是把 GenerationRun、Cocos 试玩证据、Director 修复和统一 Gate 收敛为一条可演示主链。

## Java 在系统中的职责

- 解析、规范化并版本化 GameSpec；
- 执行语义规则、能力白名单和安全边界；
- 控制 Agent 工具、权限、预算、幂等和有限重试；
- 冻结 Runtime IR、Build Request、运行快照和产物 lineage；
- 持久化运行、步骤、决策、工具调用、诊断与审批证据；
- 在发布前执行编译、构建、试玩和人工审批门禁。

游戏运行与表现由 Cocos Creator 承担，Python 仅承载 Player 策略和部分历史 Agent 实验。Java 是生成正确性与运行状态的事实源。

## 仓库结构

```text
backend-java/         Spring Boot、Spring AI、GameSpec Compiler、Agent 控制面
cocos-runtime-shell/  固定 Cocos Creator 3.8 LTS Runtime Shell
python-agent/         Player 策略、LangGraph 实验和评测接口
frontend-vue/         项目工作台、运行证据和历史 Phaser Runtime
docker/               MySQL、Redis、RabbitMQ 与可观测性配置
tools/                验证、E2E、故障注入和评测脚本
docs/                 架构、需求、报告、面试边界和升级方案
```

## 快速启动

环境要求：Java 21、Docker Desktop；如需真实 Cocos 构建，需额外安装 Cocos Creator 3.8 LTS。

```powershell
# Windows 10/11
.\start-docker.ps1
# http://127.0.0.1:5173/
```

分模块验证：

```powershell
cd backend-java
mvn test

cd ..\python-agent
python -m pytest -q

cd ..\frontend-vue
npm run test:unit
```

完整验证入口：

```powershell
.\tools\verify.ps1 -Profile quick
.\tools\verify.ps1 -Profile integration
.\tools\verify.ps1 -Profile e2e
```

## 技术栈

- Java 21、Spring Boot 3.5、Spring AI、Spring Security、MyBatis-Plus、Flyway
- MySQL 8、Redis 7、RabbitMQ、Outbox、Micrometer、Prometheus
- Python、FastAPI、Pydantic、LangGraph
- Vue 3、Vite、Phaser 3（V4 legacy）、Playwright
- Cocos Creator 3.8 LTS、Docker Compose、Maven

## 文档导航

- [黄金切片升级方案](docs/upgrade-plan-v5-gold-slice.md)
- [V5 黄金链路收敛实施计划](docs/v5-golden-path-convergence-plan.md)
- [GameSpec 语言契约](docs/requirements/v5/game-spec-language.md)
- [Java GameSpec 编译器设计](docs/requirements/v5/java-gamespec-compiler.md)
- [Cocos Runtime Target](docs/requirements/v5/cocos-runtime-target.md)
- [系统架构](docs/architecture/system-architecture.md)
- [项目事实与实习面试分析](docs/INTERNSHIP_PROJECT_DEEP_ANALYSIS.md)
- [面试问答](docs/interview-qa.md)

## AI 协作与声明边界

项目使用 AI 辅助需求分析和代码实现，人工负责范围选择、架构决策、Review 与验证。README、简历和面试只陈述仓库代码、测试及报告能够证明的事实；规划能力会明确标注，mock 评测不包装成真实模型效果。
