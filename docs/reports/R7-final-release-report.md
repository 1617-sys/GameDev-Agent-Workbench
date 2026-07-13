# R7 Final Release Report

## Release Candidate

- 日期：2026-07-14，Asia/Shanghai
- branch/commit：`r7` / `2aa8d73108a83552d80a93c1a550ee7e60904c53`
- 初始状态：工作区干净，`HEAD` 与 `origin/r7` 一致；最终仅包含验收报告、状态、最小历史弱值脱敏和本轮脱敏证据。
- 环境：Windows 11 `10.0.26200`，Docker `29.1.5`，Compose `5.0.1`，Java `21.0.8`，Python `3.13.3`，Node `24.14.1`，PowerShell `5.1`；16 逻辑 CPU、约 16 GB 主机内存，Docker 约 7.35 GiB。
- 环境资格：Fresh 与 Reference 均不合格。现有本地 `.env` 被标准启动安全门拒绝；PowerShell 低于 7，Docker 未固定为 6 CPU/8 GiB，Reference 条件未确认。
- Provider：仅 fake/mock；未调用外部 Provider。
- 总证据：[`20260713T163258Z-2aa8d73`](evidence/r7/20260713T163258Z-2aa8d73/)。

## Fresh Start 与 E2E

| 门禁 | 结果 | 命令/证据 |
| --- | --- | --- |
| 候选身份 | PASS | 初始 `git status --short` 为空；`git diff --check` 退出 0 |
| Compose config | BLOCKED | 原生 `docker compose config` 因本地安全配置缺项退出 1；进程级临时强值下退出 0，但不能替代标准启动 |
| Fresh bootstrap | BLOCKED | `start-docker.ps1` 正确拒绝弱/缺失本地配置；没有合格的 `R7-fresh-environment-bootstrap-report.md` |
| quick | PASS | `verify.ps1 -Profile quick` 退出 0；Java 142 项、0 failure/error、1 skip；Python compile、Vue build、Compose config 通过 |
| integration | BLOCKED | 命令退出 0，但 Testcontainers 与 Docker 29.1.5 协商失败，3/3 测试全部 skip |
| Main E2E | BLOCKED | 首轮暴露缺失浏览器缓存并保留[环境失败证据](evidence/r7/20260713T162504Z-2aa8d73/)；安装锁定版本 Chromium 后重跑，提交返回 `50302`，无 WorkflowRun，见[契约失败证据](evidence/r7/20260713T162731Z-2aa8d73/) |

E2E fixture 已按用户命名空间清理。首次环境失败没有覆盖；第二次运行进入真实 Java/Redis 提交边界后失败，不能用浏览器安装成功替代业务 gate。

## 并发与性能

- 场景/配置：fake Agent 固定 300 ms；目标 20 unique-key、10 same-key、20 query/SSE、60 秒 warm-up + 300 秒 measurement。
- 结果：**BLOCKED at preflight**。HTTP 200 / 业务码 `50302`，未创建 WorkflowRun；warm-up 与 measurement 均未运行。
- P50/P95/P99/吞吐：**NOT AVAILABLE**，不发布 preflight 资源采样为性能基线。
- 重复业务事实：未进入提交后阶段，无法验证 same-key、重复消息或 Artifact 唯一性。
- 证据：[clean-candidate performance run](evidence/r7/20260713T162850Z-2aa8d73/)。脚本对未跟踪 E2E 证据的首次 clean-candidate 拒绝也保存在[`20260713T162829Z-2aa8d73`](evidence/r7/20260713T162829Z-2aa8d73/)。
- 环境限制：PowerShell、Docker 内存限额和人工 Reference 条件均不合格；即使 preflight 修复，也必须在合格 Reference 环境重跑。

## 故障与恢复

| 故障 | 预期 | 实际 | 恢复 | 证据 |
| --- | --- | --- | --- | --- |
| Redis unavailable | fail-closed、0 durable run | PASS；客户端超时，持久化 Run 数 0 | Redis 已恢复 | [fault matrix](evidence/r7/20260713T162956Z-2aa8d73/fault/fault-matrix.json) |
| 锁过期/错误 owner | 错误 owner 不释放，TTL 后消失 | PASS | 自动 TTL/合法恢复 | 同上 |
| RabbitMQ/Outbox | broker 恢复后一次有效执行 | FAIL/BLOCKED；提交先返回 `50302` | broker 已恢复 | 同上 |
| Python 429/非法输出 | 有限重试并失败/DLQ | FAIL/BLOCKED；fixture 未获异步执行 | fake mode 已恢复 | 同上 |
| Consumer restart | 无重复成功事实 | FAIL/BLOCKED；fixture 未获接收 | Consumer/stack 已停止 | 同上 |
| MySQL transient | 无半终态/重复成功 | NOT RUN；没有满足五分钟边界的受控代理 | 无注入 | 同上 |

