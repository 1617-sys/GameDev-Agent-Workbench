# V4 Agentic Game Design Lab 任务索引

> 状态：ACTIVE  
> 主 PRD：[Agentic Game Design Lab PRD](../agentic-game-design-lab-prd.md)  
> 当前批次：Upgrade 3A — Director Foundation

## 目录结构

```text
v4/
├── README.md
├── upgrade-0-foundation/       # V4-01～03：基线、Simulation/Episode 协议
├── upgrade-1-simulation/       # V4-04～08：Core、Replay、Adapter、Runner、持久化
├── upgrade-2a-player-foundation/ # V4-09～11：Player 协议、交互环境、Python Client
├── upgrade-2b-player-policies/ # V4-12～15：确定性、Persona、LLM Player、Python API
├── upgrade-2c-player-delivery/ # V4-16～18：Java 编排、对照评测、轨迹 UI
├── upgrade-3a-director-foundation/ # V4-19～22：Director 协议、状态、LangGraph、工具注册
├── upgrade-3b-experiment-loop/ # V4-23～26：执行循环、DRAFT、候选、试玩比较
└── upgrade-3c-director-delivery/ # V4-27～29：E2E、证据 UI、Agentic 评测
```

每个目录代表一个人工 Review 批次。前一批次没有通过，不开始后一批次。

## 执行规则

每次只把一张任务卡交给 Codex。Codex 负责实现、相关测试、执行验证和 diff 摘要；人工负责 Review 与解锁下一任务。

统一交付格式：

```text
完成状态：DONE / PARTIAL / BLOCKED
修改文件：
行为变化：
测试命令与结果：
未解决风险：
人工 Review 重点：
```

禁止在任务结束时进行全仓库审查、重写 PRD、更新简历材料或顺手修复无关问题。

## 批次状态

| 批次 | 任务 | 状态 | 批次产物 |
|---|---|---|---|
| Upgrade 0 | V4-01～03 | DONE | 基线、Simulation Protocol、Episode Protocol |
| Upgrade 1 | V4-04～08 | DONE | 可复现 Core、Phaser/Headless、Episode 持久化 |
| Upgrade 2A | V4-09～11 | DONE | Player 协议、交互式环境服务、Python Client |
| Upgrade 2B | V4-12～15 | DONE | 确定性/Persona/LLM Player 与 Python API |
| Upgrade 2C | V4-16～18 | DONE | Java 编排、对照报告、轨迹 UI |
| Upgrade 3A | V4-19～22 | READY | Director 协议、持久化状态、LangGraph 决策器、工具注册表 |
| Upgrade 3B | V4-23～26 | BLOCKED | Director 执行循环、DRAFT 生命周期、候选与实验工具 |
| Upgrade 3C | V4-27～29 | BLOCKED | 恢复验证、证据 UI、Agentic 对照报告 |

## Upgrade 0：Foundation

- [V4-01 当前基线冻结](upgrade-0-foundation/V4-01-baseline-freeze.md) — DONE
- [V4-02 Simulation Protocol RFC](upgrade-0-foundation/V4-02-simulation-protocol-rfc.md) — DONE
- [V4-03 Episode Protocol RFC](upgrade-0-foundation/V4-03-episode-protocol-rfc.md) — DONE
- [冻结的 Simulation Protocol](upgrade-0-foundation/V4-simulation-protocol.md)
- [冻结的 Episode Protocol](upgrade-0-foundation/V4-episode-protocol.md)

## Upgrade 1：Simulation

- [V4-04 Simulation Core](upgrade-1-simulation/V4-04-simulation-core.md) — DONE
- [V4-05 Determinism 与 Replay](upgrade-1-simulation/V4-05-determinism-replay.md) — DONE
- [V4-06 Phaser Adapter](upgrade-1-simulation/V4-06-phaser-adapter.md) — DONE
- [V4-07 Headless Runner](upgrade-1-simulation/V4-07-headless-runner.md) — DONE
- [V4-08 Episode Persistence](upgrade-1-simulation/V4-08-episode-persistence.md) — DONE

## Upgrade 2A：Player Foundation

- [V4-09 Player 与 Persona RFC](upgrade-2a-player-foundation/V4-09-player-persona-rfc.md) — DONE
- [V4-10 Interactive Simulation Service](upgrade-2a-player-foundation/V4-10-interactive-simulation-service.md) — DONE
- [V4-11 Python Environment Client](upgrade-2a-player-foundation/V4-11-python-environment-client.md) — DONE

批次门禁：Python 能通过类型化异步客户端完成 `create → observe → step → close`，环境会话有认证、TTL、并发和预算限制。

