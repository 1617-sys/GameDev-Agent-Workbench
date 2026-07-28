# V4-13 Persona Policies

## 前置条件

V4-12 已通过人工 Review。

## 目标

在同一确定性策略框架上实现 `NOVICE`、`REGULAR`、`EXPERT` 三类可版本化、可复现的 Player Persona。

## 允许修改

- `python-agent/app/services/player/**`
- `python-agent/app/schemas/player.py`
- `python-agent/tests/test_player_personas.py`
- `python-agent/tests/fixtures/player/**`

## 禁止修改

- 仅通过 system prompt 区分 Persona
- 为保证预期排序而修改游戏规则或测试地图
- 混入 LLM 调用

## 工作内容

- 将视野、决策间隔、动作误差、目标记忆和规划能力显式参数化；
- Persona 随机误差使用独立 policy seed；
- 生成稳定 persona/policy digest；
- 固定地图和 seed 矩阵运行三类 Persona；
- 报告完成率、耗时、无效动作、路径效率和失败原因。

## 验收标准

- 三类 Persona 在固定评测集上呈现稳定且可解释的差异；
- 相同 Persona 与 seed 可重放；
- 测试不要求每个 seed 都严格排序，只要求聚合指标满足 RFC 阈值；
- 参数改变会导致 digest 改变。

## 必须执行

```powershell
cd python-agent
pytest
```
