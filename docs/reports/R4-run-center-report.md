# R4 Run Center Report

## 环境

- 日期：2026-07-12
- 分支：`codex/frontend-run-center`
- 验收基线：`7b0b4bb`
- 浏览器/视口：Playwright Chromium；1440×900、375×812。

## 后端 API 与事件

- Query Read Model：v1 查询、未知/无权资源的稳定响应与历史空字段由控制器、服务和测试覆盖。
- Persisted event sequence：`WorkflowRunEventService` 使用每个 Run 的数据库 sequence 分配，mapper 按 sequence 升序回放。
- SSE subscription：服务端先发送 snapshot，再回放持久化事件；`Last-Event-ID` 仅从请求头读取。订阅断开只释放订阅者。
- Cancel/retry：命令服务、状态策略、Outbox 与审计测试通过；前端仅根据服务端 `allowedActions` 发起命令。

## 前端运行中心

- API/Store/route：统一 HTTP/SSE 客户端和单一 `workflowRunStore` 管理快照、序号、连接和清理。
- Submit to detail：Playwright 证明提交后仅使用服务端 `workflowRunUuid` 进入详情页。
- Refresh/reconnect/dedup：Store 测试覆盖重复/乱序、sequence 缺口快照纠偏、重连受限退避、终态及 403/404 清理。
- Artifact/Demo：浏览器测试覆盖可用安全链接、空 Artifact 和不显示虚假入口。

## Harness 结果

| 命令 | 结果 | 证据 |
| --- | --- | --- |
| `npm run test:unit` | PASS | 18 项前端单元测试。 |
| `npm run test:e2e` | PASS | 3 项 Playwright 本地假后端场景：提交/终态、命令/异常状态、桌面与移动布局。 |
| `npm run test:game-config` | PASS | 9 项 GameConfig 测试。 |
| `npm run build` | PASS | Vite 生产构建成功；仅有既有 Phaser 大包告警。 |
| `./tools/verify.ps1 -Profile quick` | PASS | Java 96 项测试通过（1 项 Docker 依赖测试跳过）、Python 编译、Vue 构建、Compose 配置通过。 |
| `./tools/verify.ps1 -Profile integration` | PASS（环境跳过） | Harness 退出成功；本机未发现 Docker，3 项 Testcontainers 场景被跳过。 |
| `./tools/verify.ps1 -Profile e2e` | PASS | R4 浏览器 Harness 3/3 通过。 |
| 契约/敏感信息扫描 | PASS | 未发现持久化前端凭据、凭据 URL 参数或禁止的字面量密钥。 |

## 响应式与可访问性

- Desktop：1440px 场景断言状态、步骤、Artifact 与可用操作可见，且无横向溢出。
- Mobile 375px：断言无横向溢出、按钮在视口内且具有有效几何区域；测试保留运行截图作为失败工件。
- Keyboard/error states：可用操作为语义按钮；无权限/网络错误使用 `role="alert"`，浏览器场景覆盖无权限状态。

## 已知风险

- 本机 Docker 不可用，Testcontainers 的集成场景被跳过；CI 必须在具备 Docker 的执行器上运行 `verify.ps1 -Profile integration`，以获得真实容器链路证据。
- Vite 构建仍报告 Phaser 相关 bundle 超过 500 kB；这是性能优化项，不阻断 R4 状态正确性。

## R5 准入结论

- PASS（附 CI Docker 验证条件）。
- R4 已具备持久化运行事实源、受控查询/SSE、可恢复前端状态与浏览器回归证据；R5 可在此基础上增加评测和模型指标，不应改变 R4 的运行状态语义。
