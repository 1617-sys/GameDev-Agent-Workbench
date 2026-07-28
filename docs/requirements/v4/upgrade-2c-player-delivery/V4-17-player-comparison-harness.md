# V4-17 Player Comparison Harness

## 前置条件

V4-13 与 V4-16 已通过人工 Review。

## 目标

建立固定、可重复的 Player 对照实验，证明 Persona 差异并诚实比较确定性 Player 与 LLM Player。

## 允许修改

- `tools/player-evaluation/**`
- `docs/reports/V4-player-evaluation-report.md`
- 专用固定 fixture
- 为调用正式 API 所需的最小脚本配置

## 禁止修改

- 为提高结果修改策略、Runtime 或生产配置
- 只报告最好 seed 或删除失败样本
- 将 mock LLM 计入真实模型指标

## 工作内容

- 冻结地图、PrototypeVersion、seed、Persona、Policy、模型和预算矩阵；
- 比较 deterministic neutral、三类 Persona 和 LLM Player；
- 输出完成率、P50/P95 时间、动作效率、非法动作、失败原因、token、成本和延迟；
- 验证部分 Episode replay；
- 生成原始 JSON、汇总和 Markdown 报告。

## 验收标准

- 一条命令可重复运行；
- 报告写明样本数、版本、失败与置信限制；
- 所有汇总能追溯到持久化 Episode；
- 即使 LLM 不优于基线也必须保留真实结论。
