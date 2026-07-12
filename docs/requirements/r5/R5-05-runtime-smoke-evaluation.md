# R5-05: Phaser Runtime Smoke Evaluation

> 状态：`TODO`
>
> 前置任务：`R5-03`、`R5-04`
>
> 推荐模型：`gpt-5.5`
>
> 任务类型：浏览器运行评测 / 跨端契约测试

## 背景

通过 Schema 和规则仍不能证明 GameConfig 真能被 Phaser Runtime 加载、渲染和开始运行。R5 的第三层评测要用受控浏览器 smoke test 证明当前 Runtime 的最低可玩性，而不是相信 JSON 或模型自述。

## 目标

实现：

```text
schema + rule PASSED
-> isolated GameConfig preview route/test fixture
-> headless browser mounts Phaser runtime
-> deterministic readiness assertions
-> EvaluationReport(evaluatorType=RUNTIME)
-> Artifact runtimeEligible flag / evidence
```

最低 smoke 标准包括：配置成功加载、画布非空、关键对象创建、无未捕获错误、游戏状态达到 ready/running；不要求自动通关复杂玩法。

## 范围

允许：

- 新增/整理受控 GameConfig preview/test route、runtime readiness hook、浏览器测试脚本与报告适配器。
- 使用 Playwright 或等价工具在固定 viewport 启动前端，向 Runtime 注入已验证 fixture/Artifact。
- 捕获页面错误、console error、Phaser readiness、canvas 像素/关键对象计数、加载超时并写入 Runtime EvaluationReport。
- 添加成功、Schema/规则阻止、Runtime 初始化异常、超时、空画布、移动/桌面视口测试。
- 仅暴露受控测试接口，不让浏览器执行任意模型生成脚本。

## 非目标

- 不实现完整游戏自动化通关、性能压测或视觉评分。
- 不让 Java 服务执行浏览器内模型代码或任意 URL。
- 不改变 GameConfig Schema、规则或 Phaser 玩法来“让测试通过”。
- 不接入真实 LLM、RAG 或评测 LLM。
- 不制作面向用户的复杂评测编辑界面。

## 约束

- Runtime test 只接受 R5-03/R5-04 已通过、受控来源的 Artifact/fixture。
- 浏览器等待需基于 readiness 条件并设置明确上限，禁止用无界 sleep。
- `canvas` 非空不是唯一成功条件；还需断言 Phaser 初始化和关键配置对象可用。
- 测试失败要保存可定位证据：artifactUuid/hash、schema/rule report 引用、浏览器错误、截图/trace 路径、viewport。
- Runtime 失败必须产生 FAILED/ERROR 报告并阻止“可试玩”成功标识，但不删除原 Artifact 证据。
- 运行脚本、截图和临时文件必须与生产静态资源隔离。

## 验收标准

- [ ] 通过 Schema/规则的有效 GameConfig 可在 headless 浏览器中初始化 Phaser 并生成 Runtime PASSED 报告。
- [ ] 初始化错误、超时、空/错误画布、console exception 会生成可追溯 FAILED/ERROR 证据。
- [ ] Schema/规则失败 Config 不会绕过前置条件进入 Runtime smoke。
- [ ] 运行报告包含 Artifact、schema/rule 报告、Runtime 版本、viewport、耗时和错误证据引用。
- [ ] 375px 与桌面 viewport 都能验证 Runtime 初始化且不影响常规 Vue build。
- [ ] 测试不调用真实模型或执行任意 AI 代码。

## 验证命令

```powershell
cd frontend-vue
npm run test:game-config
npm run test:runtime-smoke
npm run build

cd ..\backend-java
mvn -Dtest=*RuntimeEvaluation*Test,*EvaluationReport*Test test

cd ..
.\tools\verify.ps1 -Profile e2e
.\tools\verify.ps1 -Profile quick
```

## 审查清单

- 是否只断言页面没有崩溃、没有真实检查 Phaser readiness。
- 是否允许未通过 Schema/规则的 Config 进入 Runtime。
- 是否用无界 sleep 或依赖人工观察截图。
- 是否将截图/测试 endpoint 暴露到生产用户路径。
- 是否把模型文本当 JavaScript/HTML 执行。

## 完成定义

- “可试玩”拥有可重复的真实 Runtime smoke 证据。
- 三层评测中的 Runtime 层可区分地记录成功、失败和错误。
