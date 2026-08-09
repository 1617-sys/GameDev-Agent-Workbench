# V5 核心链路实施说明

> 状态：CORE IMPLEMENTED / COCOS 3.8.8 BUILD VERIFIED

当前仓库已经实现 V5 的 Java 权威核心，不再把 GameConfig 或 Phaser 页面当作 V5 可玩产物。

## 已实现

- 封闭的 `arcade_collect/1` Capability Registry；
- GameSpec 0.1 解析、封闭字段校验、引用/范围/终局检查和稳定 JSON Pointer 诊断；
- canonical GameSpec、`cocos-runtime-ir/1` 和 `cocos-build-request/1` 的确定性摘要；
- 项目隔离、幂等的 `GenerationRun` 持久化与状态版本；
- Director 受控工具 `GET_GAMESPEC_CAPABILITIES@1`、`COMPILE_GAME_SPEC@1`；
- 受控 Windows Cocos Build Worker：固定 `web-mobile`、隔离工作目录、超时、日志与输出摘要，未配置时 fail closed；
- Local Playable Artifact 封包、来源文件、manifest、内容 hash、可信 PowerShell 本地启动器和下载时摘要复验。

权威合法 fixture 位于：

```text
backend-java/src/main/resources/gamespec/arcade-collect-valid.json
```

## API

```text
GET  /api/v5/gamespec/capabilities
POST /api/v5/projects/{projectUuid}/gamespec/compile
POST /api/v5/projects/{projectUuid}/generation-runs
GET  /api/v5/projects/{projectUuid}/generation-runs/{runUuid}
POST /api/v5/projects/{projectUuid}/generation-runs/{runUuid}/build?expectedVersion=0
GET  /api/v5/projects/{projectUuid}/generation-runs/{runUuid}/artifact
```

创建 GenerationRun 必须传 `Idempotency-Key`。规格非法时，Run 以 `FAILED` 持久化并保留结构化 diagnostics；合法时进入 `BUILDING`，等待真实 Cocos Worker。

## 前端生成台

`/projects/{projectUuid}/studio` 已升级为 Cocos V5 Generation Studio，提供：

- 读取 Capability Registry 并展示锁定的 GameSpec、Cocos 和构建目标版本；
- 受约束的世界、玩家与实体配置器，以及可直接编辑的 GameSpec JSON；
- 服务端编译、JSON Pointer diagnostics 与 canonical/runtime 摘要展示；
- 幂等创建 GenerationRun、长超时真实构建、失败后按状态版本重试；
- 构建流水线、持久化摘要、状态刷新和 Cocos ZIP 产物下载。

原有自然语言 `GAME_GENERATE` 前端保留在 `/projects/{projectUuid}/idea-studio`，仅作为旧版兼容入口，不再是项目默认创作台。

## Cocos 环境

必须显式配置，系统不会回退到 Phaser 或伪造构建成功：

```dotenv
COCOS_CREATOR_EXECUTABLE=C:\\ProgramData\\cocos\\editors\\Creator\\3.8.8\\CocosCreator.exe
COCOS_RUNTIME_PROJECT=F:\\coe\\java\\GameDev Agent Workbench\\cocos-runtime-shell
COCOS_BUILD_WORK_ROOT=F:\\coe\\java\\GameDev Agent Workbench\\tmp\\cocos-build-work
COCOS_ARTIFACT_ROOT=F:\\coe\\java\\GameDev Agent Workbench\\tmp\\playable-artifacts
COCOS_SUCCESS_EXIT_CODES=36
```

目标编辑器 patch 版本已锁定为 Cocos Creator `3.8.8`。本机由 Cocos Dashboard 2.2.1 下载的官方 Windows 包 `CocosCreator-v3.8.8-win-121518.zip` 的 SHA-256 为 `e365030aa4f24b515f499cf093cd86fdf38a0f763b5fbcacb20e96253e0fcc0b`。

2026-08-06 在上述安装和仓库 Runtime Shell 上完成两次真实 `web-mobile` 构建。复验构建退出码为官方成功码 `36`，生成 29 个文件、共 3,465,719 bytes，包含 `index.html`、运行时代码、场景、resources bundle 和引擎 bundle。按相对路径排序的 `path + SHA-256` 清单摘要为 `8538d29ffed56be9a1b5d1aa26558c3a2f30f266e4ead486a2997145df2100e1`。本机静态 HTTP 验收中，入口、settings 和 main runtime bundle 均返回 200，bundle 内存在 `RuntimeShell` 标记。构建日志以 `build Task (web-mobile) Finished` 结束；生成目录仅作本机验收证据，不纳入源码版本控制。

## 当前停止线

首个 Runtime Shell 与真实 CLI 构建已经可用。首套正式美术 Asset Pack 完成前，仍不继续扩展第二 archetype、平台 adapter、复杂 RAG 或视觉评分。
