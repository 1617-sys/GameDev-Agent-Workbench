# V5 Agentic Mini-Game Factory 文档入口

> 状态：PRODUCT DESIGN
> 前置版本：[`v4.0.0`](../v4/README.md)
> 唯一活跃引擎：Cocos Creator 3.8 LTS
> 首个玩法切片：`arcade_collect`
> 首个构建目标：可下载、可在本地独立启动的 Cocos Web Mobile 游戏包

## 唯一主定位

> **Agent 生成受限 GameSpec，Java控制生成与验证闭环，Cocos Creator 构建高完成度、可在本地独立运行的小游戏包。**

V4 的 Director、Player、Simulation、实验和审计继续作为基座；Phaser 冻结为历史 Runtime，不再扩展。V5 不允许 Agent 直接修改 Cocos scene/prefab/meta 或生成可执行脚本，而是通过 GameSpec、人工维护的 Runtime Shell、Asset Pack 和表现预设实现规模化产出。

## 文档集合

- [主 PRD](game-generation-studio-prd.md)
- [GameSpec 语言契约](game-spec-language.md)
- [Java GameSpec 编译与控制平面](java-gamespec-compiler.md)
- [Cocos Runtime Target](cocos-runtime-target.md)
- [可玩产物契约](playable-artifact-contract.md)
- [V5 核心链路实施说明](implementation-core.md)
- [V6 小游戏平台边界](v6-mini-game-platforms.md)
- [ADR：Java 是生成正确性的权威层](../../architecture/decisions/ADR-001-java-gamespec-authority.md)

## 已冻结的产品决策

| 决策 | 结论 |
| --- | --- |
| 产品定位 | Agentic Mini-Game Factory |
| 游戏引擎 | Cocos Creator 3.8 LTS 是 V5 唯一活跃引擎 |
| Phaser | 保留 V4 历史代码和证据，V5 不继续开发 |
| 首个切片 | `arcade_collect`，必须显著提升素材、动画、反馈和完整 UI |
| Java/Cocos 边界 | Java 管领域状态、语义、工具、证据和门禁；Cocos 管运行、渲染和本地构建 |
| Agent 边界 | Python 规划和修复；不能直接编辑 Cocos 工程或运行构建器 |
| 生成方式 | 固定 Runtime Shell + GameSpec + Asset/Presentation Profiles，不生成任意源码 |
| 平台策略 | V5 只生成本地 Web Mobile 包；所有小程序/小游戏平台适配延后到 V6 |
| RAG | 后置辅助，必须带引用并做 on/off 评测 |
| 旧 API | V5 新入口稳定后再基于观测数据渐进弃用 |

## 实施批次原则

```text
v5/
├── batch-0-contracts/          # PRD、GameSpec、Cocos target、artifact 与 ADR
├── batch-1-cocos-shell/        # 固定游戏 CLI POC、Runtime Shell、Asset Pack 与 Web build
├── batch-2-java-control-plane/ # GenerationRun、语义校验、Capability、Tool Gateway
├── batch-3-first-playable/     # GameSpec → Cocos Web Mobile 完整切片
├── batch-4-agent-gates/        # 诊断修复、Simulation、Player、Visual Gates
├── batch-5-local-delivery/     # 本地 ZIP、独立启动、下载与 artifact lineage
└── batch-6-scale-proof/        # 第二 archetype，证明不是单模板重写
```

当前只批准产品与契约文档。本轮人工 Review 通过后再创建任务卡，并按以上批次放入独立文件夹。

## 真实性边界

- V4 是已发布事实；V5 是待实现设计。
- Cocos Creator 的跨平台能力不等于 V5 实现了任何小程序/小游戏平台适配。
- V5 的本地 Web Mobile 包不能称为微信、抖音、支付宝或其他平台开发包。
- 第二 archetype 成功前，不宣称“通用小游戏生成平台”。
- 视觉质量最终由人工试玩判断，不能由 Agent 自评替代。
