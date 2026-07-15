# V3 轻量原型发布验收

> 验收日期：2026-07-16
> 结论：PASS（单机 Docker Compose、`arcade_collect` 范围）

## 覆盖范围

- 新账号创建项目并提交 Prototype Brief。
- `GAME_GENERATE` 生成概念、核心循环、开发任务、GameConfig 2.0 与资源 manifest。
- PrototypeVersion 1 试玩采样，白名单调参创建不可变 Version 2。
- Version 2 完成 5 个服务端可复算的试玩会话，生成平衡建议。
- 基于冻结输入导出本地 ZIP；重复 key 返回同一 job，下载摘要等于持久化 SHA-256。
- 浏览器把实际下载包解压到临时目录，通过 `file://` 打开 `demo/index.html`，离线 Runtime 从 READY 进入 PLAYING。
- 页面在桌面与 375×812 视口加载 Version 2，Phaser Runtime 进入 ready，页面无横向溢出。

## 自动验证结果

| Gate | 结果 |
| --- | --- |
| 后端完整 Maven 测试 | PASS |
| 前端 unit（32 tests）与 GameConfig 契约 | PASS |
| 前端生产构建 | PASS |
| Docker Compose 六个常驻服务 | healthy |
| Flyway | 33 migrations validated，schema 升级到 V32 |
| V3 浏览器主链路 | PASS，约 84 秒 |
| 导出安全/确定性测试 | PASS |
| 移动端 375px | PASS |

本次样本导出包为 19,974 bytes、一次尝试完成。该数据仅作为防止明显退化的单机基线，不构成并发吞吐或 SLA 承诺。

## 前端导出发布记录

- 前端版本：`frontend-vue` 2.1.0；Compose 镜像摘要 `sha256:4afb8b5414b43f5b3c5d55621272f6dca6747e2269bdbe0385393c0a651dba17`。
- 后端兼容基线：Flyway V32，导出契约提交 `283f6af`；本次仅升级前端与验收文档，没有修改后端或数据库。
- 页面通过独立 adapter 携带 JWT 创建、查询、重试和下载导出作业；浏览器无法读取跨域 `Content-Disposition` 时，使用作业响应中的 `packageName` 安全兜底。
- 已验证 PENDING 有界轮询、页面切换取消陈旧流程、COMPLETED 重下载、FAILED 原 job 重试，以及下载错误不会被保存为 ZIP。
- 已从新构建的 Compose 前端容器完成真实浏览器导出；限制仍为单机 Compose、`arcade_collect` 和固定离线 Runtime，不代表生产 SLA。

回滚仅替换前端镜像或恢复上一个前端提交，不回滚 Flyway V32，也不删除 MySQL/Redis volume。具体命令见 `docs/docker-one-click-start.md` 的“前端单独升级与回滚”。

## 安全与内容边界

- 导出 API 校验 user/project/version/job 归属；越权版本由自动化测试拒绝。
- ZIP entry 拒绝绝对路径、反斜杠、控制字符与 `..` 段，不创建符号链接。
- 正文扫描阻止远程 URL、数据库连接串和凭据形态；资源仅来自 capability 白名单并映射为包内 SVG/静音音效。
- manifest 对每个非自身文件记录 SHA-256，下载时再次验证整个 ZIP 摘要。
- 失败只持久化冻结输入和稳定错误码；重试不查询最新聚合、不调用 AI、不发布半成品。

## 明确非目标

没有第二游戏模板、Galgame、复杂战斗/多关卡、微信原生包、Unity/Godot 工程、云部署、素材商店或公开分享。导出包不执行模型生成的 JavaScript/HTML；它使用仓库内固定的离线 Runtime。
