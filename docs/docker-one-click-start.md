# Docker 一键启动与健康门禁

本指南只面向本仓库的本地 Docker Compose 环境。默认模式使用受控 mock fallback，不需要个人模型密钥；界面、脚本和后续报告必须明确它不是真实 Provider 结果。

## 首次启动

前置条件：PowerShell 7、Git、Docker Desktop/Engine（含 Compose v2）已启动，主机至少有 4 个逻辑 CPU、8 GiB 内存和 20 GiB 可用磁盘。在仓库根目录运行：

```powershell
.\start-docker.ps1
```

脚本会在 `.env` 不存在时生成仅本机使用的随机 MySQL、应用数据库、Redis、RabbitMQ 和 JWT 凭据，并把 `.env` 保持在 Git 忽略范围内。它不会打印任何凭据，也不会复制带占位符或弱默认值的模板。随后脚本执行：

```text
docker compose config --quiet
-> docker compose up -d --build
-> MySQL / Redis / RabbitMQ / Python / Java / Vue health gate
-> Java dependency readiness and browser-facing endpoint checks
```

成功时所有服务持续在后台运行。主机端口仅绑定到 `127.0.0.1`：Vue `5173`、Java `8080`、Python `8000` 和 MySQL `3307`。Redis 与 RabbitMQ（AMQP、Management UI）均为 Compose 内部服务；需要队列诊断时使用受控的 `docker compose exec`，不开放管理控制台。

打开 `http://127.0.0.1:5173` 后，先注册一个**业务账号**，再创建项目并提交游戏想法。业务账号由页面注册产生；它不是 MySQL、Redis、RabbitMQ 或 Docker 的连接账号，也不应写入仓库或演示文档。

若某个默认端口已被本机其他程序占用，Compose 会失败并保留具体端口错误，不会假装服务 healthy。可只在本机 `.env` 覆盖对应变量后重试，例如：

```env
BACKEND_HOST_PORT=18080
```

可覆盖项是 `MYSQL_HOST_PORT`、`PYTHON_AGENT_HOST_PORT`、`BACKEND_HOST_PORT` 和 `FRONTEND_HOST_PORT`；它们仍只绑定 loopback，禁止为了避错改成 `0.0.0.0`。

## 健康与诊断

可单独运行同一门禁；`-SkipBuild` 只跳过镜像构建，不跳过 Compose、健康和 HTTP readiness 验证：

```powershell
.\tools\verify-bootstrap.ps1
.\tools\verify-bootstrap.ps1 -SkipBuild
docker compose ps
```

健康信号有不同含义：

- MySQL、Redis、RabbitMQ：各自容器的依赖可用性。
- Python：`/health` 可响应；默认没有模型密钥时返回的是 mock-capable Agent 服务。
- Java：Docker health 调用 `/actuator/health`，其中包括数据库、Redis 和 RabbitMQ 依赖；`/api/health` 只表示进程 API 可响应，不能代替 dependency readiness。
- Vue：静态应用入口可响应，且只在 Java health 通过后启动。

单个服务失败时先查看状态和最后日志；不要为了让启动显示成功而跳过 healthcheck：

```powershell
docker compose ps
docker compose logs --tail 100 mysql
docker compose logs --tail 100 redis
docker compose logs --tail 100 rabbitmq
docker compose logs --tail 100 mysql-bootstrap
docker compose logs --tail 100 backend-java
docker compose logs --tail 100 python-agent
docker compose logs --tail 100 frontend-vue
```

## 浏览器主链路验收

浏览器验收使用一次性的隔离 Compose 栈和受控测试账号；脚本会在完成后清理该账号及其项目、运行和关联数据。隔离栈需要 `8080`、`5173`、`8000`、`3307` 空闲，因此先停止日常本地栈，再执行：

```powershell
.\tools\stop-docker.ps1
.\tools\verify.ps1 -Profile e2e
```

该验收覆盖注册/登录、项目创建与选择、生成提交、运行详情刷新及 Phaser 预览。它使用临时本机凭据，生成的 evidence 和 Playwright 临时文件不得提交。

