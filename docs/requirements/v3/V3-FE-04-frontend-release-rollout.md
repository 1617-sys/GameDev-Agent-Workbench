# V3-FE-04：前端构建、部署与发布收口

> 状态：`DONE`
>
> 前置任务：`V3-FE-03`
>
> 执行方式：`VIBE`（命令证据驱动，最终人工 diff 审查）
>
> 预计工时：30-45 分钟

## 目标

把通过验收的前端构建为可部署产物，升级 Compose 前端服务，并留下可复现验证与回滚信息。

## 范围

- 执行前端 unit、GameConfig、V3 E2E 和生产构建。
- 重建并替换 `frontend-vue` 容器，确认服务 healthy。
- 浏览器确认导出入口来自新构建，而非本地 dev server 或缓存。
- 记录前端构建版本、后端最低 migration `V32` 和对应 commit。
- 更新 Docker 启动说明、发布验收报告和必要踩坑记录。
- 提供回滚到上一前端镜像/commit 的操作说明；回滚不降级数据库。
- 最终检查范围、敏感信息、任务卡状态和 Git 工作区。

## 非目标

- 不重建后端业务逻辑或回滚 V32。
- 不引入 CDN、云部署、灰度平台或新的 CI/CD 系统。
- 不把单机 Compose 验收描述为生产 SLA。

## Vibe 执行约束

1. 让模型根据命令输出生成验收摘要，不允许在命令失败时自行写 PASS。
2. 文档只记录实际运行证据，不复制环境变量、Token、Cookie 或连接凭据。
3. 最终由人工检查 `git diff`、任务范围和回滚步骤，再允许 commit。
4. Vibe 生成的 commit message 必须由人工确认，禁止自动 push 未审查变更。

## 验收标准

- [x] 前端全部自动测试和生产构建通过。
- [x] 新前端容器 healthy，并能调用 V32 后端导出接口。
- [x] 浏览器可创建、重试并下载原型包。
- [x] 发布文档准确说明能力、限制和回滚方式。
- [x] 没有静态凭据、调试日志或测试产物进入提交。
- [x] 最终 diff 仅包含前端升级及必要文档。

## 验证命令

```powershell
.\tools\verify.ps1 -Profile quick

docker compose up -d --build frontend-vue
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

## 完成定义

- 部署中的前端具备完整导出能力，且有自动化证据和无数据库降级的回滚方案。
- 四张前端任务卡全部标记 `DONE` 后，前端升级才算完成。
