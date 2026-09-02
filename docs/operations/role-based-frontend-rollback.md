# 角色化前端发布与回滚

## 发布前

1. 备份数据库并确认 Flyway 将执行 `V39__add_user_role_capabilities.sql` 和 `V40__add_agent_run_idempotency.sql`。
2. 运行严格端点门禁、前端单元测试/构建、Java 全量测试、角色 E2E 和 V4/V5 浏览器 E2E。
3. 使用真实 `USER`、`PROJECT_ADVANCED`、`ADMIN` 账号逐页验收；确认普通用户看不到管理员、Demo、旧版或内部写接口。
4. 确认 Cocos Creator 3.8.8 路径和 `arcade_collect/1` capability digest 未变化。

## 回滚

1. 将应用版本回滚到变更前提交 `7a31479` 对应的部署产物；不要用 `git reset --hard` 覆盖开发者工作区。
2. 前端和后端必须成对回滚，避免旧前端无法理解 capability 或结构化桥接结果。
3. `sys_user.role`、`agent_run.idempotency_key` 和 `request_fingerprint` 为向后兼容新增列，可在应用回滚期间保留，避免破坏历史数据与重复调用证据。
4. 若必须回退数据库，先确认没有依赖新增角色或幂等键的数据，再通过受审查的独立迁移执行；不要手工删除列。
5. 回滚后重新运行认证、V4 Prototype/Player、V5 GenerationRun、Cocos 构建与 API 覆盖冒烟测试。

## 失效保护

- capability 获取失败时前端不显示受控入口，后端继续拒绝未授权调用。
- V5→V4 转换不兼容时返回 `compatible=false` 和结构化 reasons，不创建伪造的 PrototypeVersion。
- 生产 profile 不注册 Demo 执行端点；Machine Episode 批量写入保持 internal。
