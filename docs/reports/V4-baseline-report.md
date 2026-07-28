# V4 改造前基线报告

> 任务：V4-01 当前基线冻结  
> 采集时间：2026-07-27 20:46—20:48（Asia/Shanghai）  
> Git：`codex/v4`，`592d1485b78dbe9a9d5eb08b92d01ba2cb3d7084`

## 1. 结论

本报告只记录 V4 改造前基线，没有修改生产代码、依赖、数据库或 Docker 配置。四条必执行命令均已尝试：Java、前端单元测试和 Phaser Runtime smoke 通过；Python `pytest` 在收集阶段因当前命令入口无法导入本地 `app` 包而被环境阻塞，测试体未运行。没有为获得绿色结果调整代码、环境变量或命令。

| 范围 | 状态 | 执行/通过/失败/跳过 | 命令墙钟耗时 | 工具报告耗时 |
|---|---|---:|---:|---:|
| Java 单元测试 | PASS | 182 / 181 / 0 / 1 | 40.331 s | Maven 38.164 s；Surefire suites 29.428 s |
| Python 单元测试 | BLOCKED（收集失败） | 0 / 0 / 0 / 0；另有 7 个 collection errors | 5.269 s | pytest 1.25 s |
| 前端单元测试 | PASS | 32 / 32 / 0 / 0 | 2.471 s | Node test 874.978 ms |
| Runtime smoke | PASS | 4 / 4 / 0 / 0 | 28.140 s | Playwright 25.8 s |

状态口径：`PASS` 表示命令退出码为 0；`BLOCKED` 表示已执行命令但测试体未开始；本次没有未尝试的必执行命令。Java 的 182 个测试中 1 个跳过，因此通过数为 181。

## 2. 可复现环境与命令

### 2.1 环境

| 项目 | 当前值 |
|---|---|
| OS | Microsoft Windows 11 家庭版中文版 `10.0.26200`，64-bit |
| Java | Oracle JDK `21.0.8` |
| Maven | `3.9.11` |
| Python | `3.13.3` |
| pytest | `9.1.1` |
| Node.js | `v24.14.1` |
| npm | `11.11.0` |
| 前端应用 | `gamedev-agent-workbench-web@2.1.0` |
| 已解析核心前端依赖 | Vue `3.5.39`、Phaser `3.90.0`、Vite `6.4.3`、Playwright `1.55.1` |

新环境应先按各模块清单安装依赖：Java 使用 `backend-java/pom.xml`，Python 使用 `python-agent/requirements.txt`，前端使用 `frontend-vue/package-lock.json`（推荐 `npm ci`）。随后从仓库根目录在 PowerShell 中执行任务卡的原始命令：

```powershell
cd backend-java; mvn test
cd ..\python-agent; pytest
cd ..\frontend-vue; npm run test:unit
npm run test:runtime-smoke
```

Runtime smoke 需要 Playwright 浏览器已安装；本机测试使用 Playwright `1.55.1` 的 Chromium 环境。

### 2.2 原始结果摘要

#### Java：PASS

- 退出码：`0`
- Maven 汇总：`Tests run: 182, Failures: 0, Errors: 0, Skipped: 1`，`BUILD SUCCESS`。
- 测试期间 `ActuatorHealthSecurityTest` 输出本机 MySQL JDBC health check 失败警告，但这是受测 health 响应的一部分，没有造成用例失败；本机 RabbitMQ `localhost:5672` 可连接。
- JVM 另输出 Byte Buddy 动态加载 agent 的未来兼容性警告，不影响本次结果。

#### Python：BLOCKED（不是测试失败）

- 退出码：`2`
- pytest 输出：`collected 0 items / 7 errors`，随后 `Interrupted: 7 errors during collection`。
- 7 个测试模块均在导入时失败，核心错误原文为：`ModuleNotFoundError: No module named 'app'`。
- 环境条件：Windows 11、Python `3.13.3`、pytest `9.1.1`；从 `python-agent` 目录直接执行任务卡指定的 `pytest` 控制台命令。
- 另有 1 条 `StarletteDeprecationWarning`（`fastapi.testclient` 的 httpx 兼容提示）。因收集已中断，不能把仓库中测试函数的静态数量宣称为本次已运行数量。

