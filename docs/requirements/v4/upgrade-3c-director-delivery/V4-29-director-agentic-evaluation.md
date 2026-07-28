# V4-29 Director Agentic Evaluation

## 前置条件

V4-27 已通过人工 Review。

## 目标

建立固定对照实验，判断 Director + Tools 是否相对固定 Workflow 带来可量化价值。

## 允许修改

- `tools/director-evaluation/**`
- `docs/reports/V4-director-agentic-evaluation-report.md`
- 固定目标、地图、seed 和预算 fixture

## 禁止修改

- 本任务修改生产策略、工具或评测规则
- 删除失败目标或只展示最佳运行
- 将 fake/mock 模型结果描述为真实模型能力
- 将 RAG 或贝叶斯优化计入本阶段结论

## 对照组

- 现有固定 Workflow/规则式候选流程；
- Director + Tools，RAG off，使用相同候选生成器和实验预算；
- 可选真实模型组必须单独标注 provider/model/version。

## 指标

- 目标与保护约束同时达成率；
- 非法工具调用率；
- 无效/重复候选数；
- 平均轮数、Episode 数、延迟、token 和成本；
- 恢复成功率；
- 人工批准、拒绝和修改率；
- 每个结论的证据可追溯率。

## 验收标准

- 一条命令可重复运行固定目标集；
- 报告包含全部样本、版本、原始 JSON 和限制；
- mock、不可比较和样本不足明确标记；
- 即使 Director 不优于固定 Workflow，也保留真实结论并用于 Upgrade 4 决策。
