# R5-04: 业务规则与 Runtime 能力匹配 Evaluation

> 状态：`TODO`
>
> 前置任务：`R5-03`
>
> 推荐模型：`gpt-5.5`
>
> 任务类型：规则引擎 / 可解释评测

## 背景

Schema 合法不代表游戏配置在当前 Phaser Runtime 中有意义。例如元素坐标可能超出世界边界、目标数量与 items 不一致、重复 id、规则引用不存在、未支持的玩法能力被模型描述为可用。R5 需要将这些业务语义变成确定性、可解释规则。

## 目标

实现第二层评测：

```text
schema-passed GameConfig
-> RuntimeCapabilityRegistry + GameConfigRuleEvaluator
-> deterministic violations/warnings
-> EvaluationReport(evaluatorType=RULE)
-> eligible / ineligible for runtime smoke
```

## 范围

允许：

- 新增 RuntimeCapabilityRegistry、RuleEvaluator、规则 violation 模型、规则版本与报告持久化。
- 实现最小规则：支持 gameType、世界边界/坐标、正尺寸/速度范围、唯一 item/enemy id、targetItems 与 items 一致、exit/player 合法、规则引用与当前 Runtime 支持能力一致。
- 将规则结果与 schema 报告关联，区分 blocking violation 与 non-blocking warning。
- 为规则可配置阈值、schema/runtime version 增加明确版本标识。
- 添加规则 fixture、边界值、组合冲突、历史 Artifact 重评和解释性测试。

## 非目标

- 不构建通用 Drools/复杂 DSL 或让用户在线编辑业务规则。
- 不靠 LLM 解释规则通过/失败。
- 不修改 Phaser Runtime 功能来迁就模型输出。
- 不做实际浏览器运行 smoke test。
- 不实现 R6 文档知识约束或自动修复 Prompt。

## 约束

- 规则必须针对已通过 Schema 的规范化对象执行；Schema FAILED 直接记录依赖关系并跳过/失败，不得继续伪评测。
- 每条 violation 必须带稳定 code、字段路径、严重级别、期望和实际摘要，不能只有自然语言。
- RuntimeCapabilityRegistry 是当前 Runtime 能力的唯一声明来源，规则和前端不能各自维护冲突名单。
- blocking violation 必须阻止 Runtime smoke/可试玩成功；warning 不得被误标为失败。
- 规则版本必须随 EvaluationReport 记录，历史报告不可因规则升级被静默改写。

## 验收标准

- [ ] Runtime 不支持的 gameType/能力、越界坐标、重复 id、目标数不一致等可得到稳定 violation。
- [ ] 合法示例只产生允许的 warning 或 PASSED，且可进入 Runtime Evaluation。
- [ ] 每条失败能定位字段、规则 code、规则版本与期望/实际摘要。
- [ ] Schema 失败与 Rule skipped/failed 的关系清晰，不存在误判为规则通过。
- [ ] blocking/warning 对后续 Runtime 是否执行的影响有测试。
- [ ] GameConfig contract 与 Phaser Runtime 基础测试不回归。

## 验证命令

```powershell
cd backend-java
mvn -Dtest=*RuleEvaluation*Test,*RuntimeCapability*Test,*GameConfig*Test test
mvn test

cd ..\frontend-vue
npm run test:game-config
npm run build

cd ..
.\tools\verify.ps1 -Profile quick
```

## 审查清单

- 是否使用自由文本错误而没有稳定 code/字段路径。
- 是否在 Schema 失败后仍宣称规则通过。
- 是否让规则与 Phaser Runtime 支持能力各自维护两份冲突列表。
- 是否把 warning 当失败或让 blocking violation 仍进入试玩。
- 是否让规则升级悄悄重写历史评测结论。

## 完成定义

- “结构合法但不可玩”的模型输出可以被确定性规则解释和拦截。
- Runtime 能力边界成为可测试的工程契约。
