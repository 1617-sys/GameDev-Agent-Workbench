# V4-26 Player Experiment Tools

## 前置条件

V4-25 已通过人工 Review。

## 目标

将现有 PlayerRun 和 MachineEpisode 能力注册为 Director 可用的异步实验工具，并实现确定性候选比较。

## 允许修改

- Director Tool Registry、Experiment 服务及关联测试
- PlayerRun/MachineEpisode 的最小只读或幂等适配
- Director 恢复唤醒逻辑

## 禁止修改

- 重写 Player 策略或 Simulation Core
- 混合机器与真人样本
- Director 忙轮询 PlayerRun
- 使用 LLM 判断客观指标是否达标

## 新增工具

- `RUN_PLAYER_EXPERIMENT`
- `GET_EXPERIMENT_STATUS`
- `COMPARE_CANDIDATE_METRICS`

## 工作内容

- 为基线与候选使用相同 Persona、seed、policy 和预算矩阵；
- PlayerRun 完成后唤醒 WAITING_EXPERIMENT DirectorRun；
- 比较完成率、耗时、失败率、动作效率及保护约束；
- 样本不足或版本混合时返回不可比较；
- 保存 metric version、样本窗口、输入 Episode 引用和比较 digest。

## 验收标准

- 重试不会重复提交相同实验批次；
- 比较结果可从 MachineEpisode 重算；
- 保护约束失败的候选不能成为推荐结果；
- 部分失败、超时、样本不足和无候选均有测试；
- Director 可以根据结构化比较结果选择继续、审批或失败。

## 必须执行

```powershell
cd backend-java
mvn test
```
