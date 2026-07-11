# R1-05：PromptVersion 不可变版本与 V1 回填

> 状态：`DONE`
>
> 前置任务：`R1-01`
>
> 推荐模型：`gpt-5.5`
>
> 任务类型：数据库迁移 / Prompt 版本治理

## 背景

当前 `prompt_template` 主要表达当前模板，历史 AgentRun 难以追踪当时使用的完整 Prompt。R1 需要建立不可变 PromptVersion，为 R5 的评测和 Prompt 实验做地基。

## 目标

实现：

```text
prompt_template 保留
+ prompt_version 新增
+ 当前 ACTIVE 模板生成 version 1
+ AgentRun / WorkflowRun 可保存 prompt_version 引用或快照字段
```

## 范围

允许：

- 新增 Flyway migration。
- 新增 PromptVersion Entity/Mapper。
- 为已有 PromptTemplate 初始化 version 1。
- 增加不可变规则测试。
- 增加查询 ACTIVE PromptVersion 的方法。
- 更新必要文档。

## 非目标

- 不做 Prompt A/B Dashboard。
- 不实现 Prompt 在线编辑 UI。
- 不接模型调用成本统计。
- 不接评测报告。
- 不修改 Python Agent Prompt 渲染协议，除非为了保存版本引用的最小字段。
- 不删除旧 PromptTemplate。

## 约束

- PromptVersion 创建后不可修改。
- 切换 ACTIVE 只影响之后创建的 WorkflowRun。
- 历史运行必须能追踪当时使用的 PromptVersion。
- V1 回填必须可重复执行或有唯一约束保护。
- 不把真实 Prompt API Key 或密钥写入表或日志。

## 验收标准

- [ ] `prompt_version` migration 存在。
- [ ] 现有 ACTIVE prompt template 可生成 version 1。
- [ ] 同一 template 下 version 唯一。
- [ ] ACTIVE PromptVersion 查询有测试。
- [ ] 尝试修改已发布 PromptVersion 被拒绝，或代码路径不提供修改能力并有测试证明。
- [ ] quick Harness 通过。

## 验证命令

```powershell
cd backend-java
mvn -Dtest=*Prompt*Test test
mvn test

cd ..
.\tools\verify.ps1 -Profile quick
```

## 审查清单

- 是否把 PromptVersion 做成可覆盖更新。
- 是否修改 ACTIVE 后影响历史运行。
- 是否没有唯一约束导致重复 version。
- 是否把 R5 的评测和成本统计提前塞进本任务。
- 是否日志输出完整 Prompt。

## 完成定义

- PromptVersion 可创建、查询、回填。
- 不可变规则被测试保护。
- 旧 PromptTemplate 仍兼容。
