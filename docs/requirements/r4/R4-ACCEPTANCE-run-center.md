# R4 验收: 前端运行中心与 SSE 订阅总验收

> 状态：`DONE`
>
> 前置任务：`R4-00`、`R4-01`、`R4-02`、`R4-03`、`R4-04`、`R4-05`、`R4-06`、`R4-07`、`R4-08`
>
> 推荐模型：`gpt-5.5`
>
> 任务类型：阶段验收 / 只验证与记录

## 背景

R4 的完成标志是前端不再借助一条长 SSE 请求承载任务执行：用户能够提交异步任务、立即进入运行详情、刷新恢复，且 SSE 只是对已持久化状态的可释放订阅。

## 目标

新增 `docs/reports/R4-run-center-report.md`，以可复现证据证明：

```text
R3 durable WorkflowRun / StepRun / Outbox
+ v1 query Read Model
+ persisted event sequence
+ read-only SSE subscription
+ API client + single workflow store + route
+ run detail, cancel/retry, artifacts
+ browser E2E + responsive checks
= R5 可在稳定运行中心上增加评测和模型指标
```

## 范围

允许：

- 运行 R4、R3、R2、R1、R0 的相关测试、quick/integration/e2e Harness 与浏览器检查。
- 审查前后端 API/事件契约、授权、状态合并、SSE 生命周期、响应式布局、差异和敏感信息。
- 新增 R4 验收报告、更新 R4 任务卡状态、记录已知风险和 R5 准入结论。
- 只修复阻断验收的最小问题并补充回归测试。

## 非目标

- 不新增 R5 模型指标、Prompt 对比、评测报告或成本 Dashboard。
- 不实现 R6 RAG、知识库、引用可视化。
- 不删除旧同步 API、旧 Demo SSE 或 Phaser 页面。
- 不重写 R3 MQ/Outbox/Consumer/恢复链路。
- 不做生产发布或无关视觉重构。

## 验收项目

### 后端事实来源

- v1 查询只读、权限隔离、可读取历史/终态/运行中 Run。
- Run/Step/Artifact 变化产生有序持久化事件。
- SSE 先发 snapshot，重连支持 sequence 回放，连接失败不影响任务。
- cancel/retry 通过状态机、Outbox 和审计实现，不绕过 R3。

### 前端状态与体验

- API Client 与单一 Store 承担请求、快照和事件合并。
- 提交后基于服务端 workflowRunUuid 跳转详情路由。
- 刷新、断线重连、重复/乱序事件最终回到服务端真实状态。
- 终态/失败/取消/重试/空 Artifact/无权限均有可理解 UI。

### 质量边界

- 离开路由和终态时 EventSource/timer 被释放。
- SSE 或页面关闭绝不影响后台 Consumer/Runner。
- 桌面和移动端无重叠、横向溢出或不可点击控件。
- GameConfig/Phaser 试玩入口不回归。

## 验收标准

- [x] 异步提交后立即进入对应 WorkflowRun 详情页，而不等待 LLM。
- [x] 关闭页面或 SSE 连接不影响后台任务执行。
- [x] 刷新详情页后可恢复当前持久化状态与步骤。
- [x] SSE 重复/乱序事件不重复追加步骤、不回退更高 sequence 状态。
- [x] 查询/订阅/取消/重试 API 均有权限校验和稳定错误语义。
- [x] 运行中、成功、失败、取消、重试、空 Artifact、无权限状态均有自动化覆盖。
- [x] 桌面与移动视口内容不重叠，quick/integration/e2e Harness 通过。
- [x] 生成 R4 报告，并给出明确 R5 准入结论。

## 验证命令

```powershell
git status --short
git diff --check
docker compose config

cd backend-java
mvn test

cd ..\frontend-vue
npm run test:unit
npm run test:e2e
npm run test:game-config
npm run build

cd ..
.\tools\verify.ps1 -Profile quick
.\tools\verify.ps1 -Profile integration
.\tools\verify.ps1 -Profile e2e

rg -n "SseEmitter|Last-Event-ID|sequence|WorkflowRunEvent|EventSource|workflowRunStore" backend-java\src\main\java frontend-vue\src
rg -n "161764|password:\s*123456|local-secret-key|change-this-to-a-strong-secret-key|\bsk-[A-Za-z0-9]{20,}" `
  backend-java frontend-vue python-agent .env.example docker-compose.yml
```

## R4 报告模板

```markdown
# R4 Run Center Report

## 环境
- 日期：
- 分支：
- 基线 commit：
- 浏览器/视口：

## 后端 API 与事件
- Query Read Model：
- Persisted event sequence：
- SSE subscription：
- Cancel/retry：

## 前端运行中心
- API/Store/route：
- Submit to detail：
- Refresh/reconnect/dedup：
- Artifact/Demo：

## Harness 结果
| 命令 | 结果 | 证据 |

## 响应式与可访问性
- Desktop：
- Mobile 375px：
- Keyboard/error states：

## 已知风险
- 风险：
- 归属阶段：R5 / R6 / R7

## R5 准入结论
- PASS / BLOCKED
- 原因：
```

## 审查清单

- 是否仍让长 SSE POST 承载新工作流执行。
- 是否让浏览器连接/Store 内存成为后台任务的事实来源。
- 是否遗漏 sequence 去重、snapshot 回补、Emitter/EventSource 清理。
- 是否允许未授权用户读取或订阅他人的运行详情。
- 是否将 cancel/retry 绕过 Outbox/状态机直接调用 Runner。
- 是否只检查页面可打开，遗漏刷新、断线、终态、移动端和 Artifact 行为。

## 完成定义

- R4 验收报告与任务状态已更新，运行中心拥有可重复的后端与浏览器证据。
- R5 可在持久化运行、稳定查询/订阅与可解释前端状态之上增加评测和模型指标。
