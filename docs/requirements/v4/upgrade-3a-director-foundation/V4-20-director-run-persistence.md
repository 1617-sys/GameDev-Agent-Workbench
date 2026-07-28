# V4-20 Director Run Persistence

## 前置条件

V4-19 已通过人工 Review。

## 目标

在 Java 中持久化 DirectorRun、状态快照、决策、工具调用、实验引用和审批等待状态。

## 允许修改

- Java 中新增 `director`/`experiment` 领域文件
- 新增下一序号 Flyway migration
- `backend-java/src/test/**` 对应测试

## 禁止修改

- Python、前端、Simulation Service
- 复用旧 WorkflowRun 表伪装 Director 状态
- 覆盖既有 migration
- 在数据库中保存密钥、完整 Prompt 或超大工具结果正文

## 工作内容

- 建立 DirectorRun、DirectorDecision、DirectorToolCall、ExperimentCandidate 记录；
- 保存不可变目标、预算、当前状态版本和 checkpoint；
- 工具输入输出保存 digest、摘要和 resultRef；
- 实现乐观锁、幂等创建、状态转换和项目隔离；
- 提供内部 Service 和只读查询，不实现执行 Worker。

## 验收标准

- 非法状态转换、并发更新和幂等冲突被拒绝；
- 终态不可重新执行；
- checkpoint 可恢复下一轮所需全部事实；
- 数据可追溯到 PrototypeVersion、PlayerRun 和 MachineEpisode；
- migration、事务回滚和跨项目测试通过。

## 必须执行

```powershell
cd backend-java
mvn test
```
