# R5-02: PromptVersion 不可变生命周期、ACTIVE 切换与快照验证

> 状态：`TODO`
>
> 前置任务：`R5-00`、`R5-01`
>
> 推荐模型：`gpt-5.5`
>
> 任务类型：版本治理 / 数据一致性测试

## 背景

R1 已有 PromptVersion 的基础表和 WorkflowRun 快照，但 R5 需要把它从“存在一个版本字段”升级为可审计、可切换、可比较的不可变实验对象。否则指标无法可靠归因。

## 目标

建立 PromptVersion 生命周期：

```text
PromptTemplate
-> create immutable PromptVersion N+1
-> validate output schema key/version and model parameters
-> activate one version for future runs
-> freeze version/content reference into WorkflowRun and AgentRun
-> archive, never overwrite historical content
```

提供最小的管理/查询服务与 API，供后续比较查询和受控实验使用。

## 范围

允许：

- 审查/完善 PromptTemplate、PromptVersion、WorkflowRun snapshot、AgentRun promptVersionId 的 migration、唯一约束和索引。
- 新增创建版本、激活版本、查询版本历史/详情的 Service/Controller/DTO/VO。
- 校验 agentType、递增 version、不可变字段、output schema key/version、model parameters 的合法性。
- 将新 WorkflowRun 和 AgentRun 明确关联到执行时的 PromptVersion；补充历史空引用的兼容读取规则。
- 新增并发 ACTIVE 切换、创建重复 version、运行中切换、旧运行查询、归档和权限测试。

## 非目标

- 不提供复杂富文本 Prompt 编辑器、多人审批流或可视化拖拽编辑。
- 不在运行中动态替换正在执行 Step 的 Prompt。
- 不直接实现 A/B 流量分配；本阶段先提供可比较的版本和归因数据。
- 不实现 R6 RAG 上下文模板或检索变量。
- 不删除旧 PromptTemplate 或篡改历史 PromptVersion。

## 约束

- `systemPrompt`、`userPromptTemplate`、schema key/version、model parameters 等版本内容创建后不可更新；修改只能新增版本。
- 每个 template/agentType 同一时刻仅一个 ACTIVE 版本，切换必须事务化并有并发保护。
- ACTIVE 切换只影响后续提交/Step；已创建 WorkflowRun、已开始 AgentRun 必须继续引用原快照。
- PromptVersion 内容可在有权限的诊断 API 中受控查看，但不得被普通运行页、日志或事件无条件暴露。
- 版本号、状态和快照之间的约束由数据库/服务层共同保护，不能只靠前端禁用按钮。

## 验收标准

- [ ] 创建 PromptVersion 后不可原地修改关键内容，修改尝试被拒绝或无更新路径。
- [ ] 同一模板版本号不会重复，ACTIVE 切换并发下不会出现两个有效 ACTIVE。
- [ ] 切换 ACTIVE 后新 WorkflowRun 选择新版本；旧 Run/AgentRun 保持原版本和内容快照。
- [ ] 归档版本不可被新运行选择，但历史查询仍可追溯。
- [ ] schema key/version 与 AgentType 不匹配时在创建/激活前被拒绝。
- [ ] R1 Snapshot、R3 异步提交和 R5-01 Metric 关联测试均通过。

## 验证命令

```powershell
cd backend-java
mvn -Dtest=*PromptVersion*Test,*WorkflowSnapshot*Test,*AsyncWorkflowSubmit*Test test
mvn test

cd ..
.\tools\verify.ps1 -Profile quick
.\tools\verify.ps1 -Profile integration
```

## 审查清单

- 是否把 PromptVersion 当可编辑记录，破坏历史可复现性。
- 是否 ACTIVE 切换后重写已创建 WorkflowRun 的 snapshot。
- 是否只用前端校验 version/active 唯一性。
- 是否让归档版本继续被新 Run 选择。
- 是否在 API/日志中无权限暴露完整 Prompt。

## 完成定义

- PromptVersion 成为不可变、可归因、可安全比较的实验单元。
- R5 指标与评测可稳定按 PromptVersion 聚合。
