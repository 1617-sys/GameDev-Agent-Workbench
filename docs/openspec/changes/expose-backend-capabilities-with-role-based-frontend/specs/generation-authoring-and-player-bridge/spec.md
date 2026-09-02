## Purpose

让用户明确控制 GameSpec 是从零生成还是基于当前规格修改，并将 Cocos 生成产物、原型版本、Player Run、Episode 与人工证据连接成可追踪的产品流程。

## ADDED Requirements

### Requirement: 明确的创作模式
GameSpec 创作界面 SHALL 提供“从零生成”和“修改当前规格”两个互斥模式，并按模式发送不同的 `currentSpec` 语义。

#### Scenario: 从零生成
- **WHEN** 用户选择“从零生成”并提交合法创意
- **THEN** 前端发送空的 `currentSpec`，结果不被默认示例规格隐式约束

#### Scenario: 修改当前规格
- **WHEN** 用户选择“修改当前规格”并提交修改说明
- **THEN** 前端发送编辑器中经过解析的当前 GameSpec，并在解析失败时阻止请求

### Requirement: 生成产物到原型版本的可追踪转换
系统 SHALL 允许符合条件的已批准或已发布 GenerationRun 通过幂等操作创建或关联 PrototypeVersion，并展示来源摘要和状态。

#### Scenario: 创建原型版本
- **WHEN** 用户从符合条件的 Cocos 生成产物选择“创建原型版本”
- **THEN** 系统返回关联版本并记录 GenerationRun、产物摘要和版本摘要之间的来源关系

#### Scenario: 重复创建
- **WHEN** 用户使用同一幂等键重复提交相同来源
- **THEN** 系统返回同一转换结果而不创建重复版本

### Requirement: Player Run 启动与观察
授权用户 SHALL 能从 PrototypeVersion 启动 Player Run，并查看运行状态、完整运行详情、Episode 批次、摘要、分页轨迹和聚合指标。

#### Scenario: 启动 Player Run
- **WHEN** 用户选择版本、Persona 和合法预算并确认启动
- **THEN** 系统创建 Player Run 并导航到可持续刷新的证据视图

#### Scenario: 查看没有 Episode 的运行
- **WHEN** Player Run 尚未生成 Episode 或运行失败
- **THEN** 页面显示真实状态和安全错误摘要，不伪造空的成功证据

### Requirement: V4 与 V5 边界可见
系统 MUST 明确标记 V4 Prototype/Player 与 V5 Cocos Runtime 的兼容状态，未经验证的转换 MUST NOT 被展示为完全等价。

#### Scenario: 能力不兼容
- **WHEN** Cocos 产物无法满足现有 Player Runtime 契约
- **THEN** 转换操作被禁用并显示缺失能力和可采取的后续行动
