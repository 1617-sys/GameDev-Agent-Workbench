# F5：工作流提交后端发布阻断修复

> 状态：`DONE`
>
> 前置任务：`F4`
>
> 任务类型：后端契约 / Docker 发布阻断

## 背景

F4 的隔离 Compose 主链路验收中，浏览器按既定契约提交 `GAME_GENERATE` 后，首个请求返回 `50302 Workflow submission is temporarily unavailable`。Redis 容器健康，问题发生在 `WorkflowSubmissionGateImpl` 的 Lua 固定窗口限流调用：Lua 参数需要可被 `tonumber` 解析的字符串，但当前 `RedisTemplate<String, Object>` 的 JSON value serializer 会改变参数表示。

同时，前端和 F2 已固定使用 `GAME_GENERATE`，而当前 Flyway migration 只提供 `DEMO_GAME_CONFIG` 的 active definition。即使限流恢复，干净数据库也不能把该前端 workflowKey 解析为可执行定义。

## 目标

- 在 Redis 健康的干净 Compose 环境中，首个合规工作流提交不返回 `50302`。
- 保持固定窗口限流的窗口、上限和拒绝语义；只修复 Lua key/argument 的序列化边界。
- 通过新的、向前兼容的 Flyway migration 提供 active `GAME_GENERATE` definition，且步骤计划可被现有解析器执行。
- 隔离 Compose 浏览器主链路从注册到 Phaser 预览通过。

## 范围

- `WorkflowSubmissionGateImpl`、Redis script 序列化配置及对应后端测试。
- 新增 workflow definition migration 和迁移/提交契约测试。
- 更新隔离 E2E fixture 的必要断言。

## 非目标

- 不让前端回退到 `DEMO_GAME_CONFIG`，不重新暴露 workflowKey 输入。
- 不关闭 Redis 限流、绕过积压门禁或把 `50302` 当作客户端重试成功。
- 不修改认证、项目生命周期、RAG 或 Phaser 前端功能。

## 验收标准

- [x] Redis 可用时，首个 `GAME_GENERATE` 提交返回 HTTP 202 和服务端 workflowRunUuid。
- [x] Redis 不可用时仍返回既有的 `50302` 降级错误；超过窗口上限时仍返回既有的限流错误。
- [x] 空数据库执行 Flyway 后存在 active `GAME_GENERATE` definition，且其步骤定义可被 `WorkflowStepPlanParser` 解析。
- [x] `tools\verify.ps1 -Profile e2e` 在隔离 Compose 环境通过，夹具账号和数据被清理。
- [x] 不提交临时 `.env`、测试账号、截图或 evidence。

## 验证命令

```powershell
cd backend-java
mvn test

cd ..
.\tools\verify.ps1 -Profile e2e
```
