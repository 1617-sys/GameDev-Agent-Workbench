# R2-06: GameConfig Artifact 校验与 Evaluation Hook 接入

> 状态：`DONE`
>
> 前置任务：`R2-02`、`R2-03`、`R2-05`
>
> 推荐模型：`gpt-5.4`（实现） / `gpt-5.5`（首次审查）
>
> 任务类型：结构化产物契约 / 回归测试

## 背景

GameConfig 是 AI 输出进入 Phaser Runtime 的唯一结构化边界。R0 已固定前端的提取、校验和归一化契约；R2 要确保 Runner 中的 `GAME_CONFIG_GENERATE` 步骤把这个契约作为产物写入和 GameBuild 的前置条件，而不是只把模型文本当作成功输出。

## 目标

实现最小的 Runner 后处理链：

```text
GAME_CONFIG_GENERATE StepExecutionResult
-> extract candidate GameConfig
-> validate current game-config schema contract
-> write Artifact schemaKey/schemaVersion/content summary
-> invoke WorkflowEvaluationHook
-> allow Demo GameBuild only after success
```

这里的 Evaluation hook 只表达“结构化产物是否可被下游使用”的结果，不实现 R5 的模型质量评分、成本统计或评测报表。

## 范围

允许：

- 为 `GAME_CONFIG_GENERATE` 引入 Java 侧最小的结构化结果适配、校验调用或与前端/现有 parser 一致的契约校验实现。
- 为 Artifact/StepRun 写入 `schemaKey`、`schemaVersion`、校验结果摘要和失败原因。
- 实现 `WorkflowEvaluationHook` 的最小 GameConfig hook，返回可由 Runner 和 Demo adapter 消费的通过/失败结论。
- 新增 Java 测试和前端契约回归测试，覆盖有效 Config、支持 aliases、无效 JSON、缺少必要结构、错误 gameType。
- 更新与 R2 产物语义有关的设计文档，若实际实现揭示了契约差异。

## 非目标

- 不重新定义 `docs/game-config-schema.md` 的 required fields、aliases 或 rejection rules。
- 不实现 R5 的质量打分、A/B Prompt 实验、token/cost 指标或 EvaluationReport 全表。
- 不让 Java 解释、执行或动态加载 AI 生成的 JavaScript/HTML。
- 不改 Phaser 游戏规则、前端可视化或 Python Agent 路由。
- 不接 MQ、重试或异步文件处理。

## 约束

- 验证必须发生在默认值/归一化之前；可选视觉字段的默认值不能掩盖 required structure 缺失。
- Java 侧若新增校验，不得与前端测试覆盖的 alias 语义漂移；差异须先写明并补充跨层契约测试。
- 校验失败必须使该 StepRun 明确失败，且不得执行 GameBuild 或写入“可试玩”的成功 Artifact。
- 原始模型输出只作为可追踪证据处理，不能进入脚本执行、SQL 拼接或 HTML 注入路径。
- schema version 必须从已冻结的 Run/Step 快照或文档契约明确获取，不得猜测当前版本。

## 验收标准

- [ ] 有效 GameConfig 可以被标记为已验证的 `game-config` Artifact。
- [ ] R0 支持的 aliases 在 Runner 产物链路中保持兼容。
- [ ] 无效 JSON、缺失 required 字段、错误 `gameType`、错误坐标/数组类型均阻止成功完成。
- [ ] 校验失败不会调用 GameBuild，也不会将 WorkflowRun 标为 SUCCESS。
- [ ] 前端 `test:game-config` 与 Java 的相关契约/步骤测试同时通过。
- [ ] 不新增执行不可信模型代码的能力。

## 验证命令

```powershell
cd backend-java
mvn -Dtest=*GameConfig*Test,*WorkflowEvaluationHook*Test,*WorkflowRunner*Test test
mvn test

cd ..\frontend-vue
npm run test:game-config
npm run build

cd ..
.\tools\verify.ps1 -Profile quick
```

## 审查清单

- 是否在归一化后才判断 required fields，从而接受了无关模型输出。
- 是否私自扩大或删除了前端已支持 aliases。
- 是否把 schema 校验失败吞成普通文本成功。
- 是否在失败 Config 下仍调用 GameBuild 或标记 Workflow SUCCESS。
- 是否将 R5 的评测体系、前端页面或 Python 服务一并重构。

## 完成定义

- GameConfig 已成为 Runner 中受验证、可追踪、可阻止下游构建的正式 Artifact。
- R2 的 Demo 成功含义不再只是“模型返回了非空文本”。
