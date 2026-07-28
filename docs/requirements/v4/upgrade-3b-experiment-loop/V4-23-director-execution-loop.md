# V4-23 Director Execution Loop

## 前置条件

Upgrade 3A 已通过人工 Review。

## 目标

由 Java Worker 驱动 `加载 checkpoint → 调用 Python 决策 → 校验/执行一个工具 → 持久化 → 下一轮或暂停`。

## 允许修改

- Java DirectorRun application/service/worker/client 相关文件
- Python Director Client DTO 的 Java 映射
- 必要的配置、恢复扫描和测试

## 禁止修改

- 在 HTTP 请求线程内无限循环
- 一个事务跨越 Python 或工具网络调用
- Worker 根据 LLM 文本绕过 Tool Registry
- 引入新的消息中间件

## 工作内容

- 实现提交、查询、取消和异步执行；
- 每轮使用状态版本 claim，网络调用前后分别提交事务；
- 执行 Tool Registry 中的单个工具并保存结果；
- WAITING_EXPERIMENT/WAITING_APPROVAL 不忙轮询；
- 支持超时、重试、恢复扫描和最大轮数；
- 传播 trace，记录状态事件和错误分类。

## 验收标准

- 重复消息、进程重启和工具超时不会重复已成功写操作；
- checkpoint 恢复后从下一未完成轮继续；
- 取消后不再发起模型或工具调用；
- 预算耗尽稳定进入 FAILED，而不是无限重试；
- fake Director + fake tools 集成测试通过。

## 必须执行

```powershell
cd backend-java
mvn test
```