## 已有 `.env` 或 volume 的安全升级

不要用 `.env.example` 覆盖已有 `.env`，也不要把 `.env` 提交到 Git。R7-01 会用一次性的 `mysql-bootstrap` 服务在 MySQL health 通过后幂等创建/更新非 root 的 `DB_USERNAME`，因此已有 MySQL volume 可以迁移到应用账号而不删除数据。

已有环境须先确认以下值存在且不是占位符：`MYSQL_ROOT_PASSWORD`、`DB_PASSWORD`、`JWT_SECRET`、`REDIS_PASSWORD`、`RABBITMQ_PASSWORD`。它们必须满足启动脚本的最小长度检查；`DB_USERNAME` 不能为 `root`，`RABBITMQ_USERNAME` 不能为 `guest`。MySQL 的 root 凭据必须保留为该现有 volume 初始化时使用的值；不要在保留 volume 的同时擅自更换 RabbitMQ 默认用户名/密码，因为官方镜像只在首次初始化时创建该账号。

本地 Compose 的 MySQL 明确启用 `log-bin-trust-function-creators=1`，以便受限应用账号执行仓库中已存在的 Flyway trigger migration；后端进程不会持有 root 凭据。此设置仅是单机演示/验证兼容项，不构成生产数据库或复制拓扑建议。

首次生成的 `.env` 默认包含：

```env
LLM_API_KEY=
LLM_ENABLE_MOCK_FALLBACK=true
```

这是可复现的 mock 模式，不是对真实模型能力的声明。要测试真实 Provider，按其安全方式在本机 `.env` 填入密钥，并设置 `LLM_ENABLE_MOCK_FALLBACK=false`；不得把密钥、完整 Prompt 或真实响应写入日志、截图或报告。

## 停止与本地数据清理

默认停止是非破坏性的，保留 MySQL/Redis named volumes：

```powershell
.\tools\stop-docker.ps1
```

> **破坏性，仅本项目本地数据。** 下列命令会删除本 Compose 项目的 MySQL 和 Redis volumes，无法恢复。先自行备份；脚本要求显式 `-Confirm`，不会默认执行。

```powershell
.\tools\reset-local-data.ps1 -Confirm
```

清理后再次运行 `.\start-docker.ps1` 即从空 volume 执行 Flyway migration 和健康门禁。不得使用 `docker system prune`、全局 volume 删除或未确认的 `docker compose down -v` 作为本项目的日常停止流程。
# V3 发布主链路

服务全部 healthy 后，可运行不依赖隐藏手工步骤的 V3 验收：

```powershell
cd frontend-vue
npm run test:e2e:main
```

该命令连接 `http://127.0.0.1:5173` 与 `http://127.0.0.1:8080`，真实执行 Brief、AI 生成、两个不可变版本、试玩聚合、平衡建议和 ZIP 导出。等待使用持久化 WorkflowRun 状态轮询，不用固定长 sleep。若端口被覆盖，可设置 `E2E_FRONTEND_BASE_URL` 和 `E2E_API_BASE_URL`。

## 前端单独升级与回滚

升级前保留当前前端镜像标签，然后只构建和替换前端服务：

```powershell
docker image tag gamedevagentworkbench-frontend-vue:latest gamedevagentworkbench-frontend-vue:rollback
docker compose build frontend-vue
docker compose up -d --no-deps frontend-vue
docker compose ps frontend-vue
```

若新前端验收失败，可恢复保留的镜像；该操作不会降级 Flyway V32，也不会触碰数据库 volume：

```powershell
docker image tag gamedevagentworkbench-frontend-vue:rollback gamedevagentworkbench-frontend-vue:latest
docker compose up -d --no-deps --no-build --force-recreate frontend-vue
docker compose ps frontend-vue
```

如果镜像标签已不可用，则在独立 Git worktree 检出上一个已验收提交并重新构建前端，避免覆盖当前工作区。回滚后重新执行 `npm run test:e2e:main`；不得为前端回滚执行 `docker compose down -v` 或数据库 migration 降级。
