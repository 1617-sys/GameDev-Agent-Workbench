# R3-01: RabbitMQ 与集成验证基础设施

> 状态：`TODO`
>
> 前置任务：`R3-00`
>
> 推荐模型：`gpt-5.4`（配置与重复工作） / `gpt-5.5`（首次审查）
>
> 任务类型：基础设施 / 配置验证

## 背景

R3 需要 RabbitMQ 承载异步工作流消息，同时继续使用既有 MySQL、Redis 和 Docker Compose 基线。基础设施必须先可重复启动，业务代码才有稳定的集成测试环境。

## 目标

让本地和测试环境具备下列最小能力：

```text
Docker Compose
-> MySQL
-> Redis
-> RabbitMQ management

Spring Boot profile
-> RabbitMQ connection/configuration
-> JSON message converter
-> listener disabled or safe by default in unit-test profile

Integration Harness
-> Testcontainers MySQL + Redis + RabbitMQ
```

## 范围

允许：

- 更新 `docker-compose.yml`、环境变量示例、Spring 配置和 Maven 依赖。
- 新增 RabbitMQ 最小拓扑配置类、消息 JSON converter 和配置属性对象。
- 添加 Testcontainers RabbitMQ/Redis/MySQL 测试基础类或可复用测试支持。
- 扩展 `tools/verify.ps1 -Profile integration` 的最小检查入口，但不要求 R3 全链路已通过。
- 增加配置加载、Docker Compose config、RabbitMQ 连接 smoke test。

## 非目标

- 不实现提交 API、Outbox、Publisher、Consumer、ACK、重试或 DLQ 业务逻辑。
- 不在此任务中执行真实 LLM/Agent 调用。
- 不提交真实 RabbitMQ/MySQL/Redis 密码、URI 或云凭证。
- 不修改 Vue、SSE 或 Python Agent 协议。

## 约束

- 所有配置通过环境变量注入；示例文件只保留占位符。
- 单元测试默认不能因本地 RabbitMQ 未运行而失败；集成测试显式使用 Testcontainers 或 compose profile。
- 管理端口不应暴露到生产默认配置；本地 compose 端口说明写入文档或示例配置。
- RabbitMQ 连接失败必须有可定位日志，不能导致应用伪装成已接收异步任务。
- 依赖只允许添加一个清晰用途的 AMQP starter/Testcontainers 模块，避免重复 starter。

## 验收标准

- [ ] `docker compose config` 能解析 MySQL、Redis、RabbitMQ 服务与健康检查/依赖关系。
- [ ] Spring Boot 在异步 profile 可加载 RabbitMQ 配置和 JSON converter。
- [ ] 单元测试 profile 不依赖个人本地 MQ 服务。
- [ ] 至少一个 Testcontainers smoke test 可以连接 RabbitMQ、Redis、MySQL。
- [ ] `.env.example`、`application-example.yml` 等不包含真实凭证或弱默认 Secret。
- [ ] quick Harness 不回归；integration Harness 有可执行入口和清晰的未实现提示。

## 验证命令

```powershell
docker compose config

cd backend-java
mvn -Dtest=*RabbitMq*Test,*MessagingConfiguration*Test test
mvn test

cd ..
.\tools\verify.ps1 -Profile quick
.\tools\verify.ps1 -Profile integration
```

## 审查清单

- 是否因加载配置让普通 `mvn test` 强制连接本地 MQ。
- 是否重复引入 Redis/RabbitMQ starter 或硬编码地址、账号、密码。
- 是否没有健康检查或未记录服务启动依赖。
- 是否用 Unit Test Mock 冒充 RabbitMQ 集成验证。
- 是否提前实现 Consumer/Outbox，导致基础设施与业务变更混在一个提交。

## 完成定义

- R3 后续任务有可重复的消息中间件本地/集成验证基础。
- 基础设施配置与业务消息语义保持分离。
