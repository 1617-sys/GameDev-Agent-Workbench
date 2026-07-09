# 工程陷阱记录

## Redis 锁不能使用 `GET` 后 `DELETE`

锁读取与删除若分成两个 Redis 命令，会出现竞态：请求 A 读取到自己的 owner 后锁恰好过期，
请求 B 随即获得同一个 key；此时 A 再执行 `DELETE`，会误删 B 的锁。

Demo Stream 的规则：

- 鉴权必须早于任何 Redis 操作。
- 每次请求生成唯一 owner token，并通过 `SET NX EX` 获取带 TTL 的锁。
- 只有实际获得锁的请求可以释放。
- 释放必须使用 Lua 在 Redis 端原子执行 owner compare-and-delete。
- Lua 的 key 和 owner 参数必须经由写锁所用的同一个 `RedisTemplate` serializer。
- 释放失败不得覆盖 Workflow 的原始异常。