#### 前端单元测试：PASS

- 退出码：`0`
- Node test 汇总：`tests 32`、`pass 32`、`fail 0`、`cancelled 0`、`skipped 0`、`todo 0`。

#### Phaser Runtime smoke：PASS

- 退出码：`0`
- Playwright 汇总：`4 passed (25.8s)`。
- 覆盖桌面启动/暂停/继续/重开、键盘收集并通关、精灵加载失败后的几何占位降级，以及 375px 移动视口触控与无横向溢出。

## 3. 旧同步 / 单 Agent API 基线

仓库静态搜索未发现 `frontend-vue/src/**` 对下列旧入口的调用。当前前端通过 `frontend-vue/src/shared/api/workflows.js` 使用 `/api/v1/**` 异步工作流 API；因此“调用方”只能确认仓库内前端为零，外部客户端和真实调用量没有现成运行指标，不能推断为零。

| API | 仓库内调用方 | 当前用途 | 建议弃用阶段 |
|---|---|---|---|
| `POST /api/agent/run` | 前端无；Controller 直接进入 `AgentRunService` | 同步运行一个指定 Agent | Upgrade 0 标记 deprecated、停止新增；Upgrade 1—2 仅作固定基线/回归；Upgrade 5 对照实验后冻结；Upgrade 6 前满足清零与回滚门禁后删除 |
| `GET /api/agent/runs` | 前端无 | 查询旧单 Agent 运行列表 | 与单 Agent 写入口同步弃用 |
| `GET /api/agent/runs/{runUuid}` | 前端无 | 查询旧单 Agent 运行详情 | 与单 Agent 写入口同步弃用 |
| `POST /api/workflow/game-design/run` | 前端无；Controller 进入 `WorkflowService` | 同步执行固定游戏设计 Workflow | Upgrade 1—2 保留为回归基线；Upgrade 3 默认 UI 流量切至 Director；Upgrade 5 冻结；Upgrade 6 前满足门禁后删除 |
| `GET /api/workflow/{workflowRunUuid}` | 前端无 | 读取旧同步 Workflow 结果 | 随旧同步写入口一起弃用 |
| `POST /api/demo/game/stream` | 前端无；仅 `!prod` Profile | 旧 Demo SSE，一次请求驱动并流式展示固定 Workflow | Upgrade 1—2 仅保留兼容/演示回归；Upgrade 3 后不承接 UI 默认流量；Upgrade 5 冻结；Upgrade 6 前满足门禁后删除 |

上述阶段直接采用 `docs/requirements/agentic-game-design-lab-prd.md` 第 10 节：Upgrade 0 记录与标记、Upgrade 1—2 保留基线、Upgrade 3 切换默认 UI、Upgrade 5 冻结、Upgrade 6 前仅在调用方清零、迁移文档和回滚验证完成后删除。本任务只记录计划，没有给 Controller 添加注解或改变行为。

当前前端的 canonical 调用方如下：

- `frontend-vue/src/features/studio/StudioPage.vue`：通过 `workflowsApi.submit/projectRuns` 使用项目作用域异步 API。
- `frontend-vue/src/features/runs/runStore.js`：通过 `/api/v1/workflow-runs/**` 读取快照、步骤、产物，执行 cancel/retry，并订阅 SSE。
- `frontend-vue/src/features/demo/DemoPage.vue`：只读取 v1 Workflow/Artifact 后挂载预览，不调用旧 Demo SSE。

## 4. Phaser Runtime、GameConfig 与遥测入口

### 4.1 Phaser Runtime

| 层 | 当前入口 | 作用 |
|---|---|---|
| Vue 挂载 | `frontend-vue/src/features/demo/GamePreview.vue` | 校验配置，创建遥测 reporter，调用 `mountGeneratedGame`，转发 HUD、告警和遥测回调 |
| Runtime 工厂 | `frontend-vue/src/features/demo/runtime/topDownCollectRuntime.js` 的 `mountGeneratedGame` | 复制规范化配置并创建 `new Phaser.Game(...)`，使用 Arcade Physics 和 `ArcadeCollectScene` |
| 玩法状态 | `frontend-vue/src/features/demo/runtime/runtimeState.js` 的 `ArcadeCollectStateMachine` | READY/PLAYING/PAUSED/WON/LOST、计分、生命、倒计时和确定性初始敌人方向 |
| 页面调用方 | `RunPage.vue`、`DemoPage.vue`、`PrototypeVersionsPage.vue` | 分别展示工作流产物、只读 Demo、绑定版本和遥测的试玩 |

