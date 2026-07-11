# R1 Flyway baseline 使用说明

> 对应任务：`R1-01-flyway-baseline.md`
>
> 基线版本：`1`

## 迁移来源与边界

`backend-java/src/main/resources/db/migration/V1__baseline.sql` 固化了当前 Docker MySQL 初始化链路中的完整结构：`sys_user`、`game_project`、`agent_run`、`agent_artifact`、`workflow_run` 与 `prompt_template`。

它不包含 `CREATE DATABASE`、`USE gamedev_agent_workbench` 或种子数据，因此会在 Spring 当前数据源所指向的 schema 中执行。旧的 Docker init 脚本保留不动，继续负责 Docker 首次启动时的 schema 与 prompt 种子数据；后续结构演进只能新增 `V2__...sql` 及更高版本的 Flyway migration，不能修改已提交的 V1。

## 空数据库

当目标 schema 为空时，Spring Boot 启动会执行 V1，创建完整的当前基线表结构，并在 `flyway_schema_history` 记录版本 `1`。

配置位于 `backend-java/src/main/resources/application.yml`：

```yaml
spring:
  flyway:
    baseline-on-migrate: true
    baseline-version: 1
    validate-on-migrate: true
    clean-disabled: true
```

## 已有本地数据库

已有库通常已经由 Docker init 或此前的 SQL 创建表，但没有 `flyway_schema_history`。首次由应用启动时，Flyway 会以版本 `1` 建立 baseline 记录，不会重新执行 V1，也不会修改已有表或数据。之后新增的 V2+ migration 会按版本顺序执行。

前提是已有库的结构与 V1 对应；若数据库来自其他版本或已人工改动，应先备份并比对表结构，不能依赖 baseline 掩盖漂移。生产环境应由发布流程在备份后执行迁移，不使用 `clean`，也不通过修改旧 migration 回滚。

## 验证

```powershell
cd backend-java
mvn test

cd ..
.\tools\verify.ps1 -Profile quick
```

`FlywayMigrationContextTest` 是不依赖本地 MySQL 账号的 migration smoke test：它验证 V1 位于 Flyway classpath 目录、包含完整的当前表结构，且不会固定到 `gamedev_agent_workbench` 数据库。测试配置关闭 Flyway，避免普通单元测试和 MVC Context 测试因未配置本地数据库而失败；部署和专用迁移环境使用主配置执行真实迁移。
