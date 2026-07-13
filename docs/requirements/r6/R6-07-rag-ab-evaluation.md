# R6-07: RAG-on/RAG-off 对照评测与影响报告

> 状态：`TODO`
>
> 前置任务：`R5-验收`、`R6-05`、`R6-06`
>
> 推荐模型：`gpt-5.5`
>
> 任务类型：实验设计 / 评测聚合与可解释报告

## 背景

引入 RAG 会增加 token、延迟和复杂度，不能只展示几条“看似相关”的引用。R6 必须用与 R5 相同的确定性评测口径比较 RAG 开关对 GameConfig Schema/Rule/Runtime 通过率、成本和延迟的实际影响。

## 目标

建立受控对照框架：

```text
fixed project documents + fixed test ideas + fixed PromptVersion/model config
-> cohort A: ragEnabled=false
-> cohort B: ragEnabled=true with recorded RetrievalRecords
-> R5 EvaluationReports + ModelCallMetrics
-> compare sample count, pass rate, latency, tokens, cost, retrieval coverage
-> RAG impact report
```

## 范围

允许：

- 新增实验定义/运行/聚合服务或最小脚本、固定 fixture、报告 DTO/API 和 `docs/reports` 模板。
- 复用 R5 PromptVersion、ModelCallMetric、EvaluationReport 与 R6 RetrievalRecord，按相同过滤条件聚合。
- 实现 ragEnabled、项目文档版本、embedding/chunking/retrieval version、PromptVersion、model/provider 的实验快照。
- 计算真实模型/Mock 分离的样本数、Schema/Rule/Runtime 通过率、P50/P95、token/cost、检索覆盖率和失败类型分布。
- 添加相同输入、RAG 关闭、空知识库、文档失效、mock 排除、样本不足和权限隔离测试。

## 非目标

- 不自动选择“更好” Prompt/RAG 配置，不做显著性推断或在线流量分配。
- 不强制每次开发都调用真实付费模型；可使用受控 fixture/fake provider 验证管线。
- 不把不同 PromptVersion、不同模型、不同文档版本的样本混成无解释对比。
- 不实现前端完整可视化，展示由 R6-08 负责。
- 不做 R7 的大规模性能压测或录屏材料。

## 约束

- 对照组必须固定用户输入、项目、PromptVersion、模型参数、评测规则版本和运行环境；差异仅限 RAG 开关/明确检索配置。
- 默认排除 mock；若展示 mock，必须单独 cohort 且不与真实结论合并。
- 报告必须写明样本数、失败/跳过、时间窗口、文档/Embedding/chunking 版本、成本数据缺失情况。
- RAG-off 不能产生 RetrievalRecord；RAG-on 无引用/检索失败必须以独立结果显示，而非视为成功检索。
- 实验只读汇总已有记录或明确创建可追溯测试 Run，不得修改历史 AgentRun/Report。

## 验收标准

- [ ] 可在同口径下比较 RAG-on/RAG-off 的 Schema、Rule、Runtime 通过率、延迟、token、成本与样本数。
- [ ] 报告可追溯项目文档/Chunking/Embedding/Retrieval/Patch/Model 版本与 RAG 开关。
- [ ] mock、空知识库、检索失败、样本不足、未评测数据不会扭曲真实结论。
- [ ] 文档删除/失效后新 RAG-on cohort 不引用旧 Chunk，历史 cohort 仍保留证据。
- [ ] 实验 API/报告不泄露跨项目知识、原始 Prompt 或完整文档内容。
- [ ] 聚合、固定 fixture 和权限测试通过。

## 验证命令

```powershell
cd backend-java
mvn -Dtest=*RagExperiment*Test,*RagComparison*Test,*RetrievalRecord*Test,*PromptMetric*Test test
mvn test

cd ..\python-agent
python -m pytest

cd ..
.\tools\verify.ps1 -Profile integration
.\tools\verify.ps1 -Profile e2e
```

## 审查清单

- 是否对比了不同 Prompt/model/doc version，导致结论无效。
- 是否将 mock/空检索样本混入真实 RAG 成功率。
- 是否忽略成本/延迟变化只报告通过率。
- 是否通过重新检索来代替历史 RetrievalRecord。
- 是否让实验报告泄露项目文档或跨项目信息。

## 完成定义

- RAG 的价值和代价可用 R5 的确定性证据量化，而不是凭主观示例判断。
- 项目具备可在面试中讲清楚的 RAG 对照实验链路。
