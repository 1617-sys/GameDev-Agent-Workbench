# R1 Workflow Domain Report

## 环境

- 日期：2026-07-11
- 分支：`codex/r1-workflow`
- Java：21
- Maven：3.9.11
- MySQL：本次验收未连接真实实例；Docker daemon 未启动。

## Migration 结果

| 文件 | 目的 | 验证 |
| --- | --- | --- |
| `V1__baseline.sql` | 固化现有六张基线表 | 离线 smoke；不含固定 schema 的 `CREATE DATABASE` / `USE` |
| `V2__add_workflow_definition_version.sql` | 定义版本与默认 `GAME_DESIGN` / `DEMO_GAME_CONFIG` | migration 与 ACTIVE 查询单测 |
| `V3__add_workflow_step_run.sql` | StepRun、Artifact 关联 | 成功、业务失败、异常路径单测 |
| `V4__add_prompt_version.sql` | PromptVersion V1 回填与不可变规则 | 唯一约束、回填保护、更新/删除阻止触发器单测 |
| `V5__extend_workflow_run_for_domain_snapshot.sql` | WorkflowRun 定义与 Prompt 标识快照 | 成功、历史空字段兼容、Prompt 切换快照单测 |

迁移命名连续，已提交的 V1–V4 未被修改。已有库以 Flyway baseline-on-migrate 从版本 1 建立历史记录，再顺序执行 V2–V5；空库策略与限制见 `docs/requirements/R1-flyway-baseline.md`。

## 领域模型结果

- Workflow 状态机：合法转换可测试；终态不能直接回到 `RUNNING`。
- StepRun：记录 UUID、attempt、状态、输入/上下文/输出快照与错误；可通过 WorkflowRun UUID 查询，Artifact 保存 `step_run_id`。
- PromptVersion：ACTIVE V1 从既有 ACTIVE 模板回填；服务仅提供读取，数据库阻止更新和删除。
- WorkflowRun Snapshot：新运行冻结定义 JSON、三个设计步骤的 PromptVersion 标识和 `game-config/1.0`；历史记录允许快照字段为 `NULL`。

## 验证命令

| 命令 | 结果 |
| --- | --- |
| `git diff --check` | PASS |
| `cd backend-java; mvn test` | PASS，36 tests |
| `./tools/verify.ps1 -Profile quick` | PASS：Java、Python 编译、Vue 构建、Compose 配置 |
| 弱默认值与 API key 扫描 | PASS：未发现命中 |

## 已知风险

- 真实 MySQL 空库与已有库迁移尚未在本机执行，因为 Docker daemon 未启动。该项属于发布前环境验证，不应通过修改已提交 migration 来规避。
- 现有同步 `WorkflowServiceImpl` 仍按旧硬编码步骤执行；R2 才抽取 Runner 并以冻结定义驱动执行。
- Vue bundle 大小告警与 R1 无关，未在本验收处理。

## R2 准入结论

**PASS（研发准入）**：R2 Runner 所需的定义版本、状态策略、StepRun、PromptVersion 与 WorkflowRun 快照地基均已存在且由测试覆盖。

发布或环境验收前必须在真实 MySQL 上执行一次空库和已有库迁移验证。
