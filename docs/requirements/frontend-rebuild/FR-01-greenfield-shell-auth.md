# FR-01：全新工程、应用框架与认证

> 状态：`DONE`
>
> 前置任务：无
>
> 推荐模型：`gpt-5.4`

## 目标

从零建立与旧产品 UI 无依赖的 Vue 应用框架，验收后替换到 `frontend-vue`，并完成注册、登录、恢复会话和退出。

## 范围

- Vite、Vue Router、Pinia、Lucide 和全局视觉 Token。
- 认证页、应用 Shell、路由守卫和 sessionStorage 会话。
- 重新编写 HTTP Client 与认证 API。
- loading、校验、网络错误和 401 状态。
- 认证 API 和路由守卫单元测试。

## 非目标

- 不复制旧 App、View、Component 和 CSS。
- 不实现项目、工作流和游戏页面。
- 不修改 Java 认证契约。

## 验收标准

- [ ] 新工程可独立安装、测试和构建。
- [ ] 注册、登录、恢复和退出可用。
- [ ] Token 仅存在 sessionStorage 和请求头中。
- [ ] 认证页面在桌面和 375px 视口可用。

## 验证命令

```powershell
cd frontend-vue
npm run test:unit
npm run build
```

## 完成定义

- 验收标准通过，diff 不包含旧 UI 复制和后端修改。
