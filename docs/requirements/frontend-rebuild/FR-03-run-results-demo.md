# FR-03：运行详情、Artifact 与 Phaser

> 状态：`DONE`
>
> 前置任务：`FR-02`
>
> 推荐模型：`gpt-5.5`

## 目标

建立以成果为中心的运行详情页，接入服务端快照、SSE、取消、重试、Artifact 内容和 Phaser Demo。

## 范围

- 全新运行 Store、SSE 去重/恢复和页面卸载清理。
- 中文状态摘要、紧凑四步 Stepper 和可读错误。
- 成果、生成过程、技术详情三个标签页。
- Artifact 详情按需加载与中文类型映射。
- 迁移 GameConfig 校验和 Phaser Runtime，不迁移旧展示组件。
- 取消、重试、刷新和页面刷新恢复。

## 非目标

- 不修改 Runner、SSE 服务端和 GameConfig 契约。
- 不执行 Artifact HTML 或脚本。
- 不新增游戏玩法。

## 验收标准

- [ ] 所有运行状态都有稳定展示。
- [ ] SSE 重复/断线不会造成状态倒退和多重订阅。
- [ ] 默认页面优先展示成果与 Demo。
- [ ] 合法 GameConfig 可试玩，非法配置安全降级。
- [ ] 失败时可读错误和允许的重试操作位于同一区域。

## 验证命令

```powershell
cd frontend-vue
npm run test:unit
npm run test:runtime
npm run build
```

## 完成定义

- 用户可从 RUNNING 等待到终态，并查看产物和可玩 Demo。
