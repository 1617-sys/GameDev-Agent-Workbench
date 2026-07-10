# R0 Baseline Report

## 环境

- 日期：2026-07-11
- 分支：`codex/r0-foundation`
- Java：`21.0.8`
- Maven：`3.9.11`
- Python：`3.13.3`
- Node：`v24.14.1`
- npm：`11.11.0`
- Docker：`29.1.5`

## 验证结果

| 检查 | 命令 | 结果 | 耗时 |
| --- | --- | --- | --- |
| Git 状态 | `git status --short` | PASS；本次验收产生 3 个任务卡状态修改和 `docs/reports/R0-baseline-report.md`；另有未跟踪文件 `docs/GIT_COMMANDS_GUIDE.md` 未纳入本次提交建议 | <1s |
| Git 空白检查 | `git diff --check` | PASS；无 whitespace error；Git 提示 3 个任务卡文件下次触碰时会从 LF 转为 CRLF | <1s |
| Harness 失败处理 | 读取 `tools/verify.ps1` | PASS；各步骤失败会记录模块名，最终 `exit 1` | <1s |
| Quick Harness | `.\tools\verify.ps1 -Profile quick` | PASS；Java tests、Python compile、Vue production build、Docker Compose config 全部通过 | 约 25s |
| Java 目标测试 | `mvn '-Dtest=DemoStreamServiceImplTest,RedisServiceImplTest,WorkflowServiceImplTest' test` | PASS；13 tests, 0 failures, 0 errors | 约 10s |
| GameConfig 契约测试 | `npm run test:game-config` | PASS；9 tests, 0 failures | 约 1s |
| Vue build | `npm run build` | PASS；仍有 chunk size warning | 约 6s |
| Compose 配置 | `docker compose config --quiet` | PASS；无输出，退出码 0 | <1s |
| Secret/弱默认值扫描 | `rg -n "161764|password:\s*123456|local-secret-key|change-this-to-a-strong-secret-key|123456" backend-java frontend-vue .env.example docker-compose.yml` | PASS；无命中，`rg` 退出码 1 表示未找到匹配 | <1s |

## 修复问题

- Redis 锁：
  - `DemoStreamServiceImplTest`、`RedisServiceImplTest` 通过。
  - 已验证鉴权在 Redis 前、锁 owner token 唯一、`SET NX EX` 语义、未获取锁不执行 Workflow、不释放锁、owner 匹配才 Lua 原子释放。
- Workflow 测试：
  - `WorkflowServiceImplTest` 通过。
  - 已固定成功路径的 `RUNNING -> SUCCESS`、三步 Agent 顺序、三个 Artifact 创建，以及失败路径的 `RUNNING -> FAILED`、原业务异常透传、未知异常转换为 `SYSTEM_ERROR`。
- GameConfig 契约：
  - `npm run test:game-config` 通过。
  - 已覆盖默认配置、包装结构提取、Artifact 优先级、非法 JSON、非法结构、历史字段归一化。
- 安全配置：
  - Quick Harness 中 Java 测试通过，包含 `SecurityConfigTest` 与 `JwtServiceTest`。
  - `pom.xml` 不再出现 Redis Starter 重复声明 warning。
  - Secret/弱默认值扫描未命中目标弱值。
  - Spring Security 启动日志使用项目 `userDetailsService`，未再出现 Boot 默认 generated security password。

## 已知警告

- 警告：Java 测试输出 Mockito/ByteBuddy 动态 Java agent warning。
  - 归属阶段：测试运行环境/JDK 兼容性；不阻塞 R0。
- 警告：Vue build 输出 `Some chunks are larger than 500 kB after minification`。
  - 归属阶段：R4 前端性能与 chunk 拆分；R0 明确不修复。
- 警告：`git status --short` 显示未跟踪 `docs/GIT_COMMANDS_GUIDE.md`。
  - 归属阶段：当前工作区既有未跟踪文档；未纳入 R0 验收提交建议，除非单独确认。
- 警告：`git diff --check` 对 3 个任务卡文件提示 LF/CRLF 转换。
  - 归属阶段：Git 工作区行尾策略；命令退出码为 0，不属于 whitespace error。

## Git 范围

- R0 文件：
  - Redis 锁与测试：`DemoStreamServiceImpl.java`、`RedisService.java`、`RedisServiceImpl.java`、`ErrorCode.java`、`DemoStreamServiceImplTest.java`、`RedisServiceImplTest.java`、`docs/PITFALLS.md`、`docs/redis-integration-plan.md`。
  - Workflow 状态测试：`WorkflowServiceImpl.java`、`WorkflowServiceImplTest.java`、`docs/requirements/R0-03-workflow-state-tests.md`。
  - GameConfig 契约：`frontend-vue/src/game/defaultGameConfig.js`、`frontend-vue/src/game/gameConfig.js`、`frontend-vue/tests/gameConfig.test.js`、`frontend-vue/package.json`、`docs/game-config-schema.md`。
  - 安全配置：`.env.example`、`backend-java/pom.xml`、`SecurityConfig.java`、`JwtService.java`、`application.yml`、`application-example.yml`、`backend-java/src/test/resources/application.yml`、`SecurityConfigTest.java`、`JwtServiceTest.java`、`frontend-vue/src/App.vue`、`python-agent/.env.example`。
  - R0 验收：`docs/reports/R0-baseline-report.md`、`docs/requirements/R0-04-game-config-contract.md`、`docs/requirements/R0-05-security-config.md`、`docs/requirements/R0-ACCEPTANCE-baseline.md`。
- 既有文件：
  - 当前 `git log --oneline -5` 显示 R0 相关提交已经存在：`test: cover workflow state transitions`、`test: add game config contract coverage`、`chore: harden baseline security config` 等。
  - 本次验收开始时 `git diff --stat` 为空，说明多数 R0 实现已在当前分支历史中。
  - 本次验收完成后，工作区预期新增/修改文件为本报告和 3 个任务卡状态字段。
- 不应提交：
  - 真实 `.env` 文件、构建产物、IDE 文件。
  - 未确认归属的 `docs/GIT_COMMANDS_GUIDE.md`。

## Commit 分组建议

1. 已存在的实现提交保持不动。
2. 本次验收单独提交：
   - `docs/reports/R0-baseline-report.md`
   - `docs/requirements/R0-04-game-config-contract.md`
   - `docs/requirements/R0-05-security-config.md`
   - `docs/requirements/R0-ACCEPTANCE-baseline.md`
3. 不把 `docs/GIT_COMMANDS_GUIDE.md` 放入本次 commit，除非后续明确确认它属于本次验收。

## R1 准入结论

- PASS
- 原因：
  - Quick Harness 返回 0。
  - Redis Lock、Workflow、GameConfig 目标测试均通过。
  - `git diff --check` 通过。
  - Compose 配置通过。
  - Secret/弱默认值扫描未发现目标弱值。
  - 已知 warning 均已记录且不属于 R0 阻塞项。
