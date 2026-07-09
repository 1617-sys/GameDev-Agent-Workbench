# Redis 接入指导计划

## 目标

在当前 GameDev Agent Workbench 项目中，Redis 不要作为“为了简历而加的技术”，而是承担几个真实职责：

- 缓存用户基础信息，减少重复查库。
- 缓存项目列表这类读多写少数据。
- 保存 Agent / Demo 执行过程中的临时状态。
- 防止用户重复点击导致重复生成 Demo。

完成后，项目可以在简历和面试中说明：不仅接入了 Redis，还理解了缓存命中、缓存失效、TTL、分布式锁和业务使用场景。

## 适合本项目的 Redis 使用点

### 1. 用户信息缓存

适用接口：

- `GET /api/auth/me`

建议缓存：

```text
auth:user:{userId}
```

建议 TTL：

```text
与 JWT 过期时间一致，或设置为 1 天
```

调用逻辑：

```text
用户请求 /api/auth/me
-> JWT 解析出 userId
-> 先查 Redis
-> Redis 命中，直接返回用户信息
-> Redis 未命中，查 MySQL
-> 查到后写入 Redis
-> 返回用户信息
```

学习重点：

- JWT 负责身份认证。
- Redis 缓存用户基础信息。
- 两者不是替代关系，而是配合关系。

### 2. 项目列表缓存

适用接口：

- `GET /api/projects`

建议缓存：

```text
project:list:{userId}
```

建议 TTL：

```text
5-10 分钟
```

查询逻辑：

```text
查询项目列表
-> 先查 Redis
-> 有缓存，直接返回
-> 没有缓存，查 MySQL
-> 写入 Redis
-> 返回结果
```

写操作后的缓存处理：

```text
创建项目成功
-> 删除 project:list:{userId}

更新项目成功
-> 删除 project:list:{userId}
```

这里建议使用“删除缓存”而不是“更新缓存”。

原因：

- 删除缓存逻辑简单。
- 下次查询时重新加载最新数据。
- 对当前项目足够稳妥。

面试表达：

> 对读多写少的项目列表使用 Redis 缓存，在创建和更新项目后删除缓存，下一次查询重新加载，降低缓存一致性维护复杂度。

### 3. Agent 执行状态缓存

适用链路：

- `POST /api/agent/run`
- `POST /api/workflows/game-design/run`
- `POST /api/demo/game/stream`

建议缓存：

```text
agent:run:status:{runUuid}
workflow:run:status:{workflowRunUuid}
demo:status:{projectUuid}
```

建议 TTL：

```text
30 分钟到 1 小时
```

执行过程中可以写入：

```text
RUNNING
SUCCESS
FAILED
当前 stage
错误信息
最后更新时间
```

价值：

- 前端刷新后可以恢复当前执行状态。
- SSE 连接中断后，可以查询最后执行到哪一步。
- 减少频繁查数据库的需求。

面试表达：

> 在 Agent 和 Demo 生成链路中使用 Redis 保存临时执行状态，用于前端刷新恢复和 SSE 断线后的状态查询。

### 4. Demo 生成防重复提交

适用接口：

- `POST /api/demo/game/stream`

建议 key：

```text
lock:demo:generate:{userId}:{projectUuid}
```

建议 TTL：

```text
60 秒到 5 分钟
```

调用逻辑：

```text
用户点击一键生成 Demo
-> 鉴权通过后生成每请求唯一 owner token
-> Redis SET key ownerToken NX EX ttl
-> 如果写入成功，说明可以执行
-> 如果写入失败，说明正在生成，直接提示请勿重复提交
-> 执行完成后用 Lua 原子比较 owner token 并删除 lock
-> 如果服务异常，TTL 到期后自动释放
```

当前 Demo Stream 使用稳定 key `demoStream:{userId}` 和 300 秒 TTL。释放锁必须在 Redis
服务端以单条 Lua 脚本完成 compare-and-delete；未获得锁的请求不得释放，且禁止先 `GET`
再 `DELETE`。

面试表达：

> 使用 Redis NX + TTL 实现 Demo 生成防重复提交，避免用户连续点击造成重复任务。

## 推荐实现顺序

### Day 1：Redis 基础接入

