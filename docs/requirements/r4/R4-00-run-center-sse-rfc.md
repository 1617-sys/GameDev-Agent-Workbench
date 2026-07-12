# R4-00: 运行中心与 SSE 订阅契约冻结

> 状态：`TODO`
>
> 前置任务：`R3-验收`
>
> 推荐模型：`gpt-5.5`
>
> 任务类型：前后端契约 / 只写文档

## 背景

R3 已把工作流执行变成独立于 HTTP 请求的可靠异步任务。现有 Vue `App.vue` 仍直接发起旧 Demo SSE 请求、在组件内维护请求/事件/展示状态；页面关闭或刷新后难以恢复真实运行状态。

R4 要把 UI 改造成围绕持久化 `WorkflowRun` 的运行中心：

```text
submit -> 202 + workflowRunUuid -> route to run detail
GET snapshot -> frontend store
SSE subscription -> incremental store update
disconnect/reload -> GET snapshot remains authoritative
```

## 目标

新增 `docs/requirements/r4/R4-run-center-sse-design.md`，冻结以下契约：

- `WorkflowRun`、StepRun、Artifact 的前端读取模型与字段脱敏规则。
- 查询、取消、重试、事件订阅 API 的 URL、鉴权、响应、错误语义和兼容策略。
- 持久化事件的事件名、`workflowRunUuid`、单调 `sequence`、时间、payload 与 `Last-Event-ID`/重连策略。
- “GET 快照优先、SSE 只做增量”的 Store 合并规则、事件去重/乱序规则和终态断连规则。
- 路由/页面结构、移动端与桌面端信息优先级、Legacy Demo 入口迁移策略。
- R4 不实现的评测详情、模型成本、RAG 引用和 Dashboard 聚合边界。

## 范围

允许：

- 阅读 R3 异步提交/Consumer/恢复状态、现有 Controller、Vue App.vue 与 GameDemoPage。
- 新增设计文档、API 表格、时序图、Store 状态图、事件样例和迁移顺序。
- 明确后续 R4 子任务的允许目录、测试策略、可访问性和响应式约束。

## 非目标

- 不修改 Java、Vue、Python 的业务代码。
- 不重写 R3 Outbox、Consumer、重试、DLQ、限流或恢复扫描。
- 不实现 R5 的 EvaluationReport、模型 Token/成本比较或 Prompt 实验页面。
- 不实现 R6 RAG 检索展示。
- 不删除旧同步 API 或旧 Demo SSE 入口。

## 约束

- MySQL 持久化状态是任务生命周期的唯一事实来源；浏览器内存和 SSE 连接均不是。
- 每个工作流事件必须有按 `workflowRunUuid` 单调递增的 sequence；前端只接受比当前 sequence 新的事件。
- SSE 首次连接和重连都先发送/拉取快照，增量事件不能修复未知的缺失状态。
- SSE 发送失败、客户端关闭、页面卸载不得写 WorkflowRun 状态或影响 Consumer/R2 Runner。
- UI 不得复制后端状态机规则，只展示后端给出的状态与允许动作。
- API/事件中不能暴露 Secret、完整私密 Prompt、Authorization、内部堆栈或未脱敏模型输入。

## 验收标准

- [ ] 文档定义可实现的查询、订阅、取消、重试 API 与兼容边界。
- [ ] 事件模型包含 snapshot、sequence、事件类型、错误和 Artifact 更新的语义。
- [ ] 文档明确刷新、断线、重复事件、乱序事件、终态和无权限场景。
- [ ] 明确 Store 是唯一前端状态源，组件不直接维护多份 WorkflowRun 状态。
- [ ] 明确 R4 与 R3、R5、R6 的职责边界和回退方案。
- [ ] 移动端/桌面端的关键信息层级与无障碍基础要求已说明。

## 验证命令

```powershell
git diff --check
rg -n "sequence|snapshot|Last-Event-ID|workflowRunUuid|Store|SSE|retry|cancel" docs\requirements\r4\R4-run-center-sse-design.md
```

## 审查清单

- 是否将任务运行与浏览器连接存在与否绑定。
- 是否让 SSE 事件没有 sequence 或让前端按到达顺序盲目覆盖状态。
- 是否让前端重新实现状态机或直接调用 Runner。
- 是否把 R5/R6 的指标、评测、RAG 内容提前塞入运行页。
- 是否遗漏历史快照、无权限和终态 Run 的读取语义。

## 完成定义

- R4 的 API、事件和 Store 合并语义已经冻结。
- 后续后端和前端任务可以独立实现而不产生接口漂移。
