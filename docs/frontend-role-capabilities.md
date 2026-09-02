# 前端角色与后端 Capability

本项目以后端 `UserCapabilityService` 返回的 capability 列表作为权限事实来源。前端只负责据此组织导航、禁止误操作和显示 403；隐藏按钮不构成安全边界，所有受控 HTTP 端点仍由 Spring Method Security 校验。

## 角色矩阵

| 角色 | 继承 | 主要 capability | 前端区域 |
| --- | --- | --- | --- |
| `USER` | 无 | `projects.read/create`、`generation.read/compile/build`、`artifacts.read`、`prototype-versions.read` | 项目中心、Cocos 生成台（可编译并创建构建）、Artifact、原型只读 |
| `PROJECT_ADVANCED` | `USER` | 项目更新、生成/编译/构建/审批/发布、原型管理、Player Run、Episode、知识库、Workflow、Director、Playtest、Export | 项目高级工具 |
| `ADMIN` | `PROJECT_ADVANCED` | `admin.dashboard`、`admin.agent-runs`、`admin.diagnostics`、`prompt-ops.manage`、`prompt-analytics.read` | 运营、Agent Run、Prompt 运维、指标、诊断 |

普通用户导航不包含管理员、诊断、旧版、内部或危险写操作。直接输入越权 URL 会进入 403 页面；直接请求 API 仍由后端 capability 校验拒绝。角色字符串或前端状态不能提升后端权限。

## V4 / V5 兼容边界

- V4 `PrototypeVersion`、Player、Episode、Playtest 和 Export 契约保持不变。
- V5 `GenerationRun` 只有在 Runtime IR 明确声明 `playerBridge.contractVersion = prototype-version/1` 且提供 `gameConfigArtifactUuid` 时，才允许转换为 V4 `PrototypeVersion`。
- 当前 `arcade_collect/1` 的真实 V5 Runtime IR 未声明上述桥接信息，因此界面会显示结构化不兼容原因，不会伪装为已打通。
- Cocos Creator 固定为 3.8.8，构建目标固定为 `web-mobile`。

## 前端验收入口

- 普通用户：`/projects`、`/projects/:projectUuid/studio`、`/projects/:projectUuid/artifacts`
- 项目高级用户：上述页面及 `/versions`、`/player-runs`、`/knowledge`、`/workflow-runs`、`/director`
- 管理员：`/admin/dashboard`、`/admin/agent-runs`、`/admin/prompt-ops`、`/admin/analytics`、`/admin/diagnostics`

建议人工验收时分别使用三个真实账号，不要通过浏览器篡改 capability fixture 代替后端授权验证。
