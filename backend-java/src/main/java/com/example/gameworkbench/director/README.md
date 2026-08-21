# Director 模块

## 职责

Director 根据持久化事实选择下一项受控操作。它不是拥有系统权限的自由 Agent：模型只选择动作，Java 负责授权、执行和状态迁移。

```text
目标 + 预算 + 事实
  -> DirectorDecisionClient 选择一个 tool/control decision
  -> Java 校验 round、schema、allowlist 和预算
  -> DirectorToolRegistry 做资源授权和限时执行
  -> 持久化 Decision / ToolCall / Event / Checkpoint
  -> 继续下一轮、等待实验、请求人工审批或结束
```

## 设计约束

- 每轮只接受一个 tool call，禁止并行工具调用。
- 模型不能直接执行工具；Spring AI 的内部自动工具执行被关闭。
- 工具参数必须满足闭合 JSON Schema，未知字段必须拒绝。
- 工具调用必须通过项目资源授权，并受超时和幂等约束。
- `stateVersion + claim token` 决定当前 Worker 是否拥有状态迁移权。
- 轮次、工具次数、候选数、episode、token、成本、时间和失败数均可受预算限制。
- 人工审批是显式状态，不允许模型伪造审批结果。

## 当前限制

- `InMemoryDirectorToolResultStore` 和 Registry 幂等缓存会在进程重启后丢失。
- 数据库保存调用摘要和 digest，但当前不能恢复内存 resultRef 对应的完整正文。
- Python LangGraph 实现主要是确定性回退，仓库证据明确标记为 mock，不能用于证明真实模型收益。
