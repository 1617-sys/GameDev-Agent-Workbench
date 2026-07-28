# V4 Player 与 Persona Protocol

> 状态：RFC FROZEN
>
> 协议版本：`player/1.0`
>
> 依赖：`simulation/1.0`、`episode/1.0`

## 1. 边界与所有权

Java 创建 Player Run、冻结 Prototype/Policy/Persona/模型/预算并最终持久化 Episode；Python 逐步执行 PlayerPolicy；Node Simulation Service 是 Simulation Core 的唯一网络宿主。调用方向固定为 Java → Python → Node，结果沿调用栈返回 Java。Python 不持久化权威 Episode，Node 不调用 Python/Java，也不复制玩法规则。

确定性 Player 与 LLM Player 必须实现同一个接口：

```text
PlayerPolicy.decide(observation: Observation) -> Action
```

一次 `decide` 只能返回当前一步的闭合 `simulation/1.0` Action。不得预生成或提交完整动作序列冒充 Agent；每次决定前必须重新 `observe`，每次决定后必须立即 `step`。

## 2. Environment 会话

环境接口按顺序提供：

1. `reset(request)` 创建会话和 Core Episode，返回初始 Observation；同一 Player Run 重试必须使用新的 session ID，重放则复用冻结的 config、simulation seed 和参数。
2. `observe()` 返回当前 Observation，不推进 tick、不消费动作预算。
3. `step(action)` 原子提交一个 Action，返回 Observation、StepResult 及最终 Episode 组装所需的状态哈希、分数、终止原因和事件。
4. `close(reason)` 幂等释放服务端状态。成功、失败、取消和调用方超时均必须在 `finally` 中调用；close 不得改变已产生的轨迹。

会话只存在于 Node 受限内存中。默认 TTL 300 秒，最大 100 个并发会话，单会话上限取请求 `maxSteps` 且不得超过 1,000,000。TTL 从最后一次成功的 create/observe/step 刷新；过期、显式关闭或进程退出都会释放状态。终态会话仍允许 observe 和 close，但拒绝后续 step。相同 session 的并发操作只有一个可进入，冲突返回 `SESSION_BUSY`，调用方不得并行决定。

## 3. 决策循环与证据

每一步固定执行：observe → 计算 Observation digest → decide → 校验 Action → step → 记录结果。EpisodeStep 必须记录 sequence、Observation digest、原始/应用 Action、策略耗时、模型调用 ID（如有）、错误、前后状态哈希、事件和 reward。

决策超时不提交 Action，记录 `DECISION_TIMEOUT`；结构不合法或动作域外先允许策略在剩余决策预算内重试，仍失败记录 `INVALID_POLICY_OUTPUT`。模型限流/网络/提供方失败可按冻结的退避策略有限重试，且不得重放已被服务端接受的 step。预算耗尽记录 `MODEL_BUDGET_EXHAUSTED`。所有失败最终都必须 close，并由 Java 保存部分轨迹。日志和领域错误不得包含 prompt、token 或完整 HTTP 正文。

## 4. Persona 参数

Persona 是可版本化、可摘要的数值行为参数，不是人格文案：

| Persona | visionRadiusPx | decisionIntervalSteps | actionErrorPermille | planningDepth | maxPolicyRetries |
|---|---:|---:|---:|---:|---:|
| `NOVICE` | 160 | 3 | 180 | 1 | 0 |
| `REGULAR` | 320 | 2 | 60 | 3 | 1 |
| `EXPERT` | 640 | 1 | 10 | 8 | 2 |

`decisionIntervalSteps` 大于 1 时，中间 tick 只能按冻结策略重复最近合法移动或 WAIT；仍须逐步 observe/step。`actionErrorPermille` 的随机选择和任何平局打破只使用独立 `policySeed`，不得消费 Simulation Core seed。相同 Persona 版本、policy seed、Observation 序列和策略版本必须产生相同 Action。固定地图与 seed 矩阵应验证可见实体数、决策频率、错误率、路径效率和胜率差异。

## 5. 版本、认证与重试

所有请求和响应携带 `protocolVersion: "player/1.0"` 或其明确依赖版本、episode ID 与 correlation ID。服务间使用独立内部 token；未知 major 必须拒绝。create、step 默认不自动重试；observe 可有限重试；close 可按相同 session ID 重试。只有带幂等键且服务端确认未执行的操作才可重试。

规范示例：[`examples/player/create-session.json`](examples/player/create-session.json)、[`examples/player/step-exchange.json`](examples/player/step-exchange.json)、[`examples/player/episode-step-evidence.json`](examples/player/episode-step-evidence.json)。
