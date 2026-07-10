# R0 验收：Baseline 总验收

> 状态：`DONE`
>
> 前置任务：`R0-02B`、`R0-03`、`R0-04`、`R0-05`
>
> 推荐模型：`gpt-5.4`
>
> 任务类型：阶段验收与证据整理

## 背景

R0 的目的不是增加 RabbitMQ、RAG 或新页面，而是让现有项目拥有可信 Baseline：

- 一个统一验证入口。
- Redis 锁缺陷被测试和修复。
- Workflow 当前行为被测试保护。
- GameConfig 契约可执行。
- 基础配置不再依赖弱 Secret。

本任务只验证和整理证据，不继续增加功能。

## 目标

证明项目达到：

```text
当前主链路可以构建和测试
+ 已知高风险锁缺陷已修复
+ 后续重构拥有回归保护
+ 配置满足基础安全要求
+ Git 改动可以被解释
```

## 输入材料

- `tools/verify.ps1`
- `R0-02A` 失败证据
- `R0-02B` 修复结果
- Workflow 状态测试
- GameConfig 契约测试
- 安全配置审查结果
- 当前 `git status` 和 `git diff`

## 范围

允许：

- 运行全部 R0 验证。
- 检查 Git diff 和未跟踪文件。
- 新增 `docs/reports/R0-baseline-report.md`。
- 在报告中记录警告、风险和后续任务。
- 修正文档中的错误命令或失效链接。
- 整理建议的 commit 分组，但不自动提交。

## 非目标

- 不新增业务功能。
- 不接 RabbitMQ。
- 不引入 Flyway。
- 不抽取 Workflow Runner。
- 不优化 Vue bundle。
- 不修复 R1 以后问题。
- 不自动删除或还原历史修改。
- 不在未经用户确认时创建 baseline commit。

## 约束

- 所有结论必须来自真实命令输出。
- 不得把 warning 描述为 error，也不得隐藏 warning。
- 不得声称未运行的测试已经通过。
- 当前工作区已有大量修改，必须区分 R0 改动与此前改动。
- 不使用 `git reset --hard`、`git checkout --` 等破坏性命令。
- 不把真实 `.env`、构建产物或 IDE 文件纳入提交建议。

## 验收项目

### Harness

- `tools/verify.ps1 -Profile quick` 返回 0。
- 任一命令失败时 Harness 返回非 0。
- 输出可以定位失败模块。

### Java

- Maven 测试通过。
- Redis 锁测试通过。
- Workflow 状态测试通过。
- `pom.xml` 不再有 Redis Starter 重复声明警告。

### Python

- `python -m compileall app` 通过。
- 没有因 R0 引入新的接口漂移。

### Vue/GameConfig

- GameConfig 契约测试通过。
- `npm run build` 通过。
- Bundle 大小 warning 如仍存在，记录到 R4，不在 R0 修复。

### Compose/配置

- `docker compose config --quiet` 通过。
- 仓库中没有真实 Secret。
- 开发、测试和 Docker 配置边界已记录。

### Git

- R0 新增和修改文件可以逐项解释。
- 未误改历史删除或其他用户修改。
- 提交分组建议清楚。

## 验收标准

- [ ] 所有前置任务已完成。
- [ ] quick Harness 返回 0。
- [ ] Redis Lock、Workflow 和 GameConfig 目标测试通过。
- [ ] 配置与 Secret 检查完成。
- [ ] `git diff --check` 通过。
- [ ] 生成 `R0-baseline-report.md`。
- [ ] 报告包含命令、结果、已知 warning、剩余风险和 R1 准入结论。
- [ ] 没有在验收任务中顺手实现新功能。
- [ ] 没有自动提交混杂的工作区改动。

## 验证命令

```powershell
git status --short
git diff --check

.\tools\verify.ps1 -Profile quick

cd backend-java
mvn -Dtest=DemoStreamServiceImplTest,RedisServiceImplTest,WorkflowServiceImplTest test

cd ..\frontend-vue
npm run test:game-config
npm run build

cd ..
docker compose config --quiet
```

## Baseline 报告模板

```markdown
# R0 Baseline Report

## 环境
- 日期：
- 分支：
- Java/Maven/Python/Node/Docker：

## 验证结果
| 检查 | 命令 | 结果 | 耗时 |

## 修复问题
- Redis 锁：
- Workflow 测试：
- GameConfig 契约：
- 安全配置：

## 已知警告
- 警告：
- 归属阶段：

## Git 范围
- R0 文件：
- 既有文件：
- 不应提交：

## R1 准入结论
- PASS / BLOCKED
- 原因：
```

## 审查清单

- 报告是否与终端真实输出一致。
- 是否遗漏失败测试或 warning。
- 是否把旧工作区改动误算为 R0。
- 是否出现任何 Secret。
- 是否存在无法解释的文件改动。
- R1 准入结论是否有证据。

## 完成定义

- 所有验收标准完成。
- Baseline 报告已保存。
- R0 的 P0 问题没有未记录项。
- R1 准入结论明确。
- commit 分组已建议但未擅自执行。
- 你能够用自己的话说明 R0 为什么是后续重构的地基。

