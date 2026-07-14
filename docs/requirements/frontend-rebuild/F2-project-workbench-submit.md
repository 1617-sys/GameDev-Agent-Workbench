# F2：项目工作台与生成提交

> 状态：`TODO`
>
> 前置任务：`F1`
>
> 推荐模型：`gpt-5.4` 实现，复杂幂等问题使用 `gpt-5.5` 审查
>
> 任务类型：主流程页面 / 异步提交接入

## 背景

项目选择完成后，用户需要一个面向业务的生成入口。当前工作台暴露项目 UUID、工作流类型和 Legacy Demo 等实现细节，本任务将其简化为“输入游戏想法并开始生成”。

## 目标

```text
选择项目
-> 输入游戏想法与可选上下文
-> 提交 GAME_GENERATE
-> 获得服务端 workflowRunUuid
-> 进入运行详情
```

## 必读上下文

- `docs/frontend-rebuild-design.md`
- `docs/requirements/frontend-rebuild/F1-auth-project-lifecycle.md`
- 后端 `AsyncWorkflowController`、提交请求 DTO 和返回 VO
- `frontend-vue/src/api/workflowApi.js`
- `frontend-vue/src/views/WorkbenchView.vue`
- `frontend-vue/src/router/workflowRoute.js`
- `frontend-vue/src/App.vue`

## 范围

- 重建项目工作台，展示当前项目的必要信息。
- 只向用户提供游戏想法、可选补充上下文和一个主要提交按钮。
- 内部使用 `GAME_GENERATE`，不向用户暴露可编辑工作流 Key。
- 按现有后端契约生成和发送幂等键。
- 请求进行中禁止重复点击；失败后允许用户修改输入并重试。
- 提交成功后仅使用服务端返回的 `workflowRunUuid` 进入详情状态。
- 为提交参数、幂等键复用和成功跳转补充必要测试。

## 非目标

- 不实现运行详情、SSE、取消、重试和 Artifact 展示。
- 不修改工作流定义、执行器、RabbitMQ、Redis 或 Python Agent。
- 不实现 Prompt 编辑器、节点编排器、模板市场或多种工作流选择器。
- 不重写知识库，只保留进入现有项目知识库的次级入口。
- 不使用旧 Demo SSE 接口作为主生成入口。

## 约束

- 用户不能输入项目 UUID、工作流 Key 和幂等键。
- 相同一次 pending 提交必须复用幂等键；一次明确的新提交才生成新键。
- 不在客户端猜测任务已进入 RUNNING 或 SUCCESS。
- 提交失败不能丢失用户尚未成功发送的文本。
- 主操作保持单一，知识库和高级功能不得抢占视觉层级。
- 不修改后端以适配未经确认的前端字段。

## 验收标准

- [ ] 从项目列表进入工作台后可以看到正确的项目名称。
- [ ] 页面不再显示项目 UUID 输入框、工作流类型输入框和 Legacy Demo 主入口。
- [ ] 游戏想法为空时不会发送请求，并显示可理解的校验信息。
- [ ] 连续点击不会产生多次并发提交。
- [ ] 提交参数与后端 DTO 契约一致。
- [ ] 成功后使用服务端返回的运行 UUID 进入运行详情。
- [ ] 失败时输入内容保留，用户可以再次提交。

## 验证命令

```powershell
cd frontend-vue
npm run test:unit
npm run build

cd ..
git diff --check
git diff -- frontend-vue
```

## 审查清单

- 是否每次点击都生成新幂等键并造成重复任务。
- 是否在服务端确认前自行生成运行 UUID 或猜测运行状态。
- 是否仍暴露面向后端开发者的字段。
- 是否因提交失败清空了用户输入。
- 是否重新接回旧同步或 Demo SSE 主链路。

## 完成定义

- 所有验收标准通过。
- 单元测试与 build 返回 0。
- diff 只涉及工作台、提交适配、必要页面状态和对应测试。
- 人工可以完成“选择项目 -> 输入想法 -> 提交 -> 进入运行详情”。