## Upgrade 2B：Player Policies

- [V4-12 Deterministic Player Baseline](upgrade-2b-player-policies/V4-12-deterministic-player.md) — DONE
- [V4-13 Persona Policies](upgrade-2b-player-policies/V4-13-persona-policies.md) — DONE
- [V4-14 LLM Player Loop](upgrade-2b-player-policies/V4-14-llm-player.md) — DONE
- [V4-15 Python Player API](upgrade-2b-player-policies/V4-15-python-player-api.md) — DONE

批次门禁：确定性与 LLM Player 使用同一协议；三类 Persona 形成可复现差异；LLM 每步读取真实反馈，不预生成动作序列。

## Upgrade 2C：Player Delivery

- [V4-16 Java Player Run Orchestration](upgrade-2c-player-delivery/V4-16-java-player-orchestration.md) — DONE
- [V4-17 Player Comparison Harness](upgrade-2c-player-delivery/V4-17-player-comparison-harness.md) — DONE
- [V4-18 Episode Trace UI](upgrade-2c-player-delivery/V4-18-episode-trace-ui.md) — DONE

V4-17 和 V4-18 可以在 V4-16 合并后并行。V4-18 是本阶段唯一允许的 UI 任务。

批次门禁：Java → Python → Simulation Service → Java persistence 闭环稳定；指标可追溯；人工可以查看完整 Episode 决策证据。

## 人工 Review 门禁

每张任务卡合并前只检查：

1. diff 是否越界；
2. 是否出现重复玩法规则；
3. 是否破坏 GameConfig 2.0 或现有 API；
4. 测试是否覆盖任务验收标准；
5. 失败、超时、随机种子和终止原因是否明确；
6. 是否存在可以删除的无效抽象。

## Upgrade 3A：Director Foundation

- [V4-19 Director 与 Experiment RFC](upgrade-3a-director-foundation/V4-19-director-experiment-rfc.md) — READY
- [V4-20 Director Run Persistence](upgrade-3a-director-foundation/V4-20-director-run-persistence.md) — BLOCKED by V4-19 Review
- [V4-21 Python LangGraph Director](upgrade-3a-director-foundation/V4-21-python-langgraph-director.md) — BLOCKED by V4-19 Review
- [V4-22 Java Typed Tool Registry](upgrade-3a-director-foundation/V4-22-java-tool-registry.md) — BLOCKED by V4-19 Review

V4-20、V4-21、V4-22 在 V4-19 Review 后可以分别在 Java、Python 和 Java 隔离工作区推进；V4-20 与 V4-22 都修改 Java 时不得并行落在同一工作区。

批次门禁：Director 状态、决策协议、预算和工具权限被冻结；Python 每轮只返回一个结构化决策；Java 能校验并审计类型化工具调用。

## Upgrade 3B：Experiment Loop

- [V4-23 Director Execution Loop](upgrade-3b-experiment-loop/V4-23-director-execution-loop.md) — BLOCKED by Upgrade 3A
- [V4-24 Prototype DRAFT 与人工审批](upgrade-3b-experiment-loop/V4-24-prototype-draft-approval.md) — BLOCKED by V4-23 Review
- [V4-25 Deterministic Candidate Generator](upgrade-3b-experiment-loop/V4-25-deterministic-candidate-generator.md) — BLOCKED by V4-24 Review
- [V4-26 Player Experiment Tools](upgrade-3b-experiment-loop/V4-26-player-experiment-tools.md) — BLOCKED by V4-25 Review

批次门禁：Director 能在预算内创建候选 DRAFT、运行多 Persona 试玩、比较结果并停在人工审批，不允许自动发布。

## Upgrade 3C：Director Delivery

- [V4-27 Goal-to-DRAFT E2E 与恢复](upgrade-3c-director-delivery/V4-27-goal-to-draft-e2e.md) — BLOCKED by Upgrade 3B
- [V4-28 Director Evidence UI](upgrade-3c-director-delivery/V4-28-director-evidence-ui.md) — BLOCKED by V4-27 Review
- [V4-29 Director Agentic Evaluation](upgrade-3c-director-delivery/V4-29-director-agentic-evaluation.md) — BLOCKED by V4-27 Review

V4-28 与 V4-29 可以在 V4-27 合并后并行。

批次门禁：目标到 DRAFT 闭环可复现、失败可恢复、工具调用可审计；固定 Workflow 与 Director + Tools 的差异有真实对照数据。

Upgrade 3C 完成后暂停编码，根据候选搜索基线结果拆分 Upgrade 4 的参数优化与 Critic 任务。
