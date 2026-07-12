# R5-06: EvaluationReport 持久化、编排与 Artifact 门禁

> 状态：`TODO`
>
> 前置任务：`R5-03`、`R5-04`、`R5-05`
>
> 推荐模型：`gpt-5.5`
>
> 任务类型：评测编排 / 生命周期一致性

## 背景

Schema、规则和 Runtime evaluator 分别可用后，系统还需要一个统一、可重试、可查询的报告模型和编排顺序。否则某个 Hook 的临时日志无法形成 Artifact 是否可进入 Runtime 的正式结论。

## 目标

建立评测编排链：

```text
Artifact created
-> Schema Evaluation
-> Rule Evaluation (only schema pass)
-> Runtime Evaluation (only blocking rules pass)
-> persist immutable EvaluationReport records
-> aggregate artifact eligibility
-> update WorkflowRun/StepRun event/read model
```

## 范围

允许：

- 新增/完善 EvaluationReport Entity、Flyway migration、Mapper、Service、状态/类型枚举与查询 DTO。
- 以短事务持久化每层评测报告、输入 hash/reference、evaluator/schema/rule/runtime version、score（如适用）、violations、evidence reference、时间与状态。
- 实现 EvaluationOrchestrator，按照前置通过关系执行并保存 `PASSED/FAILED/SKIPPED/ERROR`。
- 在 Artifact 中保存最小 eligibility/最后报告引用，供 R4 运行页展示而不混淆 Artifact 内容。
- 把评测完成/失败写入 R4 WorkflowRunEvent，保持 query/SSE 可见。
- 增加重复编排、部分失败、持久化异常、重复消息、历史 Artifact 重新评测和状态一致性测试。

## 非目标

- 不实现人工评测编辑、批量运营后台、LLM Judge 或 Prompt 自动优化。
- 不将评测结果当作唯一 WorkflowRun 成败标准；每种 WorkflowKey 的策略必须明确。
- 不重新实现 R3 重试/DLQ；评测错误只按既有错误分类协作。
- 不做 R6 RAG evidence/检索质量评测。
- 不把完整报告 payload 直接塞入 SSE 事件。

## 约束

- 评测报告不可原地改写；重新评测需生成新的报告/评测 attempt 并关联旧报告。
- 后一层只有当前置 blocking 条件满足时执行；跳过必须有明确原因而非伪通过。
- 任意层报告写入失败时，不得把 Artifact 标记 runtimeEligible 或向 UI 发布成功事件。
- 重复消费/重试下，同一 Artifact + evaluator + evaluation attempt 不能生成无界重复报告。
- 评测不持有长事务等待浏览器/网络；每层结果独立短事务保存。
- eligibility 必须由已持久化报告计算，不能由前端或临时内存值决定。

## 验收标准

- [ ] 同一 Artifact 可查询到 Schema、Rule、Runtime 三层报告及其顺序/依赖关系。
- [ ] Schema/Rule 失败时后续层为可解释 SKIPPED/FAILED，Runtime 不会错误执行。
- [ ] 三层通过后 Artifact 才被标记可试玩/eligible，失败时不会出现虚假成功入口。
- [ ] 评测报告、Artifact eligibility、WorkflowRunEvent 与 R4 查询结果一致。
- [ ] 重复编排或消息重投不会造成无限重复报告或覆盖历史证据。
- [ ] 相关单元、集成和浏览器 smoke 测试通过。

## 验证命令

```powershell
cd backend-java
mvn -Dtest=*EvaluationOrchestrator*Test,*EvaluationReport*Test,*Artifact*Test,*WorkflowRunEvent*Test test
mvn test

cd ..\frontend-vue
npm run test:runtime-smoke
npm run test:game-config

cd ..
.\tools\verify.ps1 -Profile integration
.\tools\verify.ps1 -Profile e2e
```

## 审查清单

- 是否把三层评测结果仅写日志、不持久化。
- 是否 schema/rule 失败后仍执行 Runtime 并标记可试玩。
- 是否失败报告写入异常却仍更新 Artifact eligibility/SSE 成功。
- 是否重试覆盖旧报告而非保留评测历史。
- 是否在长事务里等待浏览器运行。

## 完成定义

- 评测成为 Artifact 生命周期的正式、可追溯门禁，而不是散落的 Hook。
- R4 可安全展示 Artifact 是否可用及其失败原因摘要。
