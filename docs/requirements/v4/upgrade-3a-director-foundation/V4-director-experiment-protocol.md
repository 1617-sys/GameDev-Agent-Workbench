# V4 Director / Experiment Protocol

> 状态：RFC FROZEN
> 协议版本：`director/1.0`
> 所有权：Java 保存事实并执行工具；Python 只计算下一项决策。

## 1. 边界与不变量

Director 只能针对同一项目中已存在的 `PrototypeVersion`、`PlayerRun` 和
`MachineEpisode` 工作。自然语言目标必须先归一化为 `DesignGoal`，未通过指标、参数及预算
allowlist 校验的 Run 不得进入 `RUNNING`。Python 不持有数据库凭据，不调用用户态 Java API，
不保存权威 checkpoint，也不能批准或发布版本。

每一轮 Python 必须且只能返回一个有序决策：`CALL_TOOL`、`REQUEST_APPROVAL`、`FINISH` 或
`FAIL`。`CALL_TOOL` 只含一个调用；并行或未排序调用不属于本协议。Java 校验决策摘要、状态版本、
工具版本、JSON Schema、项目归属、权限、幂等键和预算后才执行，并将结果摘要作为下一快照的事实。

## 2. DesignGoal

```json
{
  "protocolVersion": "director/1.0",
  "sourceTextDigest": "sha256",
  "metrics": [{"name":"NOVICE_COMPLETION_RATE","target":{"min":0.55,"max":0.70}}],
  "guardrails": [{"name":"EXPERT_MEAN_COMPLETION_TIME_DELTA","operator":"LTE","value":0.08}],
  "allowedParameters": [{"path":"difficulty.enemySpeed","min":80,"max":160}],
  "budget": {
    "maxRounds":12,"maxToolCalls":10,"maxCandidates":8,"maxEpisodes":300,
    "maxTokens":20000,"maxCostMicros":3000000,"maxWallClockMs":900000,
    "maxFailures":3,"decisionTimeoutMs":30000
  }
}
```

指标名、运算符和参数路径均来自 Java allowlist。区间必须有限且 `min <= max`；保护约束不可由
Director 删除或放宽。预算所有维度均为硬上限，任一用尽后只能 `FINISH`（证据足够）或 `FAIL`。

## 3. 状态机

状态为 `PENDING`、`RUNNING`、`WAITING_EXPERIMENT`、`WAITING_APPROVAL`、`SUCCEEDED`、
`FAILED`、`CANCELED`。

- `PENDING -> RUNNING | CANCELED`
- `RUNNING -> WAITING_EXPERIMENT | WAITING_APPROVAL | SUCCEEDED | FAILED | CANCELED`
- `WAITING_EXPERIMENT -> RUNNING | FAILED | CANCELED`
- `WAITING_APPROVAL -> RUNNING | SUCCEEDED | FAILED | CANCELED`

`SUCCEEDED`、`FAILED`、`CANCELED` 是不可恢复终态。暂停保留当前不可变 checkpoint；恢复使用
乐观锁令 `stateVersion + 1`。取消可从所有非终态进入 `CANCELED`。批准只记录人工主体、时间和
目标引用；拒绝回到 `RUNNING` 供 Director 处理，或由人工明确终止为 `FAILED/CANCELED`。

## 4. 快照、决策与工具

`DirectorStateSnapshot` 是一次决策的完整输入：`runId/projectId/stateVersion/status/goal`、预算
使用量、按轮次排序的历史决策、最近工具结果、候选及其 `PrototypeVersion/PlayerRun/
MachineEpisode` 引用、等待中的审批、允许工具定义。相同快照、模型配置和历史结果必须能重放。

`DirectorDecision` 包含 `round`、四选一 `kind`、不含思维链的 `reasonSummary`、
`decisionDigest`、`modelEvidence`（provider/model/promptVersion/inputDigest/outputDigest/tokenUsage）
以及对应 payload。摘要为规范 JSON（排除 `decisionDigest`）的 SHA-256。

`ToolCallRequest` 包含唯一 `callId`、稳定 `toolName/toolVersion`、`idempotencyKey`、严格 JSON
参数及 `dryRun`。`ToolCallResult` 包含状态、输入/输出 digest、受限摘要、`resultRef`、耗时、
错误分类和重试次数；大结果正文只保存在 Java 管理的对象存储。Director 只能引用 resultRef，
不得构造任意 URL。

每个工具声明 name/version、JSON Schema（`additionalProperties:false`）、`READ/WRITE` 权限、
risk level、timeout、幂等语义和最大内联结果。Java 工具注册表是唯一 allowlist。首批只读工具为
`GET_PROTOTYPE_VERSION@1`、`GET_MACHINE_EPISODE_METRICS@1`、`GET_PLAYER_RUN_STATUS@1`、
`COMPARE_PROTOTYPE_CONFIGS@1`。

## 5. checkpoint 与审计

Java 在同一事务中追加决策/工具调用并更新 Run 的 `checkpointJson` 和 `stateVersion`。checkpoint
至少含完整 `DesignGoal`、预算已用量、最后完成轮次、最近结果引用、候选/实验引用和待审批引用；
不保存密钥、完整 Prompt 或大结果正文。创建 Run 使用 `(userId, projectId, idempotencyKey)` 幂等，
同 key 不同 request fingerprint 拒绝。更新必须匹配旧 `stateVersion`，跨项目引用或非法转换回滚。

成功必须有满足目标与所有保护约束的证据；失败必须有稳定错误分类；任何终态不得再执行工具或恢复。
Director 只能请求审批，不能生成人工批准事实，也不能把 DRAFT 发布为正式版本。
