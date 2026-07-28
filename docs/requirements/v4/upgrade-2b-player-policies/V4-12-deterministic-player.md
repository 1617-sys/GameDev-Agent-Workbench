# V4-12 Deterministic Player Baseline

## 前置条件

V4-11 已通过人工 Review。

## 目标

实现一个能逐步观察并完成固定 `arcade_collect` 关卡的确定性 Player，作为 LLM Player 的最低对照基线。

## 允许修改

- `python-agent/app/services/player/**`
- `python-agent/app/schemas/player.py`
- `python-agent/tests/test_deterministic_player.py`
- 小型固定测试 fixture

## 禁止修改

- Simulation Service、Java 和 UI
- 调用 LLM
- 硬编码 fixture 的完整动作序列或坐标答案
- 引入通用强化学习框架

## 工作内容

- 实现 `observe → decide → act → feedback` 循环；
- 实现基于 Observation 的目标选择、路径/避障和出口决策；
- 使用 `baseline-neutral/1.0` Policy/Persona 引用；
- 严格限制 decision、step、wall time 和 restart 预算；
- 输出符合 Episode Protocol 的轨迹与 usage=`NOT_APPLICABLE`。

## 验收标准

- 在固定小型地图与 seed 集上达到 RFC 规定的基线完成率；
- 隐去完整状态时只使用 Observation 可见信息；
- 同一输入重复运行 Action 与终态一致；
- 失败能够归类为环境、策略、预算或协议错误。

## 必须执行

```powershell
cd python-agent
pytest
```
