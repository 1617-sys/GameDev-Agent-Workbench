# V3-FE-03：导出浏览器 E2E 与离线包验收

> 状态：`DONE`
>
> 前置任务：`V3-FE-02`
>
> 执行方式：`VIBE`（失败驱动生成测试，真实浏览器裁决）
>
> 预计工时：45-75 分钟

## 目标

用真实 Docker 服务和浏览器证明前端升级不是“按钮能点”，而是能够完成 V3 主链路、下载正确 ZIP，并在断开服务依赖后启动包内 H5 Demo。

## 范围

- 增加或更新 V3 主链路 Playwright 配置和脚本。
- 使用新账号完成 Brief、AI 生成、版本 1 试玩、调参版本 2、指标、建议和导出。
- 等待 WorkflowRun 和 ExportJob 的可观察状态，不使用任意长 sleep。
- 验证重复导出返回同一 job，ZIP SHA-256 等于 `packageDigest`。
- 检查 ZIP entry 不包含绝对路径、反斜杠或 `..` 路径段。
- 解压实际下载文件，通过 `file://` 打开 `demo/index.html`。
- 验证离线 Runtime 从 `READY` 进入 `PLAYING`。
- 验证站内 Phaser Runtime 在桌面和 375×812 视口可用且无横向溢出。
- 测试账号密码运行时随机生成，不写静态凭据。

## 非目标

- 不做生产压力测试、跨浏览器矩阵或视觉像素快照平台。
- 不伪造数据库状态绕过生成、试玩和建议步骤。
- 不把外部模型质量作为前端通过条件。

## Vibe 执行约束

1. 先让模型从契约生成 happy-path 测试骨架，再逐步接入真实 API。
2. 每次失败保留具体 HTTP payload、状态或定位器证据，按根因最小修改。
3. 禁止通过扩大 timeout、增加固定 sleep 或删除断言让测试变绿。
4. 生成配置中的数量、ID 和时限必须从实际 GameConfig 派生，不能硬编码样例假设。
5. 人工检查测试是否真正打开下载包，而非只检查 ZIP 文件头。

## 验收标准

- [x] Docker 主链路从 Brief 执行到 ZIP 下载。
- [x] 同幂等键返回相同 job 和文件摘要。
- [x] 下载内容是安全路径的有效 ZIP。
- [x] 包内 Demo 可通过 `file://` 离线进入 PLAYING。
- [x] 桌面与 375px 页面、导出入口和 Runtime 可用。
- [x] E2E 不包含固定长 sleep 和静态凭据。

## 验证命令

```powershell
docker compose up -d --build
docker compose ps

cd frontend-vue
npm run test:e2e:main
```

## 完成定义

- E2E 在新建数据上可重复通过。
- 失败输出足以区分前端、后端、模型和环境问题。
