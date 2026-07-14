# V3-06：原型包导出与发布验收

> 状态：`TODO`
>
> 前置任务：`V3-01` 至 `V3-05`
>
> 推荐模型：`gpt-5.5` 审查，`gpt-5.4` 机械修复
>
> 预计工时：8-12 小时

## 目标

把已保存的原型版本、设计 Artifact 和试玩评测组装为可复现原型包，并在 Docker 环境完成从创意到导出的 V3 主链路验收。

## 范围

- 实现基于已持久化数据的 Prototype Package 导出。
- 导出设计简报、核心循环、GameConfig、资源 manifest、试玩摘要、平衡建议、开发任务和可运行 H5 Demo。
- 导出请求具有权限校验、幂等和失败重试能力。
- 增加压缩包内容、路径安全、敏感信息和校验和测试。
- 更新主链路浏览器 E2E、Docker 启动说明、演示脚本和项目叙述。
- 完成性能基线、安全审查、移动端试玩和最终 diff 审查。

## 非目标

- 不在导出时重新调用模型。
- 不导出微信小游戏原生包、Unity/Godot 项目或第二游戏模板。
- 不建设云端部署平台、素材商店和公开分享社区。
- 不重新执行与 V3 无关的全部历史演练。

## 约束

- 压缩包内容必须对应指定的不可变 PrototypeVersion。
- 文件名、相对路径和压缩过程必须防止路径穿越。
- 不包含密钥、Token、内部日志、数据库地址和未授权资源。
- 相同版本重复导出应得到语义一致的内容，不产生新的 AI Artifact。
- E2E 等待可观察状态，不使用任意长 sleep 掩盖竞态。

## 验收标准

- [ ] Docker 环境能完成“Brief -> AI 生成 -> 试玩 -> 调参版本 -> 指标 -> 建议 -> 导出”。
- [ ] 导出包结构完整，GameConfig 和资源可离线启动 H5 Demo。
- [ ] 导出内容与指定版本一致，并通过敏感信息扫描。
- [ ] 重复导出、失败重试和越权请求有自动化测试。
- [ ] 桌面和 375px 手机视口主流程可用。
- [ ] 项目文档准确说明 V3 能力和明确非目标。

## 验证命令

```powershell
.\tools\verify.ps1 -Profile quick

docker compose up -d --build
docker compose ps

cd frontend-vue
npm run test:unit
npm run test:game-config
npm run test:e2e:main
npm run build

cd ..
git diff --check
git status --short
```

## 人工验收

1. 使用新账号创建项目并提交 Prototype Brief。
2. 打开生成的 `arcade_collect` 原型，在桌面和手机视口各完成一次试玩。
3. 调整白名单参数创建新版本，确认旧版本不变。
4. 比较两个版本配置和试玩指标。
5. 生成平衡建议并确认包含数据范围与版本来源。
6. 下载原型包并离线打开 H5 Demo。

## 完成定义

- 自动验证、Docker E2E、安全审查和人工验收通过。
- V3 主链路可重复演示，不依赖隐藏手工步骤。
- 真实踩坑已沉淀到 `docs/PITFALLS.md`。
- 最终 diff 没有 Galgame、微信原生包和任务范围外扩张。

