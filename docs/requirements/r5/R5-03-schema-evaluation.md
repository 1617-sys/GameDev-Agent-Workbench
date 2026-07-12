# R5-03: 确定性 Schema Evaluation 与结构化输出证据

> 状态：`TODO`
>
> 前置任务：`R5-00`、`R5-01`、`R5-02`
>
> 推荐模型：`gpt-5.5`
>
> 任务类型：结构化输出评测 / 契约测试

## 背景

R2 的 GameConfig hook 已能阻止明显非法结构进入 Runtime，但评测结果尚未成为独立可追溯报告，也没有统一到 Python Pydantic、Java 结构校验和 Artifact schema metadata 的证据链。

## 目标

实现第一层确定性评测：

```text
normalized Artifact candidate
-> schema key/version resolved from frozen Prompt/Step snapshot
-> JSON/Pydantic/JSON Schema compatible validation
-> EvaluationResult(SUCCESS|FAILED, violations, evidence)
-> persisted EvaluationReport
```

GameConfig 必须继续遵守 `docs/game-config-schema.md`：先验证 required structure，再做 aliases/optional normalization。

## 范围

允许：

- 定义 SchemaEvaluator 接口、schema registry/adaptor、违反项模型和最小 EvaluationReport 持久化。
- 复用/整理现有 GameConfig parser、Java hook、Python Pydantic Model 的契约，并增加跨层契约测试。
- 校验 schema key/version、JSON 格式、required fields、类型、支持 aliases、wrapper 提取和不支持 gameType。
- 将校验的输入 hash/reference、schema version、violations、evaluatedAt、evaluatorType 写入报告。
- 为非 GameConfig 文本步骤定义明确的“未配置 schema/跳过”语义，不能伪称通过。
- 添加有效/无效 JSON、缺字段、错误类型、alias、wrapper、版本不匹配与历史 schema 测试。

## 非目标

- 不修改 GameConfig required fields、aliases 或视觉默认值契约。
- 不实现业务规则评测、Phaser Runtime smoke test、LLM-as-Judge 或质量打分。
- 不允许为了通过评测自动补齐 required structure。
- 不执行或加载模型生成的 JavaScript/HTML。
- 不实现 R6 文档/RAG 结构校验。

## 约束

- 必须在 normalization/defaults 之前判断 required structure；无关对象不得因默认值变成“通过”。
- Schema 选择来自冻结的 PromptVersion/StepPlan/Artifact 元数据，不能依赖当前 ACTIVE Prompt。
- EvaluationReport 需区分 `PASSED`、`FAILED`、`SKIPPED`、`ERROR`，并保存结构化 violations 而非只有字符串。
- schema 失败必须阻止 GameConfig 进入 Runtime/可试玩 Artifact 成功链路，但不得删除原始受控证据。
- Java/Python/前端对同一公开 GameConfig fixture 的接受/拒绝语义必须一致或明确记录差异。

## 验收标准

- [ ] 合法 GameConfig 产生可追溯的 schema PASSED 报告并可进入后续评测。
- [ ] 非法 JSON、缺失 required fields、错误类型/坐标、错误 gameType 不会被 defaults/aliases 掩盖。
- [ ] 已支持 aliases/wrappers 在跨层契约测试中保持兼容。
- [ ] schema key/version 不匹配或未配置时有明确 FAILED/SKIPPED 结果。
- [ ] schema 失败不会生成可试玩成功 Artifact，也不会将 Workflow 伪标 SUCCESS。
- [ ] Java、Python、Vue GameConfig 测试与 R2 Hook 回归通过。

## 验证命令

```powershell
cd backend-java
mvn -Dtest=*SchemaEvaluation*Test,*GameConfig*Test,*EvaluationReport*Test test
mvn test

cd ..\python-agent
python -m pytest

cd ..\frontend-vue
npm run test:game-config
npm run build

cd ..
.\tools\verify.ps1 -Profile quick
```

## 审查清单

- 是否在 defaults/normalization 后才校验 required structure。
- 是否悄悄扩大或删除已有 alias 兼容性。
- 是否只写日志、未保存结构化评测报告。
- 是否将 schema 失败吞为普通文本成功。
- 是否让不同服务对同一 fixture 得出无解释的相反结论。

## 完成定义

- Schema 通过不再只是临时 hook 结果，而是可追溯、可比较的结构化评测证据。
- 非法 AI 输出无法伪装成可运行 GameConfig。
