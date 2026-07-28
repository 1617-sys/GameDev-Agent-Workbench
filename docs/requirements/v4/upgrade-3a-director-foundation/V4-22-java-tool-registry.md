# V4-22 Java Typed Tool Registry

## 前置条件

V4-19 已通过人工 Review。

## 目标

建立 Java 侧类型化 Director Tool Registry，统一工具发现、参数校验、权限、幂等、执行和审计。

## 允许修改

- Java 中新增 `director/tool` 相关接口、schema 和实现
- 对现有 Service 的最小只读适配
- `backend-java/src/test/**` 对应测试

## 禁止修改

- 暴露 Controller 或 Mapper 作为 Agent 工具
- 动态类名、反射执行、任意 URL 或任意脚本工具
- 首任务实现候选创建和 PlayerRun 写操作
- Python、前端和数据库结构

## 首批只读工具

- `GET_PROTOTYPE_VERSION`
- `GET_MACHINE_EPISODE_METRICS`
- `GET_PLAYER_RUN_STATUS`
- `COMPARE_PROTOTYPE_CONFIGS`

## 验收标准

- 工具具有稳定 name/version、JSON schema、risk level 和 timeout；
- 输入先经过 schema、项目权限和版本归属校验；
- 结果大小受限，完整结果通过 resultRef 访问；
- 未注册、版本不符、额外字段、越权和超时测试通过；
- dry-run 不产生写操作。

## 必须执行

```powershell
cd backend-java
mvn test
```
