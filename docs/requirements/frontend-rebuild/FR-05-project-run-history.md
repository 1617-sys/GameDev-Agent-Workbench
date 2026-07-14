# FR-05：项目运行历史与恢复入口

> 状态：`DONE`
>
> 前置任务：`FR-04`
>
> 推荐模型：`gpt-5.4`

## 目标

用户离开生成详情后，仍能从所属项目重新找到进行中、成功和失败的生成任务，并进入原运行详情继续查看进度或成果。

## 范围

- 增加按用户与项目查询最近 WorkflowRun 的只读 API。
- 返回运行 UUID、类型、状态、尝试次数、耗时和创建/更新时间。
- 创作台展示最近生成记录，并提供进入详情的稳定链接。
- 运行历史只依赖服务端持久化事实，不使用 localStorage 保存任务。
- 增加后端权限/排序测试和前端展示映射测试。

## 非目标

- 不增加运行删除、批量操作、跨项目搜索和复杂分页。
- 不改变现有提交、SSE、取消和重试语义。
- 不向列表暴露 Prompt、Token、堆栈或 Artifact 正文。

## 验收标准

- [ ] 项目只能查询当前用户拥有的运行记录。
- [ ] 最近运行按创建时间倒序返回，最多 20 条。
- [ ] PENDING、RUNNING、SUCCESS、FAILED 均有可读状态和详情入口。
- [ ] 刷新页面或重新登录后仍能恢复入口。

## 验证命令

```powershell
cd backend-java
mvn -Dtest=WorkflowRunQueryServiceImplTest,ProjectWorkflowRunQueryControllerTest test

cd ..\frontend-vue
npm run test:unit
npm run build
```

## 完成定义

用户提交任务后可以任意离开页面，并能从项目创作台重新进入同一 `workflowRunUuid` 的进度与结果页面。
