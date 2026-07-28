# V4-16 Java Player Run Orchestration

## 前置条件

V4-15 已通过人工 Review。

## 目标

由 Java 校验用户、项目和 PrototypeVersion，调用 Python Player API，并通过 V4-08 服务持久化不可变 Episode 证据。

## 允许修改

- `backend-java/src/main/java/com/example/gameworkbench/**` 中 player run 相关新文件
- MachineEpisode 领域的最小复用修改
- `backend-java/src/test/**` 相关测试
- 后端与 Compose 的 Player API 配置

## 禁止修改

- Python、Simulation Service 和 UI
- 绕过现有 MachineEpisodeService 直接写 Mapper
- 同步请求无限等待大批量 LLM Episode
- 允许用户指定任意模型 URL、Prompt 或 policy ID

## 工作内容

- 提供创建 Player Run、查询状态和结果接口；
- 校验版本归属、冻结 config digest 和允许的 Persona/Policy；
- 使用现有异步可靠执行能力承载批量任务；
- Python 返回后调用 MachineEpisodeService 幂等持久化；
- 保存失败分类、trace、模型/策略版本和运行预算。

## 验收标准

- Java → Python → Simulation Service → Java persistence 闭环通过；
- 重复提交、消费重试和超时不产生重复 Episode；
- 跨项目、无权版本、篡改 digest 和非法 Persona 被拒绝；
- 查询接口可获得批次、Episode 和聚合指标。

## 必须执行

```powershell
cd backend-java
mvn test
```
