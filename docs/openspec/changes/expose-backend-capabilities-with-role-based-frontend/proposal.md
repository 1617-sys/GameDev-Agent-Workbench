## Why

当前后端约有 71 个 HTTP 方法，而前端 API 层仅覆盖约 51 个，并且部分已封装能力没有页面入口。知识库、RAG 证据、Prompt 管理与指标、Dashboard、Artifact 总览、Player Run 启动等能力因此只能通过接口或数据库访问；同时，Cocos V5 的“从零生成”语义及其到 Prototype/Player 流程的衔接也没有在 UI 中完整表达。

本变更建立一套角色分层、可审计的前端能力面，使每个后端端点都有明确归属、权限、入口或保留理由，同时避免将调试、旧版和危险操作无条件暴露给普通用户。

## What Changes

- 建立后端端点覆盖矩阵，将每个端点分类为普通用户、项目高级用户、管理员/诊断、内部或废弃，并记录对应前端入口、权限、状态和测试。
- 补齐缺失的前端 API 适配层、路由、Store、页面以及空状态、错误状态、加载状态和移动端布局。
- 为普通用户提供项目 Artifact 总览、知识库管理、RAG 来源证据、Player Run 启动与观察、完整 Episode 详情等业务界面。
- 为项目高级用户提供明确的 GameSpec“从零生成”和“修改当前规格”模式，以及 Cocos GenerationRun 到 PrototypeVersion/Player 流程的可追踪衔接。
- 为管理员提供 Dashboard、Prompt 模板管理、PromptVersion 指标和受控诊断工作区；敏感字段、原始 Prompt、密钥和跨用户数据不得暴露。
- 将健康检查、非生产 Demo、旧版 Workflow 和直接 Agent 接口纳入诊断/兼容性视图或明确标记为内部/废弃，不允许静默遗漏。
- 增加前后端契约清单和 CI 覆盖检查，防止新增 Controller 方法后没有前端映射或内部接口声明。
- 保持现有 Cocos Creator 3.8.8、`arcade_collect/1`、V4 工作流和 V5 生成发布主链兼容。

## Capabilities

### New Capabilities

- `frontend-api-coverage`: 定义全部后端端点的前端覆盖矩阵、分类规则、契约校验和无遗漏门禁。
- `role-based-frontend-shell`: 定义普通用户、项目高级用户、管理员/诊断角色的导航、路由守卫和操作权限。
- `generation-authoring-and-player-bridge`: 定义 GameSpec 新建/修改模式以及 GenerationRun、PrototypeVersion、Player Run 和 Episode 之间的可追踪衔接。
- `knowledge-rag-experience`: 定义项目知识文档管理和工作流实际 RAG 来源证据展示。
- `prompt-operations-and-analytics`: 定义 Prompt 模板管理、PromptVersion 指标和安全的数据状态表达。
- `artifact-dashboard-and-diagnostics`: 定义 Artifact 总览、项目/Agent 汇总、运行诊断、健康状态和旧接口兼容性展示。

### Modified Capabilities

当前 OpenSpec 主规格目录为空，本变更不修改既有 OpenSpec capability。

## Impact

- 前端：`frontend-vue/src/app`、`features`、`shared/api`、Store、路由、组件及测试。
- 后端：原则上复用现有 Controller；仅允许为角色授权、分页、安全 DTO、能力声明或缺失契约补充最小接口，不复制业务逻辑。
- 安全：新增角色授权、项目归属校验、敏感字段过滤、危险操作确认、非生产能力隔离和审计记录要求。
- 测试：新增端点覆盖测试、API 契约测试、组件测试、角色/越权测试、Playwright E2E、375px/桌面布局测试及现有 V4/V5 回归。
- 文档：维护端点覆盖矩阵、角色权限矩阵、内部/废弃接口清单和迁移说明。
