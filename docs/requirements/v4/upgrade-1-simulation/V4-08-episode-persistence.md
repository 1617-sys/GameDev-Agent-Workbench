# V4-08 Episode Persistence

## 前置条件

V4-03 与 V4-07 已通过人工 Review。

## 目标

在 Java 后端持久化机器 Episode、Step 或轨迹引用和批量运行结果，并与 PrototypeVersion 建立不可混淆的证据链。

## 允许修改

- `backend-java/src/main/java/com/example/gameworkbench/**` 中新建的 episode/experiment 领域文件
- 与现有 PrototypeVersion、鉴权和错误模型连接所需的最小修改
- 新增下一序号 Flyway migration
- `backend-java/src/test/**` 中相关测试
- `docs/requirements/v4/upgrade-0-foundation/V4-episode-protocol.md`，仅修复实现中确认的矛盾并明确记录

## 禁止修改

- 现有真人 `PlaytestSession`/`PlaytestEvent` 的字段语义
- Python Agent、前端页面和 Phaser Runtime
- 把机器 Episode 写进真人指标表
- 覆盖或修改已经执行的 Flyway migration
- 在 Controller 中实现聚合业务逻辑

## 工作内容

1. 实现 Episode 与 BatchRun 的实体、迁移、DTO、Service 和查询接口。
2. 绑定 project、PrototypeVersion、config hash、seed、persona 和 policy version。
3. 支持幂等提交、部分失败、终止原因和原始轨迹引用。
4. 提供完成率、终止原因、耗时和动作数的基础聚合。
5. 强制项目级权限与跨项目隔离。

## 验收标准

- 同一幂等键重复提交不产生重复 Episode；
- PrototypeVersion/config hash 不匹配时拒绝写入；
- 机器与真人样本在存储和查询上明确隔离；
- 聚合结果能追溯到原始 Episode；
- 跨项目读写测试、事务回滚测试和 migration 测试通过。

## 必须执行

```powershell
cd backend-java
mvn test
```

## 完成门禁

本任务通过人工 Review 后，Upgrade 1 才算完成。此时停止编码并根据实际 API、性能和数据量重新拆分 Upgrade 2。
