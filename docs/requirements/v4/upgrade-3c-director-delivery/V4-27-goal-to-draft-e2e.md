# V4-27 Goal-to-DRAFT E2E 与恢复

## 前置条件

Upgrade 3B 已通过人工 Review。

## 目标

验证从结构化设计目标到候选 DRAFT、机器试玩、比较和人工审批等待的完整闭环及故障恢复。

## 允许修改

- `tools/director-e2e/**`
- 专用 Docker Compose/fixture/fake Director
- 必要的 E2E 测试接线
- `docs/reports/V4-director-e2e-report.md`

## 禁止修改

- 为通过 E2E 修改生产决策或降低权限门禁
- 依赖真实收费模型作为唯一测试路径
- 自动执行人工批准

## 场景

- 正常：目标 → 基线 → 候选 → PlayerRuns → 比较 → WAITING_APPROVAL；
- 人工批准与拒绝分别验证；
- Python 超时后重试；
- PlayerRun 部分失败；
- Java 在工具成功后、checkpoint 前重启；
- 重复消息和重复审批；
- 预算耗尽、取消和跨项目攻击。

## 验收标准

- 一条命令可启动、执行、验证并清理 fixture；
- 故障恢复不产生重复 DRAFT、PlayerRun 或 Episode；
- 最终状态、工具调用和审批均可由数据库事实证明；
- E2E 报告记录 commit、镜像、协议、输入 digest 和原始证据位置。