### 4.2 GameConfig 2.0 合约

- 人类可读入口：`docs/game-config-schema.md`。
- 前端执行边界：`frontend-vue/src/features/demo/runtime/gameConfig.js` 的 `validateGameConfig` / `normalizeGameConfig`；校验通过后才允许挂载 Phaser。
- Java 权威业务校验入口：`backend-java/src/main/java/com/example/gameworkbench/gameconfig/GameConfigContract.java`，固定 `SCHEMA_KEY=game-config`、`SCHEMA_VERSION=2.0`、`GAME_TYPE=arcade_collect`，并在 Workflow 产物、PrototypeVersion、导出和遥测复算路径复用。
- Python 生成校验入口：`python-agent/app/schemas/game_config.py` 的 `GameConfigV2` / `validate_game_config_v2`；规范样例为 `python-agent/app/contracts/game-config-2.0.valid.json`。
- 当前仍支持把合法 GameConfig 1.0 历史数据迁移为只读 2.0 预览，但 Runtime 的正式执行配置为完整 2.0。

### 4.3 试玩遥测

事件从 `topDownCollectRuntime.js` 的 `onTelemetry` 产生，经 `GamePreview.vue` 转交 `frontend-vue/src/shared/api/telemetry.js` 的 `createTelemetryReporter`。Reporter 创建 session、生成单调 sequence 和 UUID、最多 50 条一批，再写入 Java API。

| 方法与路径 | 前端调用 | 当前用途 |
|---|---|---|
| `POST /api/projects/{projectUuid}/prototype-versions/{versionUuid}/playtest-sessions` | `telemetryApi.createSession` | 为不可变 PrototypeVersion 创建试玩会话 |
| `POST /api/projects/{projectUuid}/playtest-sessions/{sessionUuid}/events` | `telemetryApi.ingest` | 批量写入受限事件事实 |
| `GET /api/projects/{projectUuid}/playtest-sessions/{sessionUuid}` | `telemetryApi.session` | 查询会话复算结果 |
| `GET /api/projects/{projectUuid}/prototype-versions/{versionUuid}/playtest-metrics` | `telemetryApi.metrics` | 查询版本聚合指标 |
| `GET /api/projects/{projectUuid}/playtest-metrics/compare` | `telemetryApi.compare` | 对比两个版本指标 |
| `POST /api/projects/{projectUuid}/prototype-versions/{versionUuid}/balance-suggestions` | `telemetryApi.suggest` | 基于版本试玩事实生成平衡建议 |

允许事件严格为：`SESSION_STARTED`、`ITEM_COLLECTED`、`PLAYER_HIT`、`GAME_WON`、`GAME_LOST`、`SESSION_RESTARTED`、`SESSION_ENDED`。Java 入口为 `PlaytestTelemetryController`，事实校验与服务端指标复算在 `PlaytestTelemetryServiceImpl`。

## 5. 工作树边界

首次执行测试前，`git status --short` 已显示用户既有未跟踪目录 `docs/requirements/v4/`，其中包括 `README.md` 和 `V4-01` 至 `V4-08` 共 9 个 Markdown 文件。它们不是本任务创建或修改的内容，不归入本任务。

本任务唯一新增文件为：

```text
docs/reports/V4-baseline-report.md
```

测试命令只产生被 `.gitignore` 排除的构建/运行产物；没有生产源文件、依赖清单、数据库或 Docker 配置变更。由于任务卡和本报告目前都是未跟踪文件，普通 `git diff --` 不展示未跟踪内容；应结合下列命令核验边界：

```powershell
git status --short --untracked-files=all
git diff -- backend-java/src python-agent/app frontend-vue/src `
  backend-java/pom.xml python-agent/requirements.txt frontend-vue/package.json `
  frontend-vue/package-lock.json docker-compose.yml docker
```

预期第二条命令无输出；状态清单中除用户既有 `docs/requirements/v4/**` 外，只应新增本报告。
