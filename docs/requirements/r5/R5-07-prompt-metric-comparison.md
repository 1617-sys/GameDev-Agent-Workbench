# R5-07: PromptVersion 指标聚合与对比查询 API

> 状态：`TODO`
>
> 前置任务：`R5-01`、`R5-02`、`R5-06`
>
> 推荐模型：`gpt-5.5`
>
> 任务类型：分析查询 / 统计口径测试

## 背景

有了 PromptVersion、ModelCallMetric 和 EvaluationReport 后，项目需要把逐条证据转化为可解释的版本对比：成功率、Schema/Rule/Runtime 通过率、P50/P95 延迟、token、估算成本和样本量。

## 目标

新增按权限和过滤条件聚合的查询 API，例如：

```http
GET /api/v1/analytics/prompt-versions?agentType=...&from=...&to=...&includeMock=false
GET /api/v1/analytics/prompt-versions/{id}/comparison?against={id}
```

返回固定口径的 PromptVersion 指标与样本元数据，而不是让前端自行扫描所有 AgentRun 计算。

## 范围

允许：

- 新增聚合 Mapper/Repository、AnalyticsService、Controller、VO/DTO、必要索引和分页/时间窗口校验。
- 计算按 PromptVersion/agentType/project/user/time window 的调用数、真实/Mock 样本数、成功率、三层评测通过率、平均/P50/P95 延迟、input/output token、estimated cost。
- 定义 includeMock 默认值、零样本/不完整 usage/评测未执行的返回语义。
- 支持两个版本并排比较，返回统一分母、过滤条件、统计窗口、版本元数据和数据新鲜度。
- 添加授权/项目隔离、时间边界、mock 过滤、重复 metric、null usage、样本不足、P95 算法一致性测试。

## 非目标

- 不实现 UI 图表或大屏，前端展示由 R5-08 完成。
- 不做复杂实验流量分配、显著性统计、自动选优或商业成本结算。
- 不查询/展示完整 Prompt 内容或模型原始输出。
- 不混入 R6 RAG retrieval 指标。
- 不将测试 mock 记录默认视为真实样本。

## 约束

- 指标口径必须在 API 返回中显式提供：时间窗口、时区、过滤条件、includeMock、样本数、缺失数据数量。
- 默认聚合排除 `mock=true`；用户显式要求时可单独查看/对比 mock，不能静默合并。
- 只聚合当前用户有权访问的项目/Run，禁止跨项目、跨用户数据泄露。
- 成功率与评测通过率的分母必须分别明确，不能把未评测、跳过或失败样本混为一谈。
- P50/P95 算法、最小样本提示和成本精度要固定并由测试覆盖。
- 查询必须只读且避免逐行 N+1 聚合；大时间窗有分页/上限或合理索引。

## 验收标准

- [ ] 指定 PromptVersion 可查询真实模型的调用数、成功率、三层评测通过率、延迟、token、成本与样本数。
- [ ] 默认结果排除 mock；includeMock 行为、独立 mock 样本数和混合风险清楚可见。
- [ ] 两版本比较使用相同过滤口径，零样本/null usage/未评测样本有稳定响应。
- [ ] 未授权用户无法聚合其他用户/项目的 AgentRun 或成本数据。
- [ ] 聚合 API 不会修改 AgentRun、Metric、EvaluationReport 或 PromptVersion。
- [ ] 指标计算、权限隔离、边界时间与 P95 测试通过。

## 验证命令

```powershell
cd backend-java
mvn -Dtest=*PromptMetric*Test,*PromptVersionAnalytics*Test,*ModelCallMetric*Test test
mvn test

cd ..
.\tools\verify.ps1 -Profile quick
.\tools\verify.ps1 -Profile integration
```

## 审查清单

- 是否默认将 mock 与真实模型数据混合。
- 是否未说明成功率/评测通过率的分母和时间窗口。
- 是否在 Java 内存中全表拉取后聚合，造成性能/权限问题。
- 是否让用户跨项目读取 Prompt/成本数据。
- 是否把 null usage 当 0 并扭曲成本/平均值。

## 完成定义

- PromptVersion 对比有统一、可解释、可复现的服务端数据口径。
- R5 前端可直接展示聚合结果而不重复实现统计逻辑。
