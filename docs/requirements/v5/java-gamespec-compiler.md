# Java GameSpec Control Plane 与语义编译器

> 状态：DRAFT / 待实现
> 目标：由 Java 掌握生成状态、Agent 工具、GameSpec 语义、Cocos 构建编排和发布门禁

## 1. 编译管线

```text
raw GameSpec
→ parse
→ schema validate
→ normalize
→ symbol/reference resolve
→ semantic validate
→ capability check
→ typed IR
→ Cocos runtime IR / build request
→ isolated Cocos Build Worker
→ artifact assemble
→ smoke/playability gates
→ immutable artifact digest
```

每个阶段只接收上一阶段的类型化输出。任何 `ERROR` 立即停止，不生成“尽量可用”的产物；`WARNING` 是否阻断由发布策略决定并持久化。

## 2. Java 模块职责

建议边界而非最终包名：

- `gamespec-model`：版本化 AST、value object、source location；
- `gamespec-parser`：JSON 到 AST，不执行默认语义；
- `gamespec-validation`：schema、引用、规则图、数值和可达性；
- `gamespec-capability`：archetype、组件、事件、动作、asset pack 注册表；
- `gamespec-ir`：规范化、与 Runtime 无关的类型化中间表示；
- `gamespec-target-cocos`：IR、表现 profile 和 Cocos build request；
- `legacy-gameconfig-projection`：为 V4 Simulation 生成兼容评测投影，不承担渲染；
- `artifact-build`：Build Worker 编排、文件清单、hash、开发包与 gate；
- `generation-application`：GenerationRun 状态机、幂等命令、事务、审计和 Agent Tool Gateway。

## 3. 编译请求与结果

编译请求绑定：项目、GameSpec 内容 digest、Java编译器版本、capability registry、精确 Cocos Creator patch 版本、Runtime Shell digest、local web build profile、全部表现 profile 与 asset pack digest，以及 idempotency key。

结果只允许两种：

- `SUCCEEDED`：canonical spec、IR digest、target outputs、artifact candidate 和 warnings；
- `FAILED`：有序 diagnostics，不返回可运行 candidate。

同一冻结输入重复执行 Java 语义编译，必须得到相同的 canonical spec、IR digest、诊断排序和 Build Request。Cocos package 是否能做到字节级可复现，必须经过两次 clean build 实测后再承诺；首版只强制记录每次精确 package digest。时间戳、耗时与 trace id 属于观测元数据，不进入 source digest。

## 4. 结构化诊断

```json
{
  "code": "GS1401_UNSUPPORTED_CAPABILITY",
  "severity": "ERROR",
  "path": "/entities/2/components/weapon",
  "message": "arcade_collect/1 does not support weapon",
  "allowedValues": ["patrol", "contact_damage"],
  "retryableBySpecChange": true
}
```

Agent 读取诊断后只能调用 `revise_game_spec` 或终止。`ignore_error`、`execute_code`、`install_dependency` 不进入工具白名单。

## 5. 幻觉防线

| 幻觉类型 | 处理方式 |
| --- | --- |
| 编造字段/组件 | 封闭 schema 与 capability registry 拒绝 |
| 引用不存在实体 | symbol table 解析失败 |
| 参数看似合理但越界 | value object 与领域规则拒绝 |
| 组合后无法获胜 | 规则图/最低可达性检查 + headless gate |
| 编造资源 URL | 本地 asset id 和 manifest 白名单拒绝 |
| 修复时反复震荡 | max attempts、diagnostic fingerprint、无进展检测 |
| 伪造测试通过 | gate 由 Java 调度并读取机器结果，Agent 无写权限 |

## 6. Agent 工具面

首版只暴露高层工具：

- `get_gamespec_capabilities(archetype, version)`
- `compile_game_spec(projectId, spec, idempotencyKey)`
- `get_compile_diagnostics(compileRunId)`
- `build_cocos_preview(compileRunId)`
- `package_local_game(buildRunId)`
- `run_playability_gate(artifactId, suite)`
- `create_prototype_draft(artifactId, evidenceIds)`

工具由 Java 校验身份、项目归属、状态、预算和参数 schema。Python 不直接访问数据库或产物目录。

## 7. 首版验收

- 合法 fixture 的 canonical spec、IR 和 Build Request 编译两次字节级一致；
- 每类核心错误有固定 diagnostic fixture；
- 不支持能力不会被静默降级；
- Agent 修复前后 compile run 均可查询；
- simulation projection 通过现有 GameConfig contract，Cocos IR 通过 target contract；
- Cocos 构建只能由隔离 Worker 执行，Python Agent 无进程权限；
- artifact digest 与审批绑定；
- 测试覆盖 parser、domain rules、target golden files、幂等和越权。
