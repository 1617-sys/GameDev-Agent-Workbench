# R7-08: README、架构图、面试问答与简历投递材料

> 状态：`TODO`
>
> 前置任务：`R7-02`、`R7-03`、`R7-04`、`R7-05`、`R7-06`、`R7-07`
>
> 推荐模型：`gpt-5.4`（整理） / `gpt-5.5`（事实审查）
>
> 任务类型：项目交付文档 / 求职材料

## 背景

代码和报告如果不能让陌生人快速理解，就很难转化为求职价值。最终材料需要准确解释项目解决的问题、技术选择、并发可靠性、AI 工程、验证证据和个人贡献，同时避免夸大未实现能力。

## 目标

形成统一投递包：

```text
root README
architecture and core sequence diagrams
quick start + demo guide
test/performance/fault/security report index
AI collaboration workflow and pitfalls
interview Q&A
resume bullet variants
3-5 minute project narrative
```

## 范围

允许：

- 重写/完善根 README 的定位、架构、功能、技术栈、快速启动、Demo、测试、报告、已知限制和贡献说明。
- 新增/更新 Mermaid 架构图、提交/执行/SSE/RAG/评测时序图和数据关系图。
- 编写 `docs/interview-qa.md`、`docs/resume-project-description.md`、项目讲解提纲和报告导航。
- 将 R0-R7 的关键 commit、测试数量、并发/性能/故障数据和截图链接到可验证来源。
- 审查所有描述与当前代码/报告一致，删除历史 MVP/玩具式过时表述和未实现承诺。

## 非目标

- 不捏造用户量、线上收入、真实生产规模、模型效果、性能数字或个人职责。
- 不复制整个任务卡到 README，不让首页变成内部开发日志。
- 不添加新业务功能或为截图重写 UI。
- 不泄露公司 JD、个人隐私、API Key、内部绝对路径或敏感日志。
- 不把 AI 生成代码描述成未经审查的纯个人手写成果。

## 约束

- 每个技术亮点都能指向代码、测试或报告；无法证明的描述改为“设计/限制/后续计划”。
- README 第一屏说明项目是什么、解决什么问题、如何运行，不先堆工具名。
- 架构图反映实际边界：Java 状态/消息、Python Agent/RAG、Vue/SSE/Phaser、MySQL/Redis/RabbitMQ。
- 面试问答包括“为什么不用”“失败时怎样”“如何验证”“AI 做了什么、你审查了什么”，不只背概念。
- 简历描述使用量化但真实的测试/性能/故障数据，并准备 1 行、3 行和详细版。
- 所有本地文件链接、命令和截图在仓库相对路径/公开环境可用。

## 验收标准

- [ ] 陌生开发者可从 README 理解定位、架构、启动、主链路、验证证据和限制。
- [ ] 架构图与代码职责、消息链路、RAG/评测链路一致，无不存在的服务。
- [ ] 并发、性能、故障、安全数据均链接到真实报告和复现命令。
- [ ] 面试问答覆盖 Java、MySQL、Redis、RabbitMQ、事务/幂等、SSE、RAG、评测和 AI 协作。
- [ ] 简历描述没有夸大生产规模，能够说明个人设计、审查、验证和沉淀责任。
- [ ] 文档链接、命令、拼写、敏感信息和过时内容检查通过。

## 验证命令

```powershell
git diff --check
rg -n "TODO|TBD|localhost-only|sk-|password|secret" README.md docs
.\tools\verify.ps1 -Profile quick
```

## 审查清单

- 是否堆砌中间件名而没有业务问题和验证证据。
- 是否写了未实现/无法复现/夸大的性能和生产结论。
- 是否 README 过长却没有快速启动和主链路。
- 是否面试问答只能解释概念，不能解释代码取舍和失败边界。
- 是否链接、命令、截图或路径只在作者电脑可用。

## 完成定义

- 项目从代码仓库升级为陌生人可运行、面试官可验证、你自己可讲清楚的作品集项目。
- 所有求职表述都有真实工程证据支撑。
