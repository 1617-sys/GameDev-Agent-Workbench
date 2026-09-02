## Context

参见 `proposal.md`。当前系统同时保留 V4 工作流/原型/Player 能力和 V5 Cocos 生成发布能力；后端 Spring MVC Controller 数量多于前端 API 映射，已有 springdoc OpenAPI 支持，但前端没有统一的端点分类、角色能力模型或覆盖门禁。认证上下文目前以用户身份为主，因此管理员能力必须由后端显式提供，不能只靠前端隐藏入口。

## Goals / Non-Goals

**Goals:**

- 让所有后端端点进入可审计覆盖矩阵，并为可交互端点提供角色匹配的 UI。
- 保持前端 API 适配器统一处理认证、错误、下载、SSE、超时和幂等。
- 分阶段补齐业务页面，同时让 V4/V5 边界和不兼容状态对用户可见。
- 用自动化契约与权限测试防止后续再次产生后端独有能力。

**Non-Goals:**

- 不把所有端点直接展示给普通用户。
- 不让浏览器直接访问数据库、消息队列、向量库或 Cocos CLI。
- 不借此重写后端领域服务、合并 V4/V5 数据模型或扩大当前 `arcade_collect/1` 玩法能力。
- 不在前端重新计算后端权威的指标、编译结论、RAG 选择或发布状态。

## Decisions

### 1. 以 OpenAPI 加覆盖元数据作为接口事实源

使用现有 springdoc OpenAPI 生成规范化端点清单，并维护一份版本化覆盖元数据，补充 OpenAPI 无法表达的 `audience`、`lifecycle`、`frontendFeature`、`dangerLevel` 和 `owner`。CI 比较两者，任何未分类端点均失败。

选择该方案而不是手写 71 项静态列表，是因为 Controller 漂移可以被自动发现；选择覆盖元数据而不是仅依赖 OpenAPI tag，是因为内部/废弃状态与页面入口属于产品契约。

### 2. 权限由后端能力声明驱动

扩展认证响应或新增只读能力端点，返回当前用户可用的稳定 capability key。前端路由和导航消费 capability key，后端 Controller/Service 继续执行最终授权。角色名称只用于界面分组，不作为安全边界。

该方案优于在 Vue 中硬编码 `isAdmin`，因为前端无法成为权限事实来源，也便于后续增加只读运维角色。

### 3. 按领域建立前端 feature，而不是建立“万能后台页”

新增独立领域模块：`artifacts`、`knowledge`、`rag-evidence`、`prompt-ops`、`analytics`、`agent-runs`、`diagnostics`；共享的分页、状态、确认和安全文本组件放入 `shared`。每个 feature 拥有 API 适配器、Store、页面和测试。

万能接口浏览器虽然开发更快，但会暴露技术细节、削弱任务导向并增加误操作风险，因此不采用。

### 4. 先修复创作语义，再建立 V5 到 Player 的显式桥

GameSpec 页面新增互斥模式：从零生成时发送 `currentSpec: null`；修改模式必须发送合法对象。GenerationRun 到 PrototypeVersion 的转换由后端提供幂等、来源可追踪的桥接操作，前端不自行拼装 V4 配置。

若 V5 产物尚不满足 Player 契约，桥接端点返回结构化不兼容原因，页面禁用启动 Player，而不是伪装成成功转换。

### 5. 分阶段迁移但保持每阶段可发布

阶段一建立覆盖矩阵、权限能力、路由骨架和创作模式；阶段二补 Artifact、Player 和桥接；阶段三补知识/RAG；阶段四补 Prompt/指标/Dashboard/诊断。每阶段必须通过当前 V4/V5 回归，未完成入口显示为不可用能力而不是空白页面。

## Risks / Trade-offs

- [现有认证模型可能没有管理员角色] → 先建立最小后端 capability 声明和拒绝测试，再开放管理员路由。
- [“完全暴露”导致危险操作增多] → 使用角色、danger level、二次确认、幂等和审计，不为内部端点自动生成执行按钮。
- [V4/V5 数据契约无法直接桥接] → 以结构化兼容性检查作为前置门禁，允许只展示来源而不允许启动 Player。
- [OpenAPI 与运行时 Profile 不一致] → 覆盖元数据记录 profile 条件，分别验证 prod 与非 prod API 表面。
- [一次性新增页面过多导致回归] → 按领域分批交付，每批独立路由、API 契约和 E2E，并持续运行完整单元测试与生产构建。
- [指标或 RAG 页面泄露敏感信息] → 使用安全 DTO、字段白名单、转义渲染和跨项目越权测试。

## Migration Plan

1. 冻结当前 prod/non-prod OpenAPI 快照并完成端点分类，不改变现有路由行为。
2. 增加后端 capability 声明和覆盖 CI，先将现有未映射端点登记为 planned/internal/deprecated，避免立即阻塞主分支。
3. 上线新的分层导航和创作模式；保留旧页面 URL 与现有 API 适配器。
4. 按 Artifact/Player、Knowledge/RAG、Prompt/Analytics、Dashboard/Diagnostics 顺序开放页面。
5. 所有 planned 端点完成 UI 或被正式重新分类后，将 CI 门禁切换为严格模式。
6. 回滚时可逐领域关闭导航 capability；后端原接口和现有 V4/V5 页面保持可用。
