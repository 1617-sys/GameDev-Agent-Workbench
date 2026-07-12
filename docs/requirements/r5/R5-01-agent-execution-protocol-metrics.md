# R5-01: 统一 Agent 执行协议与 ModelCallMetric 落库

> 状态：`TODO`
>
> 前置任务：`R5-00`
>
> 推荐模型：`gpt-5.5`
>
> 任务类型：Java-Python 契约 / 数据迁移与集成测试

## 背景

现有 Python Agent 与 Java AgentRun 已能生成内容，但模型名、Provider、token、延迟、估算成本、raw output reference 和 mock fallback 的记录尚未形成稳定的跨服务契约，无法支持可靠对比与故障定位。

## 目标

统一 Python 内部 Agent 执行请求/响应与 Java 持久化：

```json
{
  "status": "SUCCESS|FAILED",
  "output": {},
  "raw_output_ref": "optional-safe-reference",
  "model": "model-name",
  "provider": "provider-name",
  "usage": { "input_tokens": 0, "output_tokens": 0, "estimated_cost": 0 },
  "latency_ms": 0,
  "mock": false,
  "trace_id": "trace-id"
}
```

并将一次 Agent 调用的不可变证据写入 `AgentRun` 与 `ModelCallMetric`（或等价独立指标表）。

## 范围

允许：

- 扩展 Python Pydantic 请求/响应模型、Provider adapter、mock fallback 标记和错误分类。
- 扩展 Java Client DTO、AgentRun、Mapper、Flyway migration、Metric Entity/Repository/Service。
- 记录 workflowRunId/stepRunId/promptVersionId、provider、model、input/output token、estimatedCost、latency、mock、status、error category、traceId、createdAt。
- 记录原始模型输出的安全引用、hash 或受限存储标识；输出的规范化结构仍走 Artifact/StepRun。
- 新增 Java-Python 契约测试、mock/真实响应解析测试、缺失 usage/未知 Provider/超时失败测试。
- 更新 R4 查询 Read Model 的最小引用字段，但不做完整 Dashboard UI。

## 非目标

- 不实现 PromptVersion 管理 UI 或 ACTIVE 切换。
- 不实现 Schema/规则/Runtime EvaluationReport。
- 不接入 RAG retrievals、向量库或 PDF 文档。
- 不调用真实付费模型作为测试前提。
- 不将完整原始 Prompt/输出无条件返回给浏览器。

## 约束

- `mock` 为显式布尔事实，缺失/未知时不能默认当作真实模型调用。
- token/cost 不可用时记录 `null`/未知原因，不得伪造 0 并混入成本平均值。
- 每次模型调用都以独立 metric 记录；一次 WorkflowRun 的多个 Step/attempt 不能覆盖同一条数据。
- Java 不信任 Python 返回的自由文本错误，必须映射为受控错误类别并脱敏。
- Agent 调用网络等待不持有长数据库事务；结果持久化在调用完成后短事务进行。
- raw output 只能保存安全引用/受控内容，不能写入 Secret、Authorization 或用户私密上下文。

## 验收标准

- [ ] Java 能解析并持久化模型、Provider、token、成本、延迟、mock、traceId 和错误分类。
- [ ] mock fallback 在 AgentRun、Metric、查询结果中均显式可见，不混入真实模型聚合。
- [ ] 每个 StepRun/attempt 的 Agent 调用可追溯到 PromptVersion 和 Metric。
- [ ] usage 缺失、Provider 超时、结构响应非法和 Python mock 结果都有稳定处理与测试。
- [ ] 原始输出仅以安全引用/受限快照保存，浏览器接口不会泄露敏感内容。
- [ ] Java-Python 契约测试、R3 integration Harness 与 quick Harness 通过。

## 验证命令

```powershell
cd backend-java
mvn -Dtest=*AgentExecution*Test,*ModelCallMetric*Test,*AgentRun*Test test
mvn test

cd ..\python-agent
python -m compileall app
python -m pytest

cd ..
.\tools\verify.ps1 -Profile quick
.\tools\verify.ps1 -Profile integration
```

## 审查清单

- 是否将 mock 或未知 usage 当作真实模型/零成本。
- 是否只在内存日志记录指标、重启后不可查询。
- 是否让一个 WorkflowRun 的多次调用相互覆盖。
- 是否将 Python 原始错误/Prompt/Secret 透传给前端。
- 是否在外部 Provider 调用期间持有事务。

## 完成定义

- 每次 Agent 调用均留下可追溯、可区分 mock 的持久化技术与成本证据。
- R5 后续评测和比较可稳定关联到具体模型、Prompt 与执行尝试。
