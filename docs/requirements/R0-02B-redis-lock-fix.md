# R0-02B：Redis 锁修复与审查

> 状态：`DONE`
>
> 前置任务：`R0-02A`
>
> 推荐模型：`gpt-5.5`
>
> 任务类型：高风险并发修复

## 背景

`R0-02A` 应已经产生稳定失败测试，证明 Demo Workflow 的锁获取、执行和释放语义不正确。本任务根据这些失败证据做最小修复，并补齐 owner 安全释放。

目标不是建立通用分布式锁框架，而是修复当前 Demo Workflow 的真实并发缺陷。

## 目标

实现以下锁语义：

```text
鉴权通过
-> 生成唯一 owner token
-> SET key owner NX EX ttl
-> 只有获取成功才执行 Workflow
-> 只有 owner 可以原子释放
-> 获取失败不执行、不释放
```

## 代码入口

- `backend-java/src/main/java/com/example/gameworkbench/service/impl/DemoStreamServiceImpl.java`
- `backend-java/src/main/java/com/example/gameworkbench/service/RedisService.java`
- `backend-java/src/main/java/com/example/gameworkbench/service/impl/RedisServiceImpl.java`
- `backend-java/src/main/java/com/example/gameworkbench/common/enums/ErrorCode.java`
- `backend-java/src/test/java/`
- `docs/redis-integration-plan.md`

## 范围

允许：

- 修复锁获取结果判断。
- 将用户鉴权移动到 Redis 操作之前。
- 为每次请求生成唯一 owner token。
- 为 `RedisService` 增加 owner-aware release 契约。
- 使用 Redis Lua compare-and-delete 实现原子释放。
- 只有获取成功时才尝试释放。
- 为重复执行增加或复用明确业务错误。
- 更新 `R0-02A` 测试，使其验证修复后的契约。
- 增加 `RedisServiceImpl` 原子释放相关单元测试。
- 对必要的 Redis 锁规则文档做最小同步。

## 非目标

- 不引入 Redisson。
- 不建立通用锁注解或 AOP。
- 不实现自动续期 watchdog。
- 不改变 Workflow 步骤。
- 不接入 RabbitMQ。
- 不增加全局用户限流。
- 不重构 SSE 架构。
- 不修改前端和 Python Agent。

## 约束

- 锁 key 必须保持稳定且包含正确业务维度。
- owner token 必须每次请求唯一，不能只使用 userId。
- 解锁必须在 Redis 端原子完成，禁止 `get` 后再 `delete`。
- 未获取锁时不得执行释放。
- Redis 返回 `null` 时按未获取锁处理。
- TTL 必须大于 0，并保留当前可解释默认值。
- 不持有 Java `synchronized` 锁等待 LLM。
- 不吞掉 Redis 或 Workflow 原始异常。

## 目标接口语义

接口命名可根据现有风格确定，但必须表达：

```java
boolean tryLock(String key, String ownerToken, long timeoutSeconds);

boolean releaseLock(String key, String ownerToken);
```

原子释放等价逻辑：

```lua
if redis.call("get", KEYS[1]) == ARGV[1] then
  return redis.call("del", KEYS[1])
end
return 0
```

## 验收标准

- [ ] `R0-02A` 的所有失败测试变绿。
- [ ] `setIfAbsent == true` 时执行 Workflow。
- [ ] `setIfAbsent == false/null` 时不执行 Workflow。
- [ ] 未获得锁的请求不会删除 Redis key。
- [ ] 锁 value 是唯一 owner token。
- [ ] owner 匹配时原子删除成功。
- [ ] owner 不匹配时不删除。
- [ ] `userId == null` 时不访问 Redis。
- [ ] 锁具有明确 TTL。
- [ ] 重复请求收到可解释错误，而不是静默结束。
- [ ] 相关测试和 quick Harness 通过。

## 验证命令

```powershell
cd backend-java
mvn -Dtest=DemoStreamServiceImplTest,RedisServiceImplTest test

cd ..
.\tools\verify.ps1 -Profile quick
```

## 审查清单

- 获取锁成功条件是否仍有布尔值写反风险。
- owner token 是否真的唯一。
- Lua 的 key/value serializer 是否与写锁一致。
- finally 是否只在 `lockAcquired == true` 时释放。
- 解锁失败是否覆盖原始 Workflow 异常。
- SSE 是否在重复请求时明确结束。
- 是否出现无关的 Workflow 或 Redis 重构。
- 测试是否包含错误 owner。

## 完成定义

- 所有验收标准通过真实测试证明。
- `R0-02A` 红灯测试全部变绿。
- quick Harness 返回 0。
- diff 只包含 Redis 锁修复、对应测试和必要文档。
- AI 已按并发、异常、资源释放维度审查 diff。
- 锁问题已记录到 `docs/PITFALLS.md`；若该文件尚不存在，本任务允许创建。
- 你能够解释为什么不能使用 `get` 再 `delete`。
