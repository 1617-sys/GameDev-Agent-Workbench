# 全新 Vue 前端重建设计

> 状态：`DONE`
>
> 开发分支：`codex/frontend-rebuild`
>
> 目标：不新增后端功能，以最短路径交付可稳定演示的全新产品前端

## 1. 决策

本次不继续修改旧 `frontend-vue` 页面。先在独立目录从零搭建新工程，验收后整体替换 `frontend-vue`，不继承旧的 App、页面、组件、样式与页面测试。

允许迁移的只有非产品 UI 基础：

- 后端 HTTP/SSE 契约。
- GameConfig 校验规则。
- Phaser Runtime。
- Docker 端口和健康检查约定。

新工程已通过验收并替换 `frontend-vue`；Compose、Harness 与开发命令继续使用统一目录名，仓库不再维护两套前端。

## 2. 当前问题

- 页面按后端字段组织，用户看到 `game_concept`、`AVAILABLE` 等内部术语。
- 生成步骤占据首屏，真正的游戏结果被推到页面底部。
- 大卡片、大字号和大留白导致信息密度低、滚动距离长。
- 成功、运行、等待和失败缺少清晰的颜色、图标和动作区分。
- Artifact 作为数据库记录展示，没有转换为游戏概念、核心玩法、开发计划和可玩 Demo。
- 刷新、取消、重试与当前状态关系不清晰。
- 后端的 SSE、重试、序列和版本能力没有被放在合适的技术详情区域。

## 3. 产品主链路

```text
注册/登录
-> 项目中心
-> 创建或选择项目
-> 填写游戏创意
-> 提交 GAME_GENERATE
-> 查看实时生成进度
-> 查看游戏概念、核心玩法、开发计划
-> 打开 Phaser 可玩 Demo
```

项目 UUID、Workflow Key、幂等键和 Artifact UUID 不作为用户输入。

## 4. 页面设计

### 4.1 认证页

- 左侧显示产品名称和简洁的工作流视觉背景，右侧为登录/注册表单。
- 使用业务账号，Token 仅存入 `sessionStorage`。
- 注册、登录、恢复会话和 401 退出均有明确状态。

### 4.2 项目中心

- 固定侧栏展示品牌、项目列表、创建项目和当前用户。
- 主区域显示当前项目摘要、最近操作和进入创作台的主按钮。
- 创建项目使用对话框，不使用占满页面的长表单。
- 只支持当前后端已有的 `top_down_collect` 和 Web 平台，不增加新功能。

### 4.3 游戏创作台

- 顶部显示项目面包屑和项目名称。
- 主区域提供游戏想法、可选上下文和一个“开始生成”操作。
- 提供少量示例文案帮助演示，但不增加模板市场。
- 提交期间按钮禁用，同一次 pending 请求复用幂等键。

### 4.4 运行详情

- 顶部紧凑展示中文状态、进度、耗时、刷新、取消或重试。
- 四个步骤使用横向 Stepper：游戏概念、核心玩法、开发计划、游戏配置。
- 默认打开“成果”标签，展示 Artifact 内容和 Phaser Demo。
- “生成过程”显示步骤状态；“技术详情”折叠展示 UUID、attempt、statusVersion、lastSequence 和 SSE 状态。
- 失败时在顶部显示真实可读错误，并将重试放在错误旁边。

## 5. 视觉系统

- 工作型 SaaS 风格，浅灰页面、白色内容区、深色文字。
- 主色使用蓝色，成功使用绿色，失败使用红色，警告使用琥珀色。
- 页面标题 24-30px，正文 14-16px，卡片圆角不超过 8px。
- 页面区块不互相套卡片；步骤和产物使用紧凑列表或标签页。
- 图标统一使用 Lucide，不手绘 SVG。
- 结果和游戏预览优先占据可视区域，技术字段默认隐藏。
- 桌面优先，同时保证 375px 视口不重叠、不横向溢出。

## 6. 技术结构

```text
frontend-vue/
├─ src/app                 应用框架与路由
├─ src/features/auth       注册、登录和会话
├─ src/features/projects   项目列表和创建
├─ src/features/studio     创意提交
├─ src/features/runs       SSE、步骤、命令和结果
├─ src/features/demo       Phaser 预览
├─ src/shared/api          HTTP 与接口适配
├─ src/shared/ui           通用展示组件
├─ src/shared/presentation 状态和字段中文映射
└─ src/styles              Token 与全局样式
```

依赖限定为 Vue 3、Vite、Vue Router、Pinia、Lucide Vue 和现有 Phaser 3。不引入完整 UI 框架。

## 7. 安全与状态约束

- Token 不进入 localStorage、URL、日志或错误消息。
- 401 统一清空用户、项目和运行状态并跳转登录。
- 模型 Artifact 不使用 `v-html`，只按文本或已验证 GameConfig 渲染。
- SSE 断开后使用服务端快照恢复，页面卸载时关闭连接。
- 重复和乱序事件不能让状态倒退或重复步骤。
- 取消、重试只根据服务端 `allowedActions` 启用。
- DeepSeek Key 只保留在本机 `.env`，不进入前端构建和 Git。

## 8. 任务与交付顺序

1. [FR-01：全新工程、应用框架与认证](requirements/frontend-rebuild/FR-01-greenfield-shell-auth.md)
2. [FR-02：项目中心与游戏创作台](requirements/frontend-rebuild/FR-02-project-studio.md)
3. [FR-03：运行详情、Artifact 与 Phaser](requirements/frontend-rebuild/FR-03-run-results-demo.md)
4. [FR-04：Docker 切换与演示验收](requirements/frontend-rebuild/FR-04-cutover-acceptance.md)
5. [FR-05：项目运行历史与恢复入口](requirements/frontend-rebuild/FR-05-project-run-history.md)
6. [FR-06：Agent 驱动的 Phaser 场景升级](requirements/frontend-rebuild/FR-06-agent-driven-phaser-scene.md)

## 9. 完成定义

- 新工程没有导入旧 App、旧 View、旧 UI Component 和旧 CSS。
- 用户可完成注册到 Phaser Demo 的真实 DeepSeek 主链路。
- 运行页默认突出成果，而不是数据库字段。
- 单元测试、构建、Docker 健康检查和浏览器 E2E 通过。
- 桌面与 375px 视口完成视觉检查。
- Compose 已切换到新前端，旧前端删除或明确退出发布路径。
