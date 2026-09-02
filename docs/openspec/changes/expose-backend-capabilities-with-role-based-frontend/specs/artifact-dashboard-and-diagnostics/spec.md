## Purpose

将项目 Artifact、Agent 运行、汇总指标和系统诊断能力组织为受控视图，使用户和管理员能够发现已有后端证据，同时隔离非生产、旧版和内部操作。

## ADDED Requirements

### Requirement: 项目 Artifact 总览
项目用户 SHALL 能查看有权限的 Artifact 列表、类型、来源运行、摘要、资格状态和详情，并从运行与版本页面双向导航。

#### Scenario: 查看项目 Artifact
- **WHEN** 用户进入项目 Artifact 页面
- **THEN** 页面显示该项目的 Artifact 列表及分页、筛选、空状态和错误状态

#### Scenario: 猜测其他项目 Artifact UUID
- **WHEN** 用户请求无权访问的 Artifact
- **THEN** 后端拒绝请求且页面不泄露 Artifact 内容或归属

### Requirement: Dashboard 与 Agent 运行视图
管理员 SHALL 能查看项目运行汇总、Agent 类型汇总、Agent Run 列表和单次运行详情，并能按后端支持的条件筛选。

#### Scenario: 查看运行汇总
- **WHEN** 管理员进入 Dashboard
- **THEN** 页面展示后端返回的项目与 Agent 汇总并明确数据窗口和空状态

#### Scenario: 查看 Agent Run
- **WHEN** 管理员选择一个 Agent Run
- **THEN** 页面展示安全的状态、模型证据和关联对象，不展示密钥或完整敏感 Prompt

### Requirement: 诊断与兼容性工作区
管理员/诊断工作区 SHALL 显示健康状态、可用运行模式、非生产 Demo 状态、旧版 Workflow 接口和内部接口的生命周期分类；生产环境 MUST NOT 提供被禁用的非生产操作。

#### Scenario: 生产环境查看 Demo 能力
- **WHEN** 管理员在生产环境打开诊断页面
- **THEN** 页面将 Demo 标记为不可用且不展示可执行按钮

#### Scenario: 旧版接口存在替代路径
- **WHEN** 某旧版接口已由新版接口取代
- **THEN** 页面和覆盖矩阵显示 deprecated 状态、替代路径和移除条件

### Requirement: 危险操作保护
可能创建运行、修改模板、重试任务或触发外部模型调用的诊断操作 MUST 具备后端授权、幂等键、明确成本提示和确认步骤。

#### Scenario: 重复提交诊断操作
- **WHEN** 管理员因网络重试重复发送同一操作
- **THEN** 系统依照幂等契约复用结果或返回明确冲突，不产生重复副作用
