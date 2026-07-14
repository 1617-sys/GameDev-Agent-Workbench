# FR-04：Docker 切换与演示验收

> 状态：`DONE`
>
> 前置任务：`FR-01`、`FR-02`、`FR-03`
>
> 推荐模型：`gpt-5.5` 审查，`gpt-5.4` 修复

## 目标

将 Compose 发布入口切换到新前端，删除旧前端发布路径，并完成真实 DeepSeek 主链路和视觉验收。

## 范围

- 更新 Compose、启动文档和必要 Harness 路径。
- 重写浏览器主链路 E2E 选择器和断言。
- 验证注册、项目、提交、SSE、Artifact 和 Phaser。
- 桌面和 375px 截图与重叠检查。
- 删除旧 `frontend-vue` 或在同一提交中完成最终目录替换。

## 非目标

- 不增加业务功能、第二玩法和生产部署平台。
- 不把 DeepSeek Key 写入仓库或测试证据。
- 不为通过测试加入固定长时间 sleep。

## 验收标准

- [ ] `docker compose up -d --build` 后全部服务健康。
- [ ] 真实 DeepSeek 工作流可以到达 SUCCESS。
- [ ] Phaser Runtime 在运行详情中可见且可交互。
- [ ] 新前端没有旧页面、旧样式和旧测试依赖。
- [ ] 桌面和移动视口无溢出、遮挡和不可点击控件。

## 验证命令

```powershell
cd frontend-vue
npm run test:unit
npm run test:runtime
npm run build

cd ..
docker compose up -d --build
docker compose ps
git diff --check
```

## 完成定义

- 自动验证与人工主链路通过，新前端成为唯一发布入口。
