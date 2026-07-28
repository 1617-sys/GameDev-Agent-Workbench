# V4-24 Prototype DRAFT 与人工审批

## 前置条件

V4-23 已通过人工 Review。

## 目标

为 PrototypeVersion 增加明确生命周期和人工审批证据，使 Director 只能创建 DRAFT，不能自行批准或发布。

## 允许修改

- PrototypeVersion 领域、Controller、Service、VO/DTO 的生命周期扩展
- Director Tool Registry 的 DRAFT/approval 接线
- 新增下一序号 Flyway migration
- Java 与前端 API 的相关测试；本任务不做完整 UI

## 禁止修改

- 修改既有 migration
- Agent 自动调用 approve/publish
- 覆盖父版本配置
- 将审批者伪装成 Director 或 SERVICE

## 工作内容

- 状态至少包含 DRAFT、APPROVED、REJECTED；既有版本迁移状态必须明确；
- 新增人工 approve/reject 命令、原因和审计记录；
- 注册 `CREATE_DRAFT_VERSION` 和 `REQUEST_HUMAN_APPROVAL`；
- DRAFT 创建使用严格调参 DTO、父版本、config digest 和幂等键；
- WAITING_APPROVAL 只被真实用户命令唤醒。

## 验收标准

- Director 身份无法批准或发布；
- 重复审批幂等，冲突审批被拒绝；
- DRAFT 不影响父版本和既有正式试玩链接；
- 越权、篡改参数、未知字段和状态竞争测试通过；
- 审批证据包含 actor、时间、原因、候选和 DirectorRun。

## 必须执行

```powershell
cd backend-java
mvn test
```