任务：

- 引入 `spring-boot-starter-data-redis`。
- 在 `application.yml` 中配置 Redis 地址。
- 创建 Redis 配置类。
- 封装一个简单 `RedisService`。
- 写一个临时测试方法，确认 Redis 可以正常读写。

建议自己写：

- Redis key 命名规则。
- `RedisService` 的基础方法。

AI 可以辅助：

- Redis 序列化配置。
- `application-example.yml` 示例配置。

### Day 2：用户信息缓存

任务：

- 改造 `/api/auth/me`。
- 查询用户时先查 Redis。
- 未命中时查 MySQL。
- 查到后写入 Redis。

建议自己写：

- 缓存命中 / 未命中的判断逻辑。
- 用户缓存 key。

AI 可以辅助：

- 对象 JSON 序列化和反序列化代码。

### Day 3：项目列表缓存

任务：

- 改造 `listProjects`。
- 查询时优先读 Redis。
- 创建项目、更新项目后删除缓存。

建议自己写：

- 查询缓存逻辑。
- 写操作后删除缓存。

AI 可以辅助：

- 重复工具方法封装。

### Day 4：Agent / Demo 状态缓存

任务：

- Agent 执行开始时写入 `RUNNING`。
- Agent 执行成功时写入 `SUCCESS`。
- Agent 执行失败时写入 `FAILED` 和错误信息。
- Demo SSE 每个阶段都写入当前 stage。

建议自己写：

- 状态流转逻辑。
- Redis key 设计。

AI 可以辅助：

- 状态对象 DTO。
- 日志补充。

### Day 5：防重复提交

任务：

- Demo 生成开始前加 Redis lock。
- 重复点击时直接返回友好提示。
- 任务完成后释放锁。
- 异常情况下依赖 TTL 自动释放。

建议自己写：

- `setIfAbsent` 逻辑。
- finally 中释放锁。

AI 可以辅助：

- 错误码和提示信息。

## Redis Key 命名建议

统一格式：

```text
业务模块:业务含义:唯一标识
```

推荐 key：

```text
auth:user:{userId}
project:list:{userId}
agent:run:status:{runUuid}
workflow:run:status:{workflowRunUuid}
demo:status:{projectUuid}
lock:demo:generate:{userId}:{projectUuid}
```

注意：

- key 不要太随意。
- 不要把密码、API Key 等敏感信息直接放进 Redis。
- 状态类缓存一定要设置 TTL。
- 锁类 key 一定要设置 TTL，避免死锁。

## 哪些代码必须自己写

建议自己写：

- Redis 使用场景选择。
- key 命名。
- 项目列表缓存。
- 写操作后删除缓存。
- Demo 生成防重复提交。
- 缓存失效策略说明。

原因：

这些内容在面试里很容易被追问。如果只是让 AI 生成，自己不理解，会很难讲清楚。

## 哪些代码可以交给 AI

可以让 AI 辅助：

- `RedisConfig`
- JSON 序列化配置
- `RedisService` 基础工具方法
- 文档整理
- Apifox 测试用例
- README 简历描述

## 面试可讲版本

可以这样介绍：

> 项目中引入 Redis 主要用于用户信息缓存、项目列表缓存、Agent 执行状态缓存和 Demo 生成防重复提交。对于项目列表这类读多写少数据，我采用查询时读取缓存、写操作后删除缓存的方式降低数据库压力并简化一致性维护。对于 Demo 生成，我使用 Redis NX + TTL 做简单防重复提交，避免用户连续点击导致重复生成任务。

## 简历可写版本

```text
引入 Redis 缓存用户信息、项目列表和 Agent 执行状态，针对项目创建/更新操作设计缓存失效策略，并使用 Redis NX + TTL 实现 Demo 生成防重复提交，提升接口响应速度与系统稳定性。
```

## 暂时不建议做的内容

当前阶段暂时不建议优先做：

- Redis Cluster
- Redisson 分布式锁复杂封装
- 多级缓存
- 缓存预热
- 缓存雪崩 / 击穿 / 穿透的完整企业级方案

原因：

当前项目主要目标是实习求职和 Demo 展示。先把 Redis 用在真实业务链路里，比堆复杂概念更重要。
