# GameDev Agent Workbench Frontend

这是一个不依赖构建工具的静态前端 MVP，用来演示项目、Agent 运行、工作流运行和产物查看。

## 启动

在 `frontend` 目录运行：

```bash
python -m http.server 5173
```

然后访问：

```text
http://localhost:5173
```

默认后端地址：

```text
http://localhost:8080
```

可以在页面的“连接设置”里修改。

## 当前支持

- 登录并保存 JWT
- 创建游戏项目
- 查看项目列表
- 运行单个 Agent
- 运行三步游戏设计工作流
- 查看 Agent 运行记录
- 查看项目下 Artifact

## 后续建议

- 迁移到 React + Vite
- 接入分页组件
- 为 Workflow 历史增加列表页
- 增加 SSE 流式输出
- 增加产物编辑器
