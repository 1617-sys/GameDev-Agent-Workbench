# V4-10 Interactive Simulation Service

## 前置条件

V4-09 已通过人工 Review。

## 目标

将 Simulation Core 暴露为有界的内部 HTTP 会话服务，使 Python Player 能逐步 observe/act，而不是预先提交动作序列。

## 允许修改

- `frontend-vue/src/features/demo/runtime/simulation-service/**`
- `frontend-vue/src/features/demo/runtime/simulation/**` 的最小协议适配
- `frontend-vue/tests/simulationService.test.*`
- `frontend-vue/package.json`
- 新建专用 Docker target/file
- `docker-compose.yml` 中新增 `simulation-service`
- `.env.example` 中新增非敏感配置

## 禁止修改

- Phaser/Vue 页面行为
- Java、Python与数据库
- 复制 Simulation Core 玩法规则
- 使用前端静态站点端口兼任内部服务

## 工作内容

- 提供 health、create session、observe、step、close；
- session 只保存在受限内存中，设置 TTL、总量和单会话 step 上限；
- 使用内部 token，错误不得回传堆栈和完整配置；
- step 返回协议 Observation、StepResult 和最终 Episode 所需数据；
- close/终止后释放会话，进程关闭时清理。

## 验收标准

- 每次 Action 前均可取得最新 Observation；
- 非法 token、未知会话、过期会话、并发冲突和终止后 step 均有测试；
- 相同 config、seed、action sequence 与 Headless Runner 终态哈希一致；
- Compose healthcheck 通过，服务不暴露到公网地址。

## 必须执行

```powershell
cd frontend-vue
npm run test:unit
npm run build
docker compose config
```
