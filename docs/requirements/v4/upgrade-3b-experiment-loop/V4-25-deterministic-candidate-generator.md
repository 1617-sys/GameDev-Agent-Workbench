# V4-25 Deterministic Candidate Generator

## 前置条件

V4-24 已通过人工 Review。

## 目标

实现受限、可重放的邻域候选生成器，作为 Upgrade 4 优化算法的正式基线，避免 Director 直接猜精确数值。

## 允许修改

- Java `experiment/candidate` 领域服务与 Tool Registry 接线
- 候选参数规则配置
- `backend-java/src/test/**` 对应测试

## 禁止修改

- 贝叶斯优化、强化学习或外部优化框架
- LLM 直接提供最终候选数值
- 修改 GameConfig 合法范围
- 无上限笛卡尔积生成

## 工作内容

- 注册 `GENERATE_NEIGHBOR_CANDIDATES`；
- 只支持 TunePrototypeVersionRequest 已允许参数；
- 根据目标方向、离散步长、边界和预算生成稳定候选集；
- 去重、排除与基线相同或非法候选；
- 候选计划保存 generator version、输入 digest 和顺序；
- 经 V4-24 工具创建不可变 DRAFT。

## 验收标准

- 相同输入产生相同有序候选及 digest；
- 数量不超过 Director 预算；
- 边界、互斥约束和无有效候选有明确结果；
- 每个候选可以追溯到目标、父版本和生成规则；
- 不调用模型。

## 必须执行

```powershell
cd backend-java
mvn test
```
