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
| 前端 unit（25 tests）与 GameConfig 契约 | PASS |
| 前端生产构建 | PASS |
| Docker Compose 六个常驻服务 | healthy |
| Flyway | 33 migrations validated，schema 升级到 V32 |
| V3 浏览器主链路 | PASS，约 77 秒 |
| 导出安全/确定性测试 | PASS |
| 移动端 375px | PASS |

本次样本导出包为 19,974 bytes、一次尝试完成。该数据仅作为防止明显退化的单机基线，不构成并发吞吐或 SLA 承诺。

## 安全与内容边界

- 导出 API 校验 user/project/version/job 归属；越权版本由自动化测试拒绝。
- ZIP entry 拒绝绝对路径、反斜杠、控制字符与 `..` 段，不创建符号链接。
- 正文扫描阻止远程 URL、数据库连接串和凭据形态；资源仅来自 capability 白名单并映射为包内 SVG/静音音效。
- manifest 对每个非自身文件记录 SHA-256，下载时再次验证整个 ZIP 摘要。
- 失败只持久化冻结输入和稳定错误码；重试不查询最新聚合、不调用 AI、不发布半成品。

## 明确非目标

没有第二游戏模板、Galgame、复杂战斗/多关卡、微信原生包、Unity/Godot 工程、云部署、素材商店或公开分享。导出包不执行模型生成的 JavaScript/HTML；它使用仓库内固定的离线 Runtime。
