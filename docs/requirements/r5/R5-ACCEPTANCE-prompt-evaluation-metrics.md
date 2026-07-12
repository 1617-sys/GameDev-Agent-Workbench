# R5 验收: Prompt、评测与模型指标总验收

> 状态：`TODO`
>
> 前置任务：`R5-00`、`R5-01`、`R5-02`、`R5-03`、`R5-04`、`R5-05`、`R5-06`、`R5-07`、`R5-08`
>
> 推荐模型：`gpt-5.5`
>
> 任务类型：阶段验收 / 只验证与记录

## 背景

R5 的完成不是“多了一张评测表”，而是每次模型调用、PromptVersion、结构化产物和三层评测都拥有可追溯证据；用户能安全比较版本质量、延迟与成本，且 mock 不会伪装成真实能力。

## 目标

新增 `docs/reports/R5-prompt-evaluation-metrics-report.md`，以可复现证据证明：

```text
immutable PromptVersion
+ AgentRun / ModelCallMetric evidence
+ explicit mock provenance
+ Schema + Rule + Runtime evaluations
+ immutable EvaluationReports and artifact eligibility
+ permission-safe aggregate comparison
+ run-center quality/metric UI and browser tests
= R6 can add retrieval evidence without losing evaluation baseline
```

## 范围

允许：

- 运行 R5/R4/R3/R2/R1/R0 的相关测试、quick/integration/e2e Harness、浏览器 runtime smoke 和前端响应式检查。
- 审查 Prompt 生命周期、跨服务协议、评测链路、指标口径、权限、mock、敏感信息、数据迁移和 diff。
- 新增 R5 验收报告、更新任务卡状态、记录遗留风险和 R6 准入结论。
- 仅修复阻断验收的最小问题并补回归测试。

## 非目标

- 不实现 R6 RAG 文档、Embedding、检索记录、项目隔离检索或对照实验。
- 不以 LLM-as-Judge 取代确定性三层评测。
- 不删除旧 Prompt/Artifact/WorkflowRun 数据或做破坏性回滚。
- 不进行真实生产模型成本结算、复杂实验分流或上线运营后台。
- 不重写 R4 运行中心、R3 可靠性链路或 Phaser Runtime。

## 验收项目

### 调用与 Prompt 证据

- 每个 AgentRun/StepRun 可追溯 PromptVersion、Provider、模型、token、成本、延迟、状态、traceId 和 mock。
- PromptVersion 不可变，ACTIVE 切换只影响未来运行，历史快照可查。
- mock 与真实模型指标、报告和 UI 始终区分。

### 三层评测

- Schema 评测遵循 GameConfig required fields/aliases/rejection rules。
- 规则评测验证 Runtime 能力、边界、引用和业务一致性。
- Runtime smoke 用受控浏览器证明 Phaser 初始化与关键对象可用。
- 每层报告可追溯、不可覆盖，失败不会虚假标记 Artifact 可试玩。

### 指标与体验

- 服务端按固定口径聚合 PromptVersion 成功率、评测通过率、延迟、token、成本和样本数。
- 查询按用户/项目权限隔离，默认排除 mock。
- 运行中心可展示评测层级、mock、版本指标、缺失/零样本/错误状态，桌面/移动端可用。

## 验收标准

- [ ] 修改 ACTIVE Prompt 不影响已创建或运行中的 WorkflowRun/AgentRun 快照。
- [ ] 每个 AgentRun 可追踪模型、Provider、PromptVersion、token、延迟、成本、traceId 与 mock。
- [ ] 非法 GameConfig 无法通过 Schema/Rule/Runtime 门禁进入可试玩链路。
- [ ] 每个 Artifact 的三层报告、violations、版本与证据引用可查询且不被重试覆盖。
- [ ] Dashboard/analytics 可在相同口径下比较两个 PromptVersion 的真实模型成功率、评测通过率、耗时和成本。
- [ ] mock、零样本、缺失 usage、无权限和网络错误均有明确 API/UI 语义。
- [ ] Maven、Python、Vue、runtime smoke、quick/integration/e2e Harness 通过，并生成 R5 报告和 R6 准入结论。

## 验证命令

```powershell
git status --short
git diff --check
docker compose config

cd backend-java
mvn test

cd ..\python-agent
python -m compileall app
python -m pytest

cd ..\frontend-vue
npm run test:unit
npm run test:game-config
npm run test:runtime-smoke
npm run test:e2e
npm run build

cd ..
.\tools\verify.ps1 -Profile quick
.\tools\verify.ps1 -Profile integration
.\tools\verify.ps1 -Profile e2e

rg -n "PromptVersion|ModelCallMetric|EvaluationReport|mock|SchemaEvaluator|RuleEvaluator|Runtime" backend-java\src\main\java python-agent\app frontend-vue\src
rg -n "161764|password:\s*123456|local-secret-key|change-this-to-a-strong-secret-key|\bsk-[A-Za-z0-9]{20,}" `
  backend-java frontend-vue python-agent .env.example docker-compose.yml
```

## R5 报告模板

```markdown
# R5 Prompt Evaluation Metrics Report

## 环境
- 日期：
- 分支：
- 基线 commit：
- 浏览器/测试 profile：

## Prompt 与调用证据
- PromptVersion immutable/active：
- AgentRun / ModelCallMetric：
- mock provenance：

## 三层评测
- Schema：
- Rule：
- Runtime smoke：
- Artifact eligibility：

## 指标与前端
- Aggregation API：
- Prompt comparison：
- UI / mobile：

## Harness 结果
| 命令 | 结果 | 证据 |

## 已知风险
- 风险：
- 归属阶段：R6 / R7

## R6 准入结论
- PASS / BLOCKED
- 原因：
```

## 审查清单

- 是否让 mock 伪装真实模型，或将 null usage 当 0。
- 是否允许改写历史 PromptVersion/EvaluationReport。
- 是否只做 JSON 检查却遗漏规则/Runtime 证据。
- 是否让失败 Artifact 仍标记可试玩或让 UI 显示虚假成功。
- 是否允许跨用户读取 Prompt、成本、模型输出或评测证据。
- 是否把 R6 检索/RAG 与 R7 性能工作混进 R5。

## 完成定义

- R5 报告与任务状态已更新，质量/指标/版本证据可自动验证并能被用户解释。
- R6 可在稳定的 Prompt、Artifact、评测和指标基线上增加检索证据与对照实验。
