# Vue3 Frontend

这是 GameDev Agent Workbench 的 Vue3 演示前端，重点用于展示完整 AI 工具流：

```text
登录
-> 选择游戏项目
-> 输入游戏想法
-> SSE 运行三步 Demo Workflow
-> 查看 Agent 输出和 Artifact
-> 打开可试玩 Demo 链接
```

## 启动

```bash
cd frontend-vue
npm install
npm run dev
```

默认地址：

```text
http://localhost:5173
```

## 后端地址

默认 Java 后端地址：

```text
http://localhost:8080
```

可以在页面右上角修改并保存。

## 注意

`/api/demo/game/stream` 是 POST SSE，所以这里没有使用浏览器原生 `EventSource`，而是使用 `fetch + ReadableStream` 来读取流式事件。
