# R5-00: Prompt、评测与模型指标契约冻结

> 状态：`TODO`
>
> 前置任务：`R4-验收`
>
> 推荐模型：`gpt-5.5`
>
> 任务类型：架构契约 / 只写文档

## 背景

R1 已引入 PromptVersion 与 WorkflowRun 快照，R2 已有最小 GameConfig 校验 hook，R4 已提供稳定的运行中心。但当前系统仍不能回答：某个产物使用了什么模型和 Prompt、是否来自 mock fallback、为什么评测失败、两个 PromptVersion 哪个更可靠/更快/更省成本。

## 目标

新增 `docs/requirements/r5/R5-prompt-evaluation-metrics-design.md`，冻结 R5 的完整证据链：

```text
PromptVersion snapshot
-> Python agent execution response
-> Java AgentRun / ModelCallMetric
-> Artifact
-> deterministic schema / rule / runtime evaluation
-> EvaluationReport
-> PromptVersion aggregate comparison
```

文档必须明确数据字段、版本关系、指标计算公式、评测层边界、失败语义、查询 API 和前端展示边界。

## 范围

允许：

- 阅读 R1 PromptVersion、R2 GameConfig hook、R3 Agent/消息可靠性、R4 Read Model 与现有 Python Agent 响应。
- 新增设计文档、实体关系图、时序图、指标字典、评测结果样例、任务依赖图与回退方案。
- 明确后续 R5 子任务可修改目录、测试策略、数据迁移顺序与敏感数据脱敏规则。

## 非目标

- 不修改 Java、Python、Vue 业务代码。
- 不接入 R6 RAG、Embedding、检索记录或知识库。
- 不使用 LLM-as-Judge 作为 R5 的通过条件。
- 不建立商业计费、真实财务结算或多租户配额系统。
- 不更改 `docs/game-config-schema.md` 已冻结的结构契约。

## 约束

- 评测的第一性证据是确定性 Schema、业务规则与 Runtime smoke test；LLM-as-Judge 最多作为未来补充。
- `mock=true` 必须从 Python 响应一路落库并在查询/聚合/UI 中显式区分，默认不得混入真实模型成功率/成本。
- PromptVersion 内容不可原地修改；ACTIVE 切换只影响未来 Run，历史 AgentRun 始终可追溯快照。
- 模型输出、Prompt、错误和指标需要脱敏、分级保存；不能把 API Key、Authorization 或完整敏感上下文暴露给前端。
- 成本、token、延迟、分母和过滤条件必须有明确口径，不能由页面临时计算不同版本。

## 验收标准

- [ ] 文档定义 Prompt、AgentRun、Metric、Artifact、EvaluationReport 的关联与不可变边界。
- [ ] 文档定义真实模型、mock fallback、Provider/Schema/Runtime 失败的可区分语义。
- [ ] 三层评测的输入、输出、通过条件、失败证据和执行位置明确。
- [ ] PromptVersion 对比指标、样本数、时间窗口、mock 过滤规则和零样本语义明确。
- [ ] R5 与 R4 运行中心、R6 RAG、R7 性能/投递材料的边界明确。

## 验证命令

```powershell
git diff --check
rg -n "PromptVersion|mock|ModelCallMetric|EvaluationReport|schema|rule|runtime|aggregate" docs\requirements\r5\R5-prompt-evaluation-metrics-design.md
```

## 审查清单

- 是否将 mock 结果混入真实模型指标。
- 是否只记录文本成功而没有 Schema/规则/Runtime 证据。
- 是否允许 ACTIVE Prompt 回写历史运行。
- 是否让 Dashboard 自行猜测成本、成功率或分母。
- 是否把 R6 检索和 R7 压测提前塞进 R5。

## 完成定义

- R5 的数据、评测、指标和展示语义已冻结。
- 后续任务可以在不重新解释“成功”“成本”“mock”和“评测通过”的前提下实施。
