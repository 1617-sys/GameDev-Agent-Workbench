# R7-00: 最终发布、验证与投递契约冻结

> 状态：`TODO`
>
> 前置任务：`R6-验收`
>
> 推荐模型：`gpt-5.5`
>
> 任务类型：发布设计 / 只写文档

## 背景

R0-R6 已完成领域模型、Runner、消息可靠性、运行中心、评测指标和 RAG。R7 不再增加新的核心产品能力，而是验证整个系统在新环境、并发、依赖故障和演示条件下是否真的成立，并把工程证据整理成可投递作品。

## 目标

新增 `docs/requirements/r7/R7-release-hardening-design.md`，冻结最终交付矩阵：

```text
fresh environment bootstrap
-> full E2E
-> concurrency/performance baseline
-> dependency fault injection/recovery
-> observability/security audit
-> reproducible demo
-> README/architecture/interview/resume package
-> final acceptance
```

文档必须定义环境、数据、性能指标、故障场景、报告格式、通过阈值、证据保存路径和最终版本标记策略。

## 范围

允许：

- 阅读 R0-R6 报告、Harness、Docker Compose、架构文档、测试和现有演示数据。
- 新增发布设计文档、验收矩阵、任务依赖图、风险/回滚表和证据目录约定。
- 明确后续 R7 子任务的允许目录、运行时间、环境要求、推荐模型和完成定义。

## 非目标

- 不新增新的工作流类型、模型 Provider、RAG 算法、游戏类型或业务模块。
- 不以 R7 为名重写 R0-R6 已通过的架构。
- 不执行生产发布、云资源采购或真实用户数据迁移。
- 不把录屏、README 或简历文字代替自动化测试证据。
- 不追求无法说明业务意义的极限压测数字。

## 约束

- 所有结论必须关联命令、配置、commit、环境和报告；“本机跑过一次”不算最终证据。
- 测试默认使用 mock/fake 或受控 Provider，不以付费模型和个人密钥作为唯一前提。
- 性能、并发和故障报告必须区分系统瓶颈、外部 Provider 限制和测试环境限制。
- R7 只修复阻断发布的最小缺陷；发现结构性问题应回到对应 R 阶段任务卡和测试，不在收尾阶段堆补丁。
- 最终文档必须能让新用户和面试官理解系统，而不是要求他们先阅读全部内部任务卡。

## 验收标准

- [ ] 文档覆盖 fresh start、E2E、并发性能、故障注入、可观测性、安全、演示和投递材料。
- [ ] 每类验证有环境、输入、通过阈值、失败证据、报告路径和回滚方式。
- [ ] 明确 R7 不增加核心功能及发现回归时的归属阶段。
- [ ] 明确最终 tag/release candidate、commit 冻结和验收报告流程。
- [ ] 明确真实模型与 mock/fake 演示/测试的区别和标记要求。

## 验证命令

```powershell
git diff --check
rg -n "fresh|E2E|concurrency|performance|fault|observability|security|demo|release" docs\requirements\r7\R7-release-hardening-design.md
```

## 审查清单

- 是否把新功能开发混进发布收尾。
- 是否没有量化阈值，只写“验证通过”。
- 是否将 mock 演示伪装成真实模型能力。
- 是否报告不可复现或未关联 commit/环境。
- 是否让文档材料替代真实 Harness。

## 完成定义

- 最终发布与作品投递的验证范围、证据和阈值已经冻结。
- R7-01 至 R7-08 可以按依赖顺序独立执行和验收。
