# 3–5 分钟项目讲解提纲

## 0:00–0:35：一句话定位

“这是一个面向单一 `arcade_collect` 玩法的 LLM 可玩原型与平衡实验平台。它不生成任意游戏代码，而是把 Brief 编译成严格 GameConfig，通过固定 Phaser Runtime 试玩，再把版本、遥测、建议和导出串成可审计闭环。”

主动说明边界：当前是固定四步 LLM Workflow，不是自主多 Agent；RAG 的协议和引用证据存在，但语义检索实现仍是 fake/in-memory 基线。

## 0:35–1:15：用户闭环

演示以下主线：

```text
创建项目与填写 Brief
→ 提交异步生成
→ 查看四步运行状态
→ 校验 GameConfig 并试玩
→ 创建不可变调参版本
→ 采集并由服务端复算试玩指标
→ 生成平衡建议
→ 比较版本并导出离线包
```

强调最终可玩物是受白名单约束的数据，不执行模型生成的代码。当前公开 Demo 还不能让外部测试者贡献匿名遥测，这是下一阶段首先要补的产品断点。

## 1:15–2:05：两个最强工程亮点

亮点一：**可靠执行边界**。

“HTTP 事务只创建幂等 WorkflowRun 和 Outbox；RabbitMQ Consumer 领取执行；数据库状态和唯一约束是正确性底线，Redis 锁只保护昂贵执行窗口；失败通过分类、重试、heartbeat 和恢复审计留下证据。”

亮点二：**不确定输出到确定产物**。

“每次运行冻结 Workflow、Prompt、输入和 RAG 状态。GameConfig 经过 Schema、业务规则和 Runtime capability 门禁，成功后创建不可变 PrototypeVersion。试玩事件绑定准确版本，调参永远派生子版本，导出使用冻结输入并记录摘要。”

## 2:05–2:50：RAG 怎么讲才可信

“我已经实现项目级文档生命周期、Chunk、RAG-on/off 状态、实际 `used_references` 回传和 RetrievalRecord provenance，因此能证明某次运行声明使用了哪些文档版本。但当前 embedding 是 8 维确定性 fake，进程内 vector store 不计算真实语义相似度，所以它是检索协议桩，不是生产级 RAG。”

然后说明升级路径：真实 embedding + pgvector/Qdrant、正确 PDF 提取、持久化索引任务、固定评测集，以及 RAG-on/off 的检索质量、约束满足率、延迟、token 和成本对照。

不要说“RAG 已提升模型效果”，除非未来评测报告给出同条件数据。

## 2:50–3:35：为什么不扩三个 Runtime

“当前最有价值的不是模板数量，而是版本化实验事实链。增加多个半成品 Runtime 会同时扩大 Schema、运行时、遥测和测试矩阵，却不能证明用户价值。因此下一阶段选择把单玩法做成真正的平衡实验台：公开分享试玩、匿名遥测、样本进度、建议依据、一键候选版本和 A/B 对比。”

这不是降低目标，而是把项目从功能展示推进到可验证实验系统。

## 3:35–4:15：验证与诚实边界

可引用当前本地验证：

- Java 182 项测试通过，1 项跳过；
- Python 21 项测试通过；
- 前端 32 项单测通过；
- 前端生产构建通过，但 Phaser chunk 仍有体积警告；
- V3 历史 Compose 主链路验收通过；本次 Review 环境因 Docker daemon 未运行，没有重跑完整 Compose E2E。

明确区分单元测试、历史报告和本次实际运行，不把环境 skip 写成成功。

## 4:15–4:45：求职落点

“这个项目目前最能证明的是 Java 侧可靠 LLM Workflow：幂等、Outbox、消息消费、恢复、SSE、契约校验、版本冻结和可观测性。AI 侧我会保守描述为 OpenAI-compatible LLM 接入、结构化产物和 RAG 协议；真实语义检索和质量评测是下一阶段工作。”

推荐标题：**LLM 驱动的可玩原型工作流与平衡实验平台**。

## 高风险追问入口

- 为什么 RabbitMQ + Outbox 仍然是 at-least-once？见[面试问答](interview-qa.md#4-rabbitmq--outbox-解决了什么)。
- 为什么当前不能称生产级 RAG？见[面试问答](interview-qa.md#7-rag-当前到底实现到了什么程度)。
- 如何证明 RAG 有价值？见[面试问答](interview-qa.md#8-准备如何证明-rag-确实有价值)。
- 为什么坚持单玩法？见[面试问答](interview-qa.md#10-为什么不继续增加多个-runtime)。
- AI 辅助开发中个人贡献是什么？见[面试问答](interview-qa.md#12-ai-在项目里做了什么你承担什么责任)。
