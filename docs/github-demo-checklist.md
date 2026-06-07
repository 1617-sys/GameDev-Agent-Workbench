# GitHub Demo Checklist

这份清单用于投简历前整理仓库，目标是让面试官打开 GitHub 后能快速理解项目价值，并知道如何启动和演示。

## 1. GitHub 首页必须清楚说明

- 项目是什么：AI 小游戏开发工作台
- 解决什么问题：把游戏创意拆解为概念、核心循环、任务，并返回可试玩 Demo
- 使用什么技术：Spring Boot、FastAPI、Vue3、LLM、SSE、MyBatis-Plus、MySQL
- 当前完成什么：认证、项目、AgentRun、Prompt 模板、Workflow、Artifact、SSE、Demo URL
- 如何启动：Java、Python、Vue3 三个服务分别怎么启动
- 如何演示：从注册登录到一键生成 Demo 的流程

## 2. 推荐仓库结构

```text
backend-java/      Spring Boot 后端
frontend-vue/      Vue3 工作台
frontend/          早期静态前端版本
docs/              项目文档、计划、设计说明
tools/             辅助脚本
README.md          GitHub 首页说明
```

## 3. 投简历前建议检查

- README 没有乱码
- README 中有完整启动步骤
- Java 后端可以启动
- Python Agent 可以启动
- Vue3 前端可以启动
- Apifox 中保留核心接口用例
- 数据库有基础测试数据
- Prompt 模板表中有 3 个 ACTIVE 模板
- `/api/demo/game/stream` 可以跑完
- 最终 SSE 返回 `demoUrl`
- `demoUrl` 能打开可试玩页面

## 4. 演示脚本

面试或录屏时可以按这个顺序演示：

```text
1. 展示项目 README，说明技术栈和架构
2. 打开 Vue3 前端
3. 登录账号
4. 创建或选择游戏项目
5. 输入游戏想法
6. 点击一键生成可玩 Demo
7. 观察 SSE 进度
8. 查看 Agent 输出和 Artifact
9. 打开 demoUrl 试玩小游戏
10. 说明后续会升级为 GameSpec 驱动的动态游戏生成
```

## 5. 简历讲法

可以重点讲这三点：

- Java 后端不是简单转发请求，而是负责用户、项目、状态、记录、产物和工作流编排。
- Python Agent 专注模型调用和提示词处理，体现 Java + Python 的跨服务协作。
- SSE 让 AI 生成过程可视化，Demo URL 让 AI 输出从文本变成可展示的产品闭环。

## 6. 当前版本边界

当前可试玩 Demo 是最小模板版，不是任意游戏源码生成器。

更准确的说法是：

```text
AI 生成游戏设计结果，并返回一个可试玩的模板化 Demo 页面。
```

后续升级方向：

```text
Agent 输出 -> GameSpec JSON -> 游戏模板读取 GameSpec -> 动态渲染不同玩法参数
```
