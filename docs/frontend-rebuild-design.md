# 前端产品化重建设计方案

> 状态：`PROPOSED`
>
> 适用阶段：`F1-F4`
>
> 实施原则：后端契约优先、复用已验证能力、主链路优先、低额度迭代

## 1. 背景与问题

当前 Vue 前端已经接入工作流运行、SSE、知识库、产物和 Phaser 预览等工程能力，但页面更接近后端能力验证台，而不是普通用户可以稳定使用的产品界面。主要问题是：

- 用户需要手工填写项目 UUID、工作流类型等后端概念。
- 登录之外缺少完整的注册、项目创建和项目选择链路。
- 主页面同时暴露提交、调试、Legacy Demo 等入口，操作层级不清晰。
- 前端组件虽然较多，但没有围绕一条完整用户旅程组织。
- 数据库连接账号容易被误解为业务登录账号。

本次不推倒已验证的工作流能力，而是重建产品外壳，让项目可以稳定演示：

```text
注册/登录
-> 创建或选择项目
-> 输入游戏想法
-> 启动异步生成
-> 查看实时运行进度
-> 查看产物和游戏预览
```

## 2. 建设目标

### 2.1 产品目标

- 新用户可以独立完成注册和登录。
- 用户不需要理解或填写 UUID、工作流 Key、幂等键和 SSE 参数。
- 用户可以创建、查看和选择自己的游戏项目。
- 用户可以从项目工作台提交一次游戏生成任务。
- 用户可以查看任务状态、步骤、错误、取消、重试和最终产物。
- 页面刷新后可以根据服务端数据恢复，不依赖临时内存维持结果。
- Docker 启动后可以稳定完成主链路演示。

### 2.2 工程目标

- 后端接口契约是前端行为的唯一事实来源。
- 复用现有 API、Store、SSE、GameConfig 和 Phaser Runtime。
- 不引入不必要的框架和依赖，降低改造成本与回归风险。
- 为核心 API、状态逻辑和主链路保留自动化验证。
- 将真实踩坑沉淀到 `docs/PITFALLS.md`，不预先堆积假规则。

## 3. 非目标

- 不修改 Java 后端业务逻辑、数据库模型和接口语义。
- 不重新设计 Python Agent、Prompt、RAG 或工作流执行器。
- 不增加第二种游戏玩法，不重写 Phaser Runtime。
- 不建设复杂后台、权限管理、个人中心或运营大屏。
- 不追求复杂动画、主题系统、低代码编辑器或营销首页。
- 不引入 Vue Router、Pinia、UI 组件库或新的 CSS 框架。
- 不把 Prompt 指标、Debug 面板放在主链路中。

## 4. 信息架构

前端只保留四个核心界面。

### 4.1 登录与注册

- 登录和注册使用同一页面中的模式切换。
- 注册字段遵循后端校验：用户名 4-20 位，密码 6-32 位。
- 登录态只保留在当前会话认证层，不新增 localStorage Token。
- 401 统一清理认证状态并回到登录界面。
- 页面明确区分业务账号与 MySQL/Redis 等基础设施账号。

### 4.2 项目列表

- 展示当前用户已有项目。
- 支持创建项目，字段以 `GameController` 请求 DTO 为准。
- 支持选择项目进入工作台。
- 覆盖加载中、空列表、加载失败和创建失败状态。
- 项目 UUID 由页面内部保存和传递，不作为用户输入项。

### 4.3 项目工作台

- 顶部显示当前项目名称和必要的项目信息。
- 主操作只包含“游戏想法”、可选补充上下文和“开始生成”。
- 默认工作流为 `GAME_GENERATE`，不向普通用户展示。
- 每次明确提交生成新的幂等键；请求未完成时禁止重复提交。
- 提交成功后使用服务端返回的 `workflowRunUuid` 进入运行详情。
- 知识库作为项目内次级入口，不阻断游戏生成主流程。

### 4.4 运行详情

- 展示运行状态、步骤、attempt、时间和可读错误信息。
- 使用现有 Store 和 SSE 接收增量状态，断线时允许重连或重新加载快照。
- 根据服务端 capability 决定是否显示取消和重试。
- 成功后展示 Artifact、结果摘要和 Phaser 游戏预览。
- 刷新页面后可以通过运行 UUID 重新加载。
- 原始 JSON 和调试信息仅放入折叠的高级区域，默认隐藏。

## 5. 页面状态与导航

不引入 Vue Router，由 `App.vue` 和现有轻量路由适配器维护有限页面状态：

```text
unauthenticated -> auth
authenticated + no project selected -> projects
project selected -> workspace
workflow submitted -> run detail
workspace secondary action -> knowledge
```

应用导航保持简单：

- 项目
- 当前工作台
- 知识库
- 退出登录

移动端允许导航换行或折叠，但不能出现横向滚动、文字遮挡和按钮溢出。

## 6. 前端模块规划

### 6.1 复用

- `src/api/httpClient.js`
- `src/api/workflowApi.js`
- `src/api/knowledgeApi.js`
- `src/stores/workflowRunStore.js`
- `src/router/workflowRoute.js`
- `src/components/WorkflowStepper.vue`
- `src/components/ArtifactLibrary.vue`
- `src/components/PhaserGamePreview.vue`
- `src/game/` 下的 GameConfig 和 Runtime
- 已有单元测试、浏览器测试和 Playwright 基础设施

### 6.2 新增或重建