故障脚本退出 1。所有隔离 Compose stack 已停止，fixture 仅按本轮 namespace 清理，volume 未删除。

## 可观测性与安全

- Trace/log/metrics：现有[R7 可观测报告](R7-observability-operations-report.md)为 BLOCKED，且不属于本 `RC_SHA`；本轮没有成功 Run、受控失败 Run、恢复 Run 的同候选跨服务 trace 集合。
- Security audit：既有[R7 安全审计](R7-security-release-audit.md)为 BLOCKED，且不属于本 `RC_SHA`。本轮精确扫描在最小历史档案脱敏后剩余 9 行，均为冻结验收文档/基线报告中的扫描表达式；应用源码、运行配置、示例和新证据为 0。高置信凭据签名为 0。
- Evidence privacy：新证据中的 8 个本机路径/用户名文本工件已在写入 Git 前机械脱敏并重算 checksum；复查为 0。详情见[`security-review.txt`](evidence/r7/20260713T163258Z-2aa8d73/security-review.txt)。
- Remaining risks：本地 ignored `.env` 仍需由操作者安全备份后重新生成/轮换；Compose 双用户、完整依赖/镜像扫描、Python 生产 mock 关闭证据仍未在本候选闭环。

## 演示与投递材料

- 3–5 分钟 Demo：直接 verify 因未 prepare 退出 1；prepare 使用 OFFLINE/MOCK 到异步提交边界后返回 `50302`；再次 verify 确认没有完整 Workflow；reset 退出 0，仅清理 `r7-demo-v1` namespace 并保留 volume。结论 BLOCKED。
- README/架构图：[README](../../README.md)与[架构](../architecture/system-architecture.md)存在，链接/命令在 R7-08 检查中通过。
- Interview/resume package：[面试问答](../interview-qa.md)、[简历描述](../resume-project-description.md)、[项目讲解](../project-narrative.md)存在且明确 BLOCKED 边界。
- Delivery report：[R7-08 报告](R7-project-delivery-materials-report.md)本身为 BLOCKED；其前置报告没有在同一候选全部 PASS。

## R0-R7 完成矩阵

| 阶段/Gate | 报告 | 最终审查结论 |
| --- | --- | --- |
| R0 baseline | [R0](R0-baseline-report.md) | PASS（历史阶段报告） |
| R1 domain | [R1](R1-workflow-domain-report.md) | PASS（研发准入） |
| R2 runner | [R2](R2-workflow-runner-report.md) | PASS（历史阶段报告） |
| R3 async reliability | [R3 harness](R3-08-async-integration-concurrency-harness.md) | BLOCKED；当前提交门 `50302`，integration 3/3 skip |
| R4 run center | [R4](R4-run-center-report.md) | 历史 PASS 附 Docker 条件；当前 E2E BLOCKED |
| R5 evaluation | [R5](R5-prompt-evaluation-metrics-report.md) | BLOCKED |
| R6 RAG | [R6](R6-rag-knowledge-report.md) | BLOCKED |
| R7-01 Fresh | 报告 MISSING | BLOCKED |
| R7-02 E2E | [报告](R7-main-workflow-e2e-report.md) + 本轮证据 | BLOCKED |
| R7-03 performance | [报告](R7-concurrency-performance-baseline-report.md) + 本轮证据 | BLOCKED；measurement NOT RUN |
| R7-04 fault | [报告](R7-fault-injection-recovery-report.md) + 本轮证据 | BLOCKED |
| R7-05 observability | [报告](R7-observability-operations-report.md) | BLOCKED |
| R7-06 security | [报告](R7-security-release-audit.md) | BLOCKED |
| R7-07 demo | [报告](R7-demo-reproducibility-report.md) | BLOCKED；reset PASS |
| R7-08 materials | [报告](R7-project-delivery-materials-report.md) | BLOCKED；材料子检查 PASS |

## 最终结论

- **`BLOCKED`**。发布候选未满足 Fresh、Main E2E、integration、performance、fault、observability、security、demo 和同候选报告链要求。quick 与部分 Redis/材料检查通过，不能覆盖这些阻断项。
- Release tag 建议：**拒绝创建 RC/final tag**。本轮未创建 tag、未 push、未创建 GitHub Release、未部署。
- 主要回归归属：R3 Redis rate-limit/Lua 提交集成。必须先修复健康 Redis 下的 `50302`，补当前 Docker/Testcontainers 兼容验证，再在新的干净候选上从 Fresh 开始重跑。
- 后续维护清单：安全重新生成本地 `.env`；补 Fresh 人工报告；在合格 Reference 环境完成 60+300 秒性能门；完成 RabbitMQ/Python/Consumer/MySQL 故障矩阵；闭环 R5/R6 和 R7 security/observability；最后重跑 Demo、R7-08 与本验收。
