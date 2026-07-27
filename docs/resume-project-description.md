# 简历项目描述：GameDev Agent Workbench

以下内容只使用仓库可验证事实。投递前按自己的实际参与范围删改“设计、负责、主导”等词，不添加线上用户、收入、生产 QPS 或没有评测数据支持的模型效果。

## 推荐项目名称

**LLM 驱动的可玩原型工作流与平衡实验平台**

不推荐使用“通用 AI 游戏生成平台”“自主多 Agent 平台”或“生产级 RAG 平台”。

## 一行版

设计并审查 Spring Boot + FastAPI + Vue/Phaser 的受控 LLM 工作流，将单玩法 Brief 转换为可校验 GameConfig 和不可变可玩版本，并以 Outbox/RabbitMQ、幂等消费、SSE 恢复、试玩遥测与确定性导出建立可追溯闭环。

## 三行版

- 将游戏原型生成拆为冻结定义驱动的四步 LLM Workflow，持久化 Run/Step/Agent/Metric/Evaluation/Artifact，以 MySQL 短事务、Outbox、RabbitMQ Consumer、Redis owner lock 和恢复审计处理异步与重复执行边界。
- 建立 GameConfig 2.0 Schema/Rule/capability 门禁和不可变 PrototypeVersion；试玩事件绑定版本并由服务端复算指标，支持白名单调参、版本比较、平衡建议与确定性离线 ZIP 导出。
- 实现项目隔离的知识文档、RAG-on/off 协议和实际引用 provenance；当前 embedding/vector search 为确定性测试桩，正在升级为持久化语义检索与固定数据集对照评测。

## 详细职责候选

- 设计 WorkflowDefinitionVersion、WorkflowRun、WorkflowStepRun、PromptVersion、AgentArtifact 和 PrototypeVersion 的快照关系，使历史运行不受 ACTIVE 配置变化影响。
- 设计异步提交边界：HTTP 事务只创建幂等 WorkflowRun 与 Outbox intent，模型 I/O 由 RabbitMQ Consumer 驱动；使用 durable claim、状态版本、owner lock 和恢复 audit 控制重复执行与失败恢复。
- 设计 Run Center 的服务端 read model、持久化事件 sequence 与 SSE 重连；前端以服务端快照为事实源，支持取消、重试和断线恢复。
- 将模型输出限制为白名单 GameConfig 数据，在 Java、Python 和 Vue 三端维护契约；非法输出不得进入 Phaser Runtime，也不执行模型生成的 JavaScript/HTML。
- 建立版本绑定的试玩事件和服务端指标复算；调参创建不可变子版本，导出冻结 Artifact、资源、遥测摘要和建议，并使用摘要验证确定性。
- 实现 KnowledgeDocument/Chunk 生命周期、项目隔离、RAG 状态和 RetrievalRecord provenance；明确 fake embedding/in-memory vector 的测试边界，不把接口桩包装成语义检索效果。
- 使用自动化测试和 Harness 验证状态机、幂等、消息、恢复、安全、Schema、Runtime、遥测和导出；区分真实执行、mock 和环境 skip。

## 当前可验证数据

| 数据 | 当前证据边界 |
| --- | --- |
| Java 182 项测试通过，1 项跳过 | 本地 `mvn test`；不等价于完整 Docker/Testcontainers E2E |
| Python 21 项测试通过 | 本地 `python -m pytest -q`；包含 1 个依赖弃用 warning |
| 前端 32 项单测通过并完成生产构建 | Phaser 预览 chunk 约 1.5 MB，仍有大包警告 |
| V3 单机 Compose 主链路验收通过 | 仅限 `arcade_collect`、固定 Runtime 和当时验收环境 |
| 确定性 Prototype ZIP | 表示冻结输入可复现，不表示通用游戏工程导出 |

数字会随代码演进变化；投递前应重新运行测试并更新本表。

## RAG 在简历上的安全表述

当前可以写：

> 建立项目级知识文档、Chunk、RAG-on/off 协议和实际引用 provenance，为后续替换真实 embedding/vector backend 保留可追溯数据模型与对照评测接口。

真实向量实现和评测完成后，才可以写：

> 基于固定数据集评估语义检索 Recall@K/MRR，并对比 RAG-on/off 的约束满足率、延迟、token 和成本。

在数据出现前不得写“RAG 显著提升生成质量”。

## 不应写入简历

- “自主多 Agent”：当前是固定四步串行 LLM Workflow，没有动态规划、工具调用、反思或 Agent 协商。
- “生产级 RAG”：当前 fake embedding 为 8 维字符哈希，进程内 vector search 不计算真实相似度。
- “通用游戏生成”：当前只支持 `arcade_collect`，本质是受约束的参数化原型。
- “高并发/生产 SLA”：缺少可发布的真实容量与线上数据。
- “完整安全合规”：当前是个人单机 Compose 工程，不是经过正式合规审计的 SaaS。
- “全部代码独立手写”：项目采用 AI 辅助流程，应说明自己的决策、审查和验证责任。

## 面试展开顺序

1. 用一句话给出准确产品定位和单玩法边界。
2. 展示 Brief → Workflow → GameConfig → Version → Telemetry → Suggestion → Export。
3. 深挖 Outbox + 幂等 Consumer，或不可变版本 + 服务端遥测复算。
4. 主动说明当前 RAG 是协议桩，并给出真实检索和评测路线。
5. 展示测试、历史 V3 验收和当前未完成项，不用基础设施数量代替业务价值。

完整讲解见[项目叙事](project-narrative.md)，升级计划见[B 路线与 RAG 路线图](roadmap-balance-lab-rag.md)。
