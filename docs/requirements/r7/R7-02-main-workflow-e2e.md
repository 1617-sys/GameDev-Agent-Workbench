# R7-02: 从创意提交到 Phaser Demo 的主链路 E2E

> 状态：`TODO`
>
> 前置任务：`R7-01`
>
> 推荐模型：`gpt-5.5`
>
> 任务类型：系统级 E2E / 浏览器与后台证据

## 背景

各阶段已有单元和局部集成测试，但最终项目必须证明一条真实用户链路跨越认证、异步提交、Outbox/MQ、Runner、Python Agent、评测、RAG、SSE、Artifact 和 Phaser Runtime 后仍然成立。

## 目标

实现可重复的全链路 E2E：

```text
register/login
-> create/select project
-> upload/enable project knowledge (controlled fixture)
-> submit workflow with Idempotency-Key
-> observe 202 and run detail/SSE
-> wait terminal state
-> verify StepRun/AgentRun/Metric/Retrieval/Evaluation/Artifact
-> open Phaser Demo
-> assert runtime smoke readiness
```

## 范围

允许：

- 完善 `tools/verify.ps1 -Profile e2e`、Playwright/浏览器测试、测试用户/项目/知识/Prompt fixtures 和清理脚本。
- 使用 Docker Compose 全栈、受控 fake/mock Agent 或可选真实 Provider profile。
- 断言数据库/API/UI 的 workflowRunUuid、traceId、step/attempt、metric/mock、retrieval、evaluation 和 artifact 链接一致。
- 覆盖 RAG-on 和 RAG-off 的最小成功链、失败链、刷新恢复、SSE 去重和 Phaser Runtime smoke。
- 保存失败截图/trace/关键日志引用与测试报告。

## 非目标

- 不以真实付费 LLM 作为 CI/E2E 唯一前提。
- 不测所有组合、所有浏览器和所有游戏类型。
- 不用 UI 断言替代数据库/API 业务事实验证。
- 不在 E2E 中修复架构缺陷或添加新产品功能。
- 不把手工录屏当自动化 E2E。

## 约束

- 测试数据、Idempotency-Key、project/run UUID 唯一且可清理，不能依赖执行顺序或历史数据。
- 异步等待使用带上限的条件轮询/事件等待，不使用任意长 sleep。
- fake/mock 结果必须在 AgentRun/UI 中显式标记；可选真实 Provider 测试单独报告。
- 失败时输出 traceId、workflowRunUuid、Outbox/MQ/Step/Evaluation 状态和浏览器证据。
- E2E 不得绕过 Outbox、MQ、SSE 或 Runtime 直接插入最终成功数据。
- 测试结束清理用户级数据/连接，不默认删除开发者整个 volume。

## 验收标准

- [ ] 新环境中自动完成从登录/项目到异步 WorkflowRun 创建和终态。
- [ ] WorkflowRun、StepRun、AgentRun、Metric、RetrievalRecord、EvaluationReport、Artifact 可由同一 run/trace 关联。
- [ ] 页面刷新/SSE 重连后状态与持久化快照一致，不重复步骤。
- [ ] 合法 GameConfig 通过三层评测并在 Phaser Runtime 达到 ready；非法 Config 被门禁阻止。
- [ ] RAG-on/off 与 mock 标记符合 R5/R6 契约。
- [ ] E2E 可重复执行并保存报告，失败证据足以定位到具体层。

## 验证命令

```powershell
.\tools\verify.ps1 -Profile e2e

cd frontend-vue
npm run test:e2e
npm run test:runtime-smoke
npm run build
```

## 审查清单

- 是否绕过消息、评测或 Runtime 直接构造成功结果。
- 是否只检查页面显示，没有检查持久化链路。
- 是否依赖 sleep、个人数据或真实付费模型。
- 是否 mock 输出没有显式标记。
- 是否失败时没有 run/trace/截图/后台状态证据。

## 完成定义

- 项目最重要的用户价值链可以从空环境自动验证到可试玩 Demo。
- E2E 证据覆盖前端、Java、Python 和基础设施，而不是单点烟雾测试。
