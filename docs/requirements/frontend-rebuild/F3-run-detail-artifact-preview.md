# F3：运行详情、产物与游戏预览

> 状态：`TODO`
>
> 前置任务：`F2`
>
> 推荐模型：`gpt-5.5`
>
> 任务类型：异步状态整合 / 结果展示

## 背景

现有前端已经具备 WorkflowRun Store、SSE、步骤、Artifact 和 Phaser 预览能力，但需要把这些能力整理成稳定、可恢复、普通用户能理解的运行详情页。

## 目标

```text
进入 workflowRunUuid 详情
-> 加载服务端快照与步骤
-> SSE 更新或重新加载恢复
-> 可选取消/重试
-> 展示产物与 Phaser 预览
```

## 必读上下文

- `docs/frontend-rebuild-design.md`
- `docs/requirements/frontend-rebuild/F2-project-workbench-submit.md`
- 后端 WorkflowRun 查询、SSE、命令和 Artifact Controller 及返回 VO
- `frontend-vue/src/api/workflowApi.js`
- `frontend-vue/src/stores/workflowRunStore.js`
- `frontend-vue/src/views/WorkflowRunView.vue`
- `frontend-vue/src/components/WorkflowStepper.vue`
- `frontend-vue/src/components/ArtifactLibrary.vue`
- `frontend-vue/src/components/PhaserGamePreview.vue`
- `frontend-vue/src/game/`

## 范围

- 简化运行详情布局，突出状态、步骤、错误、操作和结果。
- 复用 Store 加载运行快照、步骤和 Artifact。
- 复用并修正必要的 SSE 重连、事件去重和资源释放逻辑。
- 根据服务端状态或 capability 展示取消、重试按钮。
- 动作完成后重新获取或合并服务端真实状态。
- 成功后展示结果摘要、Artifact 和可用的 Phaser 游戏预览。
- 覆盖刷新恢复、空 Artifact、网络错误、失败和取消状态。
- 为高风险状态整合补充 Store/视图测试。

## 非目标

- 不重写工作流执行器、SSE 服务端、GameConfig 或 Phaser Runtime。
- 不实现新的游戏类型、在线代码编辑器或任意脚本执行。
- 不把 Debug、Prompt 指标和原始 JSON 放入默认主界面。
- 不用客户端状态表替代服务端状态和 capability。
- 不顺手修改 RAG、评测、监控或发布 Harness。

## 约束

- 服务端快照是最终真相，SSE 只用于增量更新。
- 页面卸载、退出登录或切换运行时必须关闭旧订阅。
- 重连和重复事件不能让状态倒退或重复追加步骤。
- 取消和重试按钮必须有请求中状态，禁止重复动作。
- Artifact 内容不得直接作为不可信 HTML 执行。
- 无有效 GameConfig 时显示可读降级结果，不能让整个页面崩溃。
- 刷新详情页不能依赖提交页中的临时对象。

## 验收标准

- [ ] RUNNING、SUCCESS、FAILED、CANCELED 和网络错误均有稳定展示。
- [ ] 刷新运行详情后可以从服务端恢复状态、步骤和产物。
- [ ] SSE 重连或重复事件不会造成步骤重复、状态倒退或多重订阅。
- [ ] 取消和重试只在服务端允许时可用，完成后展示最新持久化状态。
- [ ] 空 Artifact 和非法 GameConfig 有可理解的降级展示。
- [ ] 合法游戏产物可以进入现有 Phaser 预览。
- [ ] 桌面和 375px 移动视口不存在关键内容遮挡或横向溢出。

## 验证命令

```powershell
cd frontend-vue
npm run test:unit
npm run test:game-config
npm run build

cd ..
git diff --check
git diff -- frontend-vue
```

## 审查清单

- 是否出现未关闭的 SSE、重复订阅或事件监听泄漏。
- 是否让旧事件覆盖更新的服务端快照。
- 是否用乐观 UI 伪造取消、重试或成功终态。
- 是否把模型输出直接插入 HTML 或执行脚本。
- 是否因一个 Artifact 错误导致整页不可用。
- 是否修改了稳定的 GameConfig 和 Runtime 契约。

## 完成定义

- 所有验收标准通过。
- 单元测试、GameConfig 测试和 build 返回 0。
- 高风险 SSE/Store 改动有针对性回归测试。
- 人工可以从运行中状态看到终态、产物和游戏预览。
