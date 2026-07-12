# R5 Prompt Evaluation Metrics Report

## 环境

- 日期：2026-07-12
- 分支：`codex/evaluation-metrics`
- 基线 commit：`09d6e58`
- 浏览器/测试 profile：Playwright Chromium；quick、integration、e2e

## Prompt 与调用证据

- PromptVersion immutable/active：部分具备。已有不可变数据库触发器与 Run snapshot 读取；但 R5-02 的版本创建、ACTIVE 原子切换、归档与受控管理 API 未完整实现和验收。
- AgentRun / ModelCallMetric：已记录 PromptVersion、provider、model、mock、trace、usage/成本缺失状态；原始输入/输出不在 AgentRun 浏览器 VO 序列化。
- mock provenance：Python 执行 envelope 与 Metric 均使用显式 mock 状态；analytics 默认仅选择 `mockState=FALSE`。

## 三层评测

- Schema：存在确定性 `SchemaEvaluator` 与 append-only `SCHEMA` 报告。
- Rule：存在 RuntimeCapabilityRegistry、稳定 rule violation 与 `RULE` 报告。
- Runtime smoke：Chromium 通过 canvas/Phaser readiness 的桌面和 375px smoke；后端 Runtime `EvaluationReport` 尚未由浏览器结果持久化。
- Artifact eligibility：编排器在 Runtime 结果未记录为 PASSED 时保持 `runtimeEligible=false`，不会虚假标为可试玩。

## 指标与前端

- Aggregation API：按用户、项目、AgentType、`[from,to)` 和 includeMock 查询；默认排除 mock，null usage/cost 不补零。
- Prompt comparison：已提供单版本列表聚合和固定 P50/P95；尚未实现任务卡要求的两版本 comparison 端点及三层评测通过率。
- UI / mobile：Prompt 指标页面显示 mock、零样本、缺失和样本不足；Run 详情尚未消费完整评测报告摘要。

## Harness 结果

| 命令 | 结果 | 证据 |
| --- | --- | --- |
| `backend-java/mvn test` | PASS | 全量 Maven 通过（Docker 依赖用例按环境跳过） |
| `python-agent/python -m compileall app && python -m pytest` | PASS | Python 编译与 pytest 通过 |
| `frontend-vue` unit/game-config/runtime-smoke/e2e/build | PASS | Playwright Runtime 1280px/375px 通过 |
| `tools/verify.ps1 -Profile quick` | PASS | Java、Python、Vue、Compose config 通过 |
| `tools/verify.ps1 -Profile integration` | PASS | Testcontainers 环境不可用时相关用例跳过 |
| `tools/verify.ps1 -Profile e2e` | PASS | R4 浏览器 Harness 通过 |
| Secret/弱默认值扫描、`git diff --check` | PASS | 未发现任务卡列出的敏感模式 |

## 已知风险

- R5-02 生命周期管理不完整：缺少可验收的版本创建/ACTIVE/归档并发链路。归属：R5-02。
- Runtime smoke 浏览器证据未写入 `RUNTIME` EvaluationReport，三层通过无法把 Artifact 标为 eligible。归属：R5-05/R5-06。
- 报告重评 attempt 与完整证据查询尚不完整。归属：R5-06。
- 两 PromptVersion 对比、三层通过率和完整权限 API 语义尚未实现。归属：R5-07/R5-08。

## R6 准入结论

- `BLOCKED`
- 原因：测试 Harness 全部通过，但上述 R5 数据不可变性、Runtime 持久化证据和可比较聚合口径尚未满足冻结契约；在此之前引入 R6 retrieval evidence 会扩大不可追溯面。
