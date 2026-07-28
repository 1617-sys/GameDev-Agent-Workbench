# 验证报告索引

本页是交付材料的事实导航，不把研发准入、环境跳过或局部 PASS 提升为发布 PASS。每份报告的数字只对其记录的 branch、候选 SHA、fixture 和环境有效。

## 当前结论

**Release candidate：BLOCKED。** 主要共同阻断是 R3 Redis rate-limit/Lua 提交门在健康 Redis 上返回业务码 `50302`，导致 durable WorkflowRun 未创建；R5、R6 与 R7 安全/环境也有独立缺口。当前没有最终 release 报告或 tag 建议。

## R0–R6 阶段报告

| 阶段 | 报告结论 | 关键 commit | 报告 | 主要复现命令 |
| --- | --- | --- | --- | --- |
| R0 Baseline | PASS | `dd552f8` | [R0 baseline](R0-baseline-report.md) | `.\tools\verify.ps1 -Profile quick` |
| R1 Domain | PASS（研发准入） | `cef3942` | [R1 workflow domain](R1-workflow-domain-report.md) | `cd backend-java; mvn test` |
| R2 Runner | PASS | `5c52f82` | [R2 workflow runner](R2-workflow-runner-report.md) | `.\tools\verify.ps1 -Profile quick` |
| R3 Async | Harness 已实现；R7 集成 BLOCKED | `2b6b80c` | [R3 concurrency harness](R3-08-async-integration-concurrency-harness.md) | `.\tools\verify.ps1 -Profile integration` |
| R4 Run Center | PASS，附 Docker 条件 | `9ec09d8` | [R4 run center](R4-run-center-report.md) | `.\tools\verify.ps1 -Profile e2e` |
| R5 Evaluation | BLOCKED | `2ae723e` | [R5 prompt/evaluation/metrics](R5-prompt-evaluation-metrics-report.md) | `.\tools\verify.ps1 -Profile quick` |
| R6 RAG | BLOCKED | `9225aa0` | [R6 knowledge/RAG](R6-rag-knowledge-report.md) | `.\tools\verify.ps1 -Profile quick` |

## R7 发布加固报告

| Gate | 结论 | 关键 commit | 报告/证据 | 复现命令 |
| --- | --- | --- | --- | --- |
| Fresh bootstrap | 人可读报告 MISSING；不能判 PASS | `653f309` | 仅有历史 evidence 目录，缺少冻结设计要求的 `R7-fresh-environment-bootstrap-report.md` | `.\tools\verify-bootstrap.ps1` |
| Main E2E | BLOCKED | `c9165d2` | [R7 main workflow E2E](R7-main-workflow-e2e-report.md) | `.\tools\verify.ps1 -Profile e2e` |
| Concurrency/performance | BLOCKED；measurement NOT RUN | `aa48cf8` | [R7 concurrency/performance](R7-concurrency-performance-baseline-report.md) | `.\tools\run-performance-baseline.ps1` |
| Fault/recovery | BLOCKED | `30421db` | [R7 fault/recovery](R7-fault-injection-recovery-report.md) | `.\tools\run-fault-injection.ps1` |
| Observability/operations | BLOCKED；诊断子项通过 | `692f709` | [R7 observability](R7-observability-operations-report.md)、[Runbook](../operations-runbook.md) | 按报告中的 Compose drill；`.\tools\verify.ps1 -Profile integration` |
| Security release audit | BLOCKED | `7ae1072` | [R7 security](R7-security-release-audit.md) | 按报告 Validation 逐项执行；仓库没有一键安全脚本 |
| Reproducible demo | BLOCKED；reset 通过 | `6641976` | [R7 demo](R7-demo-reproducibility-report.md)、[Demo script](../demo-script.md) | `.\tools\prepare-demo.ps1`; `.\tools\verify-demo.ps1`; `.\tools\reset-demo.ps1` |
| Delivery materials | BLOCKED；材料检查通过，前置未全 PASS | 当前交付 commit | [R7 delivery materials](R7-project-delivery-materials-report.md) | `git diff --check`; `.\tools\verify.ps1 -Profile quick` |
| Final acceptance | NOT RUN | 无 | `R7-final-release-report.md` MISSING | 仅在所有前置报告同一候选重跑后执行 |

## 可引用的验证数字

只有附带限定条件时才可引用：

- [R6 报告](R6-rag-knowledge-report.md)记录：131 项 Java 通过、1 项 Docker 条件 skip；3 项 Python、20 项前端 unit、6 项 browser E2E、2 项 runtime smoke 通过。
- [R7 可观测报告](R7-observability-operations-report.md)记录：136 项 Java（1 skip）、6 项 Python、10/10 observability 目标测试通过；成功/失败/恢复 WorkflowRun 证据仍缺失。
- [R7 安全审计](R7-security-release-audit.md)记录：142 项 Java（1 skip）、8 项 Python、前端依赖审计 0；Docker/image 与完整 Maven/Python CVE gate 未完成。
- [R7 Demo 报告](R7-demo-reproducibility-report.md)记录：prepare 44.2 秒到达提交边界、reset 6.7 秒；异步提交未成功。

禁止从[性能报告](R7-concurrency-performance-baseline-report.md)发布 P50/P95/P99、吞吐、错误率或容量数字：warm-up 与 300 秒 measurement 均为 NOT RUN。

## 阅读和复现规则

1. 先看报告的候选 SHA、环境资格、Provider/fixture 和 dirty state。
2. 再看命令退出码、required test 是否实际执行、是否有 environment skip。
3. 最后读取结论与 follow-up；局部 PASS 不覆盖报告 BLOCKED。
4. evidence 目录是脱敏证据，不是通用日志仓库；不复制完整请求、文档正文或外部 Provider 响应。
5. 修复源码、fixture 或脚本后创建新候选和新 evidence 目录，不迁移旧 PASS。

## 相关入口

- [根 README](../../README.md)
- [系统架构](../architecture/system-architecture.md)
- [Docker 启动](../docker-one-click-start.md)
- [Operations Runbook](../operations-runbook.md)
- [面试问答](../interview-qa.md)
- [简历描述](../resume-project-description.md)
# V3 发布验收

- [V3 轻量原型发布验收](V3-release-acceptance.md)：`arcade_collect` 的生成、试玩、不可变版本、指标、建议和确定性离线导出主链路。
