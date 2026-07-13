# R7 验收: 最终发布与项目级总验收

> 状态：`TODO`
>
> 前置任务：`R7-00`、`R7-01`、`R7-02`、`R7-03`、`R7-04`、`R7-05`、`R7-06`、`R7-07`、`R7-08`
>
> 推荐模型：`gpt-5.5`
>
> 任务类型：最终验收 / Release candidate 冻结

## 背景

这是 R0-R7 整轮重构的最终门禁。目标不是继续增加功能，而是证明项目在新环境、正常链路、并发、故障、安全、演示和文档方面达到可发布、可讲解、可维护状态。

## 目标

新增 `docs/reports/R7-final-release-report.md`，形成最终证据链：

```text
clean release candidate commit
-> fresh Docker bootstrap
-> quick + integration + e2e
-> concurrency/performance report
-> fault injection/recovery report
-> observability/security audit
-> reproducible 3-5 minute demo
-> README/architecture/interview/resume package
-> final PASS/BLOCKED + release tag decision
```

## 范围

允许：

- 从干净 worktree/候选 commit 运行 R0-R7 全部最终验证与报告检查。
- 审查 Git 状态、migration、配置、Secret、Docker、测试、性能/故障数据、文档链接和演示材料。
- 新增最终报告、更新 R7 任务状态、记录遗留风险/后续维护清单。
- 仅修复阻断发布的最小问题并补回归测试；修复后重新运行受影响门禁。
- 在全部通过后建议创建 release tag/版本说明，但不自动 push/tag，除非用户明确授权。

## 非目标

- 不新增 R8 或临时产品功能来延迟结束。
- 不把未通过项改写成“已知限制”以规避验收。
- 不执行生产部署、自动 push、自动创建 GitHub Release 或修改远程分支保护。
- 不删除失败证据、历史报告或用户数据。
- 不以录屏/README 代替 Harness 和真实报告。

## 验收项目

### 可运行性

- 新环境一键构建、migration、health/readiness 和服务重启通过。
- 主 E2E 从创意提交到评测/RAG/Artifact/Phaser Demo 通过。
- mock/真实 Provider 模式和限制明确。

### 工程可靠性

- 幂等提交、重复消费、执行抢占、Outbox/ACK、重试/DLQ、恢复均有自动化与真实故障证据。
- 性能报告包含可复现环境、P50/P95/P99、吞吐、积压、错误和资源。
- trace/log/metric 可定位跨服务 Run，安全审计无未处置阻断项。

### 作品交付

- 3-5 分钟 Demo 可稳定复现且有失败备用路径。
- README、架构图、报告导航、面试问答和简历描述与事实一致。
- 关键取舍、失败边界、AI 协作和 PITFALLS 可由你独立解释。

## 验收标准

- [ ] 工作区基于明确 release candidate commit，除验收报告/状态外无不明改动。
- [ ] 新环境 Docker 一键启动、migration、健康检查与主 E2E 全部通过。
- [ ] quick、integration、e2e、并发性能和故障注入验证均有最新报告。
- [ ] 重复请求/消息、依赖故障和恢复不会产生重复有效执行/Artifact/计费证据。
- [ ] 日志/指标/trace 可定位 Run，安全审计无真实 Secret、跨项目泄露或生产测试入口。
- [ ] 3-5 分钟演示、README、架构图、面试问答和简历描述可复现且不夸大。
- [ ] R0-R7 报告齐全，最终结论为 PASS 或明确 BLOCKED，不用模糊“基本完成”。

## 验证命令

```powershell
git status --short
git diff --check
docker compose config

.\tools\verify.ps1 -Profile quick
.\tools\verify.ps1 -Profile integration
.\tools\verify.ps1 -Profile e2e
.\tools\run-performance-baseline.ps1
.\tools\run-fault-injection.ps1
.\tools\verify-demo.ps1

rg -n "161764|password:\s*123456|local-secret-key|change-this-to-a-strong-secret-key|\bsk-[A-Za-z0-9]{20,}" `
  backend-java frontend-vue python-agent .env.example docker-compose.yml docs
```

## R7 报告模板

```markdown
# R7 Final Release Report

## Release Candidate
- 日期：
- branch/commit：
- 环境与 Docker 版本：
- mock/real Provider：

## Fresh Start 与 E2E
| 门禁 | 结果 | 命令/证据 |

## 并发与性能
- 场景/配置：
- P50/P95/P99/吞吐：
- 重复业务事实：
- 瓶颈与限制：

## 故障与恢复
| 故障 | 预期 | 实际 | 恢复时间 | 证据 |

## 可观测性与安全
- Trace/log/metrics：
- Security audit：
- Remaining risks：

## 演示与投递材料
- 3-5 分钟 Demo：
- README/架构图：
- Interview/resume package：

## R0-R7 完成矩阵
| 阶段 | 报告 | 结论 |

## 最终结论
- PASS / BLOCKED
- Release tag 建议：
- 后续维护清单：
```

## 审查清单

- 是否验收基于脏工作区、未知 commit 或开发机残留数据。
- 是否性能/故障/安全/演示任一项缺少可复现证据。
- 是否把未通过门禁降级成文字说明而仍标 PASS。
- 是否 mock/真实模型、限制和残余风险表述不诚实。
- 是否在未授权情况下自动 push/tag/release。

## 完成定义

- R7 最终报告给出清晰 PASS 或 BLOCKED，所有结论均有命令、报告和 commit 支撑。
- PASS 后，本轮 R0-R7 重构正式结束，项目进入维护、迭代和投递阶段。
