# R7-01: 新环境 Docker 一键启动与健康门禁

> 状态：`TODO`
>
> 前置任务：`R7-00`
>
> 推荐模型：`gpt-5.4`（脚本与配置） / `gpt-5.5`（安全审查）
>
> 任务类型：部署基线 / Fresh environment 验证

## 背景

仓库已有 Docker Compose 和一键启动说明，但最终交付必须证明：一台没有历史容器、数据库、缓存和本地配置的新环境，可以只按文档完成构建、迁移、健康检查和基础访问。

## 目标

建立可重复的一键启动链路：

```text
clone repository
-> configure documented environment variables
-> docker compose up -d --build
-> dependency health gates
-> Flyway migrations
-> Java/Python/Vue health/readiness
-> deterministic demo seed readiness
```

提供非破坏性默认启动/停止脚本和明确标记的“清空本地数据”命令。

## 范围

允许：

- 审查/更新 Dockerfiles、Compose、`.env.example`、application examples、健康检查、启动顺序和资源限制。
- 完善 `docs/docker-one-click-start.md`、跨 Windows PowerShell 的 bootstrap/health verification 脚本。
- 验证空 volume 初始化、Flyway 全量 migration、服务重启、已有 volume 升级和停止/重启。
- 增加 compose config、镜像构建、健康等待、端口冲突/缺少配置/依赖未就绪测试。
- 增加发布所需的最小 demo seed，但不写入真实密钥或个人数据。

## 非目标

- 不部署 Kubernetes、云数据库、域名、TLS 证书或生产级高可用集群。
- 不把真实 Provider API Key 写入镜像、Compose、日志或示例文件。
- 不默认删除 volumes、用户数据或 Docker 全局资源。
- 不修改核心业务语义来绕过启动失败。
- 不将开发机已有数据库作为成功前提。

## 约束

- 默认命令必须非破坏性；`down -v` 等删除数据操作必须独立、醒目标记并要求人工确认。
- Secret 只通过环境变量/外部配置注入，镜像层和 Git 历史中不得出现真实值。
- healthcheck 要区分进程存活与依赖/应用 ready，不能只检查端口打开。
- Java 必须等 MySQL/Redis/RabbitMQ ready 后启动或安全重试；前端/测试不能在后端未 ready 时假成功。
- migration 失败时应用不得伪装健康；已有数据库升级需保留数据并记录验证证据。
- 所有脚本在仓库根目录可运行，失败返回非零并说明具体服务。

## 验收标准

- [ ] 全新 Docker 环境按文档一条主命令可构建并启动全部服务。
- [ ] MySQL、Redis、RabbitMQ、Java、Python、Vue 均有可验证健康/readiness 状态。
- [ ] 空库执行全部 Flyway migration，已有数据环境升级后核心查询仍可用。
- [ ] 服务重启不丢失持久化数据，错误配置/依赖失败能明确阻断并定位。
- [ ] 默认停止流程不删除数据，破坏性清理命令有明确警告。
- [ ] 镜像、配置、日志和示例文件不包含真实 Secret。

## 验证命令

```powershell
docker compose config
docker compose build
docker compose up -d
docker compose ps
.\tools\verify.ps1 -Profile quick
```

## 审查清单

- 是否依赖开发机残留 volume/网络/配置才能启动。
- 是否 healthcheck 只检查端口而不检查 migration/依赖 readiness。
- 是否默认脚本删除 volume 或覆盖用户配置。
- 是否 Secret 被写入镜像层、compose、日志或 README。
- 是否启动失败仍返回 0 或显示服务 healthy。

## 完成定义

- 新用户无需了解内部实现即可在新环境安全启动并验证完整技术栈。
- 一键启动成为 R7 后续 E2E 和演示的稳定基线。
