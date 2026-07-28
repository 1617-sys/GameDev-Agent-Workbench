# V4-03 Episode Protocol RFC

## 前置条件

V4-01 与 V4-02 已通过人工 Review。

## 目标

冻结 Episode、Step、批量运行、重放与持久化边界，避免 TypeScript Runner、Python Agent 和 Java 后端各自发明协议。

## 允许修改

- 新建 `docs/requirements/v4/upgrade-0-foundation/V4-episode-protocol.md`
- 新建不超过三个 JSON 示例到 `docs/requirements/v4/upgrade-0-foundation/examples/episode/`

## 只读参考

- `docs/requirements/v4/upgrade-0-foundation/V4-simulation-protocol.md`
- `backend-java/src/main/java/com/example/gameworkbench/entity/PlaytestSession.java`
- `backend-java/src/main/java/com/example/gameworkbench/entity/PlaytestEvent.java`
- `backend-java/src/main/java/com/example/gameworkbench/entity/PrototypeVersion.java`
- PRD 第 6.3、7、9 节

## 禁止修改

- 所有生产代码、迁移和测试
- 现有真人 PlaytestSession/Event 的语义

## RFC 必须定义

- `EpisodeRequest`、`EpisodeResult`、`EpisodeStep`、`EpisodeBatchResult`；
- prototype/config hash、simulation protocol、policy 和 persona 版本；
- seed、模型、token、成本、耗时及其可空规则；
- 机器 Episode 与真人 Playtest 的隔离与关联方式；
- replay 所需的最小不可变输入；
- 批量提交的幂等键、数量限制、错误和部分失败语义；
- 原始轨迹、聚合指标和审计元数据的保存边界。

## 验收标准

- 确定性策略和 LLM Player 使用同一结果协议；
- 单个失败 Episode 不使已完成结果丢失；
- 机器样本不能混入真人指标；
- 任一报告指标可以追溯到具体 Episode 和 PrototypeVersion。