- 补全 `src/api/authApi.js` 的注册契约。
- 新增 `src/api/projectApi.js` 封装项目列表、创建、详情和更新。
- 重建 `App.vue`，只负责认证、应用框架和有限页面切换。
- 新增或重建项目列表视图。
- 重建 `WorkbenchView.vue`，移除 UUID 和工作流类型输入。
- 简化 `WorkflowRunView.vue`，突出进度、错误、操作和产物。
- 整理 `styles.css`，形成克制、稳定、响应式的工作台样式。

### 6.3 降级处理

- Prompt 指标和 Debug 面板不删除底层代码，但退出主导航。
- Legacy Demo 不作为主入口；兼容能力如需保留，只能位于高级区域。
- SSE 无法建立时，用户仍可通过重新加载快照看到服务端真实状态。

## 7. 后端契约边界

主要接口以实际 Controller 和 DTO 为准：

```text
POST /api/auth/register
POST /api/auth/login
GET  /api/auth/me

POST /api/projects
GET  /api/projects
GET  /api/projects/{projectUuid}
PUT  /api/projects/{projectUuid}

POST /api/v1/projects/{projectUuid}/workflow-runs
GET  /api/v1/workflow-runs/{workflowRunUuid}
GET  /api/v1/workflow-runs/{workflowRunUuid}/steps
GET  /api/v1/workflow-runs/{workflowRunUuid}/artifacts
GET  /api/v1/workflow-runs/{workflowRunUuid}/events
POST /api/v1/workflow-runs/{workflowRunUuid}/cancel
POST /api/v1/workflow-runs/{workflowRunUuid}/retry

GET  /api/projects/{projectUuid}/knowledge-documents
POST /api/projects/{projectUuid}/knowledge-documents
```

前端不得猜测数据库字段、状态迁移或 capability。发现契约缺失时，先记录证据并停止扩大修改范围。

## 8. 通用实现约束

- 不修改后端业务逻辑来迁就前端。
- 不使用 `git add .`，不提交任务范围外的现有 WIP。
- 不把 Token 写入 localStorage，不提交密钥和真实账号。
- 不把模型输出直接作为 HTML 注入页面。
- 不允许用户输入 UUID、工作流 Key和幂等键。
- 所有请求必须有 loading、empty、error 或 success 中的明确状态。
- 提交按钮在请求进行中禁用，避免并发重复提交。
- 动作成功后重新读取或合并服务端真相，不仅依赖乐观 UI。
- 不因样式调整破坏 375px 移动端和常见桌面视口。
- 不重构与当前任务无关的后端、测试 Harness 和生成能力。

## 9. AI 协作与低额度流程

每次只执行一张任务卡：

1. 读取任务卡指定的文件，不扫描全部仓库和历史证据目录。
2. 检查 `git status`，识别已有修改并保留。
3. 给出不超过 6 行计划后直接实现。
4. 先运行任务相关测试，再运行前端 build。
5. 审查 `git diff --check` 和任务范围 diff。
6. 只汇报改动、验证结果、风险和人工验收步骤。

模型使用原则：

- `gpt-5.4`：接口封装、表单、页面搬运、样式、重复测试。
- `gpt-5.5`：SSE/Store 状态整合、幂等并发风险、最终 Code Review。
- 不使用多 Agent；四张任务卡顺序执行，减少重复上下文读取。

## 10. Code Review 与修正

AI 审查重点：

- API 字段、HTTP 方法和返回结构是否符合后端契约。
- 401、网络失败、空数据和服务端错误是否有稳定表现。
- SSE 是否重复订阅、遗漏关闭或覆盖较新的服务端状态。
- 重复点击是否可能创建多个工作流。
- 取消、重试是否服从服务端 capability。
- Artifact 是否被不安全地当作 HTML 执行。
- diff 是否夹带任务外修改。

人工验收重点：

- 新账号能否注册、登录并退出。
- 能否创建和重新找到项目。
- 全程是否无需填写 UUID 和工作流类型。
- 能否从生成提交进入运行详情并查看最终结果。
- 刷新、断网、失败、取消和重试时页面是否可理解。
- 桌面和移动端是否存在遮挡、溢出或无法点击。

修正时只提供：问题、复现步骤、实际结果、期望结果和修改约束。要求 AI 做最小修复并补回归测试，不重新设计整个页面。

## 11. 阶段与完成标准

| 阶段 | 结果 |
| --- | --- |
| F1 | 注册、登录、项目列表和创建项目可用 |
| F2 | 项目工作台可以安全提交异步生成任务 |
| F3 | 运行详情、SSE、取消、重试、产物和预览可用 |
| F4 | 自动化测试、Docker 主链路和人工验收通过 |

对应任务卡：

- [F1：认证与项目生命周期](requirements/frontend-rebuild/F1-auth-project-lifecycle.md)
- [F2：项目工作台与生成提交](requirements/frontend-rebuild/F2-project-workbench-submit.md)
- [F3：运行详情、产物与游戏预览](requirements/frontend-rebuild/F3-run-detail-artifact-preview.md)
- [F4：前端集成与发布验收](requirements/frontend-rebuild/F4-integration-release-acceptance.md)

前端重建完成必须同时满足：

- F1-F4 的验收标准全部通过。
- `npm run test:unit` 与 `npm run build` 返回 0。
- Docker 环境可以完成注册到游戏预览的主链路。
- 主页面没有 UUID、工作流 Key、幂等键等技术输入。
- 没有新增高危安全问题和任务范围外回归。
- 真实踩坑已经按 `docs/PITFALLS.md` 的结构沉淀。
