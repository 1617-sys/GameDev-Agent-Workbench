# V5 黄金链路收敛实施计划

> 状态：P0 代码已实现；V38 已在 Compose MySQL 8.4 上完成真实 Flyway 迁移验证
> 最后更新：2026-08-22
> 本轮目标：停止扩展功能，把 V5 收敛为一条可演示、可恢复、可审批发布的最小闭环。

## 1. 唯一交付链路

```text
自然语言 Brief
  -> Spring AI 生成受约束 GameSpec
  -> Java Compiler 校验并给出稳定诊断
  -> 有限轮次修复
  -> READY_TO_BUILD
  -> BUILDING（数据库抢占 + 过期租约）
  -> AWAITING_APPROVAL（仅可下载内部预览包）
  -> APPROVED / REJECTED（人工决策独立留痕）
  -> RELEASED（仅此状态可下载正式发布包）
```

本轮不增加新 Agent、玩法类型、RAG、消息中间件或部署平台。V4 Phaser、旧 Workflow、旧 Prototype 审批链路保留为 Legacy，不纳入 V5 验收，也不为本轮目标进行重构。

## 2. 必须成立的系统不变量

1. 同一 GenerationRun 同一时刻最多只有一个有效构建执行者。
2. Cocos 外部进程不运行在数据库事务中。
3. 构建结果只有持有当前 `buildClaimToken` 的执行者可以提交。
4. 构建执行者崩溃后，租约过期即可由新执行者基于最新 `stateVersion` 接管。
5. 构建成功后只能进入 `AWAITING_APPROVAL`，不能直接发布。
6. 一次 GenerationRun 只允许存在一个最终人工审批事实；重复请求必须幂等。
7. `REJECTED` 不允许发布；`APPROVED` 必须经过显式 release 才能进入 `RELEASED`。
8. 正式产物接口只允许 `RELEASED`；审批前只能通过独立 preview 接口获取内部试玩包。
9. Java 与 Python 对局结果统一使用 `WON / LOST / TRUNCATED / ERROR`，完成率以 `WON` 计算。

## 3. 代码改动

### P0-A：构建状态与短事务抢占

- 数据库增加 `build_claim_token`、`build_claim_expires_at`、`build_attempt`。
- `GenerationRunStatus` 增加 `READY_TO_BUILD`、`RELEASED`。
- 创建并编译成功的任务进入 `READY_TO_BUILD`。
- `build()` 拆成三个边界：CAS 抢占、事务外构建、CAS 提交。
- 构建不可用时释放 claim 回到 `READY_TO_BUILD`；确定性构建失败进入 `FAILED`。
- 允许携带最新版本号接管已过期的 `BUILDING`。

### P0-B：V5 人工审批与发布门禁

- 新建 `generation_run_approval`，保存操作者、决定、原因、幂等键、请求指纹和时间。
- 增加审批接口：仅 `AWAITING_APPROVAL` 可决定为 `APPROVED` 或 `REJECTED`。
- 增加 release 接口：仅 `APPROVED` 可进入 `RELEASED`。
- 原 artifact 下载改为只允许 `RELEASED`；新增 preview artifact 用于审批前内部试玩。

### P0-C：跨语言结果契约修复

- `PlayerExperimentService` 和 Director 读模型中的胜利判断从 `SUCCESS` 改为 `WON`。
- 测试夹具不再构造协议中不存在的 `SUCCESS` outcome。

### P0-D：验证

- 单元测试覆盖：创建状态、构建抢占成功、并发抢占失败、过期租约接管、成功进入待审批、审批幂等、审批冲突、未发布禁止正式下载、审批后显式发布。
- Flyway 迁移测试覆盖新增表、列和状态约束。
- 运行全部 Java 测试；前端契约受影响时运行前端单测。

## 4. 接口约定

```text
POST /api/v5/projects/{projectUuid}/generation-runs/{runUuid}/build?expectedVersion={n}
GET  /api/v5/projects/{projectUuid}/generation-runs/{runUuid}/preview-artifact
POST /api/v5/projects/{projectUuid}/generation-runs/{runUuid}/approval
POST /api/v5/projects/{projectUuid}/generation-runs/{runUuid}/release?expectedVersion={n}
GET  /api/v5/projects/{projectUuid}/generation-runs/{runUuid}/artifact
```

审批请求必须携带 `Idempotency-Key`，请求体只接受：

```json
{
  "decision": "APPROVED",
  "reason": "人工试玩通过"
}
```

## 5. 验收场景

1. 合法 GameSpec 创建任务后状态为 `READY_TO_BUILD`。
2. 两个调用方使用同一版本并发构建，只有一个能获得 claim，Cocos 只执行一次。
3. claim 过期后，新调用方可以使用最新版本接管；旧调用方不能提交结果。
4. 构建成功后状态为 `AWAITING_APPROVAL`，preview 可下载，正式 artifact 被拒绝。
5. `REJECTED` 后不能 release；`APPROVED` 后可显式 release 为 `RELEASED`。
6. 同一审批幂等键与相同请求安全重放；不同请求返回冲突。
7. `RELEASED` 后正式 ZIP 可下载且摘要校验通过。
8. Player 实验中 `WON` 样本被正确计入 completion rate。

## 6. 停止条件

满足以下条件后结束本轮，不继续增加功能：

- 黄金链路可通过固定 GameSpec 稳定演示；
- 并发构建不会重复启动外部进程；
- 审批和正式发布之间不存在旁路；
- 全量 Java 测试通过，关键前端契约测试通过；
- README 明确 V5 主链与 V4 Legacy 边界；
- 简历只陈述上述测试能够证明的能力。

## 7. 后续但不阻塞本轮

- 将 Director ToolCall 的幂等键、状态和结果全部落库，补齐“副作用完成后进程崩溃”的恢复窗口。
- 使用 Testcontainers 编写真实 MySQL 的并发黄金路径测试。
- 将自动试玩结果接入 V5 `AWAITING_APPROVAL` 前的机器 Gate。

这些工作有价值，但不应阻塞当前可演示主链交付。

## 8. 本轮实施记录

- [x] V38 数据迁移：构建租约、构建次数、审批证据表和 RELEASED 状态。
- [x] 构建改为 CAS claim、事务外 Cocos、claim token 条件提交和过期接管。
- [x] 产物改为 digest-addressed 存储，避免过期 Worker 覆盖获胜构建产物。
- [x] V5 审批、preview、release 和正式 artifact 门禁接口。
- [x] Generation Studio 接入待构建、审批、发布和两类下载入口。
- [x] 修复 MachineEpisode `WON` 与实验统计 `SUCCESS` 不一致的问题。
- [x] Java 单元/契约测试与前端单测、生产构建验证。
- [x] Compose MySQL 8.4 从 V37 迁移到 V38；Flyway 记录成功，新增字段、表、唯一键、外键和检查约束已查询确认。
- [ ] 补充 Testcontainers 并发黄金路径；完整 Compose 镜像重建受本机 Docker Hub 网络超时阻断。
