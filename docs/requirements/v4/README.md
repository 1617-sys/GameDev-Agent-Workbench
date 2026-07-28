# V4 Agentic Game Design Lab 任务索引

> 状态：ACTIVE  
> 主 PRD：[Agentic Game Design Lab PRD](../agentic-game-design-lab-prd.md)  
> 当前批次：Upgrade 2A — Player Foundation

## 目录结构

```text
v4/
├── README.md
├── upgrade-0-foundation/       # V4-01～03：基线、Simulation/Episode 协议
├── upgrade-1-simulation/       # V4-04～08：Core、Replay、Adapter、Runner、持久化
├── upgrade-2a-player-foundation/ # V4-09～11：Player 协议、交互环境、Python Client
├── upgrade-2b-player-policies/ # V4-12～15：确定性、Persona、LLM Player、Python API
└── upgrade-2c-player-delivery/ # V4-16～18：Java 编排、对照评测、轨迹 UI
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
| Upgrade 2A | V4-09～11 | READY | Player 协议、交互式环境服务、Python Client |
| Upgrade 2B | V4-12～15 | BLOCKED | 确定性/Persona/LLM Player 与 Python API |
| Upgrade 2C | V4-16～18 | BLOCKED | Java 编排、对照报告、轨迹 UI |

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

- [V4-09 Player 与 Persona RFC](upgrade-2a-player-foundation/V4-09-player-persona-rfc.md) — READY
- [V4-10 Interactive Simulation Service](upgrade-2a-player-foundation/V4-10-interactive-simulation-service.md) — BLOCKED by V4-09 Review
- [V4-11 Python Environment Client](upgrade-2a-player-foundation/V4-11-python-environment-client.md) — BLOCKED by V4-10 Review

批次门禁：Python 能通过类型化异步客户端完成 `create → observe → step → close`，环境会话有认证、TTL、并发和预算限制。

## Upgrade 2B：Player Policies

- [V4-12 Deterministic Player Baseline](upgrade-2b-player-policies/V4-12-deterministic-player.md) — BLOCKED by Upgrade 2A
- [V4-13 Persona Policies](upgrade-2b-player-policies/V4-13-persona-policies.md) — BLOCKED by V4-12 Review
- [V4-14 LLM Player Loop](upgrade-2b-player-policies/V4-14-llm-player.md) — BLOCKED by V4-13 Review
- [V4-15 Python Player API](upgrade-2b-player-policies/V4-15-python-player-api.md) — BLOCKED by V4-14 Review

批次门禁：确定性与 LLM Player 使用同一协议；三类 Persona 形成可复现差异；LLM 每步读取真实反馈，不预生成动作序列。

## Upgrade 2C：Player Delivery

- [V4-16 Java Player Run Orchestration](upgrade-2c-player-delivery/V4-16-java-player-orchestration.md) — BLOCKED by Upgrade 2B
- [V4-17 Player Comparison Harness](upgrade-2c-player-delivery/V4-17-player-comparison-harness.md) — BLOCKED by V4-16 Review
- [V4-18 Episode Trace UI](upgrade-2c-player-delivery/V4-18-episode-trace-ui.md) — BLOCKED by V4-16 Review

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

Upgrade 2C 完成后暂停编码，根据 Player 对照结果拆分 Upgrade 3 的 Director 与实验编排任务。
