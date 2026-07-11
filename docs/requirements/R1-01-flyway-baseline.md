# R1-01：接入 Flyway 与 Baseline 迁移

> 状态：`READY_AFTER_R1-00`
>
> 前置任务：`R1-00`
>
> 推荐模型：`gpt-5.5`
>
> 任务类型：数据库迁移 / 高风险

## 背景

当前项目主要依赖初始化 SQL 和 Docker MySQL init 脚本。继续通过修改旧 SQL 升级数据库，会导致已有本地数据库和新环境之间漂移。

R1 需要先建立 Flyway 迁移地基，让后续 WorkflowDefinition、StepRun、PromptVersion 都通过增量迁移进入项目。

## 目标

实现：

```text
Spring Boot 启动时可执行 Flyway migration
+ 空库可以初始化
+ 已有库可以建立 baseline
+ 后续迁移不再靠修改旧 init SQL
```

## 范围

允许：

- 在 `backend-java/pom.xml` 增加 Flyway 相关依赖。
- 配置 Spring Boot Flyway。
- 新增 `backend-java/src/main/resources/db/migration/`。
- 建立 `V1__baseline.sql` 或等价 baseline 策略。
- 增加 migration smoke test 或 context test。
- 更新必要配置说明。

## 非目标

- 不新增 R1 业务表。
- 不改 Workflow 执行逻辑。
- 不改现有 Controller API。
- 不引入 MQ、Outbox、RAG。
- 不删除 Docker init 脚本，除非设计文档明确说明迁移顺序。
- 不做生产数据库回滚脚本。

## 约束

- Flyway 迁移必须能在空库运行。
- 对已有本地库必须有清晰 baseline 说明。
- 不允许破坏 `tools/verify.ps1 -Profile quick`。
- 不允许将真实数据库密码写入配置。
- migration 文件一旦提交，后续不得修改已发布版本，只能新增版本。

## 验收标准

- [ ] `backend-java/pom.xml` 中 Flyway 依赖清晰且无重复依赖 warning。
- [ ] `db/migration` 目录存在，并包含 baseline migration。
- [ ] Spring Context 测试通过。
- [ ] quick Harness 通过。
- [ ] 文档说明空库和已有库分别如何处理。
- [ ] Docker Compose config 仍通过。
- [ ] 没有修改业务 Workflow 行为。

## 验证命令

```powershell
cd backend-java
mvn test

cd ..
.\tools\verify.ps1 -Profile quick
```

## 审查清单

- 是否修改了已提交的旧 migration。
- 是否把初始化 SQL 和 migration 做成两套互相冲突的来源。
- 是否要求开发者手动改真实数据库但没有文档。
- 是否引入了破坏已有数据的 DDL。
- 是否因为缺少数据库而导致普通单元测试无法启动。

## 完成定义

- Flyway 已接入并可被测试证明。
- 迁移目录和 baseline 策略明确。
- 本任务形成一个独立 commit。
