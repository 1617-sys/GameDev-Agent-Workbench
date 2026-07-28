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

## 持久化 Docker 数据不能替代干净迁移的工作流契约验证

本地已有 volume 可能保留过往测试手工写入的工作流定义，因此“当前容器能接受某个 workflowKey”并不等于从空数据目录构建也能接受它。前端固定提交 `GAME_GENERATE` 前，必须在隔离或重置后的 Compose 环境中确认 Flyway migration 已提供对应 active definition；仅检查带历史数据的本地环境会掩盖发布阻断。

处理方式：主链路验收优先使用隔离 Compose harness，并把前端固定 workflowKey 与迁移中的 active definition 一起审查。发现两者不一致时，记录为后端契约缺口并单独建卡，不在前端偷偷回退为旧 key。

## Redis Lua 限流参数必须使用 Lua 可识别的字符串序列化

隔离 Compose 中 Redis health 正常时，首个工作流提交仍返回 `50302 Workflow submission is temporarily unavailable`。原因是固定窗口 Lua 脚本需要可被 `tonumber` 解析的 TTL 和上限参数；若复用 JSON value serializer 传递这些参数，Redis 会把字符串包装为 JSON，脚本在比较计数时失败，并被统一映射为“限流服务不可用”。

处理方式：为 Lua 的 key/argument 明确使用字符串序列化，并在干净 Docker 环境覆盖“首个提交允许”的回归。该问题属于后端发布阻断，不能由前端重试、回退 workflowKey 或伪造成功状态掩盖。

## Artifact 追溯列升级后，旁路产物也必须填写 sourceAttempt

V3-03 把 `agent_artifact.source_attempt` 收紧为非空后，工作流产物会由统一链路填写该字段，但试玩评测生成的 `BALANCE_SUGGESTION` 属于旁路产物。只在单元测试中 mock Mapper 会漏掉数据库非空约束，直到 Docker 主链路才出现 `DataIntegrityViolationException`。

处理方式：所有创建 `AgentArtifact` 的入口都必须显式填写追溯字段；发布验收必须至少执行一次真实迁移后的数据库写入，不能只依赖 Mapper mock。

## ZIP 路径安全与文件内容扫描必须分开

路径穿越规则适用于 ZIP entry 名称，不适用于 Markdown/JSON 正文。把反斜杠和 `..` 同时用于正文扫描，会误伤 JSON 中的转义换行和正常设计文本；把单词 `token` 一律拦截也会误伤普通说明。

处理方式：entry 路径单独拒绝绝对路径、反斜杠、控制字符和 `..` 路径段；正文只阻止远程 URL、数据库连接串、Bearer 凭据以及带赋值分隔符的 key/password/secret/token。合法转义与普通文字必须有回归测试。

## 确定性 ZIP 不能依赖压缩器默认元数据

即使文件内容相同，条目顺序和时间戳不同也会改变 ZIP 摘要。manifest 自身若同时声明自己的摘要，还会形成无法收敛的自引用。

处理方式：条目按相对路径排序，统一使用 PrototypeVersion `createdAt`，文本统一 UTF-8/LF；manifest 记录除自身以外的文件摘要并明确自排除。失败作业只保存错误和冻结输入，不能发布半包；重试必须读取冻结 JSON，不能查询最新指标或再次调用 AI。
