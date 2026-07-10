# R0-05：安全配置与基础配置清理

> 状态：`DONE`
>
> 前置任务：`R0-01`
>
> 推荐模型：实现使用 `gpt-5.4`，最终安全审查使用 `gpt-5.5`
>
> 任务类型：配置安全 + 构建卫生

## 背景

当前基线存在以下配置风险：

- `application.yml` 为数据库密码提供了真实风格的默认值。
- `application.yml` 为 JWT Secret 提供了可直接启动的默认值。
- `application-example.yml` 包含固定数据库密码和弱 Secret 示例。
- Vue 登录表单默认填入密码 `123456`。
- Spring Security 启用了 HTTP Basic，测试启动时生成临时密码。
- `pom.xml` 重复声明 Redis Starter。
- 缺少专门测试配置时，移除默认 Secret 可能导致测试无法启动。

本任务清理这些问题，同时保持本地开发和自动测试可执行。

## 目标

实现：

```text
生产式配置无默认敏感值
-> 本地通过 .env/example 明确配置
-> 测试通过 test profile 使用纯测试值
-> 日志不输出 Secret
-> JWT 项目不再额外启用 HTTP Basic
```

## 代码入口

- `backend-java/src/main/resources/application.yml`
- `backend-java/src/main/resources/application-example.yml`
- `backend-java/src/test/resources/`
- `backend-java/src/main/java/com/example/gameworkbench/config/SecurityConfig.java`
- `backend-java/src/main/java/com/example/gameworkbench/service/JwtService.java`
- `backend-java/pom.xml`
- `.env.example`
- `docker-compose.yml`
- `frontend-vue/src/App.vue`
- `python-agent/.env.example`

## 范围

允许：

- 移除数据库密码和 JWT Secret 的不安全默认值。
- 更新 example 配置为明确占位符。
- 新增隔离的测试配置或测试属性。
- 移除前端硬编码默认密码。
- 关闭项目不需要的 HTTP Basic。
- 删除 `pom.xml` 中完全重复的 Redis Starter 声明。
- 增加配置启动和安全路由测试。
- 检查日志与 example 文件中的敏感值。
- 更新必要的启动说明。

## 非目标

- 不实现完整 RBAC。
- 不增加 OAuth2、SSO 或第三方登录。
- 不更换 JWT 库。
- 不实现 Secret Manager。
- 不修改用户表或密码哈希算法。
- 不扩大 CORS 到任意来源。
- 不提交真实 `.env`。
- 不修改业务 Workflow。

## 约束

- 仓库中不得新增真实 API Key、数据库密码或 JWT Secret。
- `.env.example` 只能包含明显占位符。
- 测试 Secret 只能存在于测试作用域，并明确标记非生产。
- 缺少必要生产配置时应明确失败，不得静默使用弱默认值。
- 不能通过打印配置值来验证环境变量。
- 保留现有 JWT API 行为和公开路由。
- 删除重复依赖不得改变依赖版本。

## 必测场景

- 测试 profile 可以启动 Spring Context。
- `/api/health`、登录、注册等既有公开路由规则保持。
- 受保护路由无 JWT 时仍被拒绝。
- HTTP Basic 不成为旁路认证方式。
- JWT Secret 缺失时生产式配置不能使用仓库内弱默认值。
- 前端源码不包含默认登录密码。
- `pom.xml` 中 Redis Starter 只声明一次。

## 验收标准

- [ ] `application.yml` 不含可直接使用的数据库密码默认值。
- [ ] `application.yml` 不含可直接使用的 JWT Secret 默认值。
- [ ] example 文件只含明显占位符。
- [ ] 前端登录密码默认值为空。
- [ ] 项目不再无意启用 HTTP Basic。
- [ ] Redis Starter 重复声明被删除。
- [ ] 测试环境使用隔离测试值且 Context 测试通过。
- [ ] 没有真实 Secret 进入 Git diff。
- [ ] quick Harness 通过。
- [ ] `gpt-5.5` 只对最终安全相关 diff 做一次审查。

## 验证命令

```powershell
cd backend-java
mvn test

cd ..
rg -n "161764|password:\\s*123456|local-secret-key|change-this-to-a-strong-secret-key" `
  backend-java frontend-vue .env.example docker-compose.yml

.\tools\verify.ps1 -Profile quick
```

说明：`rg` 命令发现测试 fixture 或文档中的说明文字时，需要人工判断；不能仅根据字符串出现就自动删除。

## 审查清单

- 是否误提交本机 `.env`。
- 是否把弱默认值换成了另一个弱默认值。
- 测试配置是否可能被生产 profile 加载。
- 关闭 HTTP Basic 后 JWT 流程是否保持。
- CORS 是否被意外放宽。
- 日志是否打印 Token、密码、Prompt 或 API Key。
- 是否出现与安全配置无关的重构。

## 完成定义

- 所有验收标准通过。
- quick Harness 返回 0。
- 已使用 `gpt-5.5` 对安全 diff 做只读审查。
- 审查发现的问题已修复或记录。
- diff 不包含任何真实 Secret。
- 配置和启动文档保持一致。
- 你能够解释开发配置、测试配置和生产配置的边界。

