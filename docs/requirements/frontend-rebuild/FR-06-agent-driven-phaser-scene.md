# FR-06：Agent 驱动的 Phaser 场景升级

> 状态：`DONE`
>
> 前置任务：`FR-05`
>
> 推荐模型：`gpt-5.5` 设计与审查，`gpt-5.4` 实现

## 目标

保留轻量 `top_down_collect` 游戏类型，同时让 Agent 输出真实影响地图结构、巡逻行为和场景表现，使 Phaser Demo 不再只是固定几何图形验证器。

## 范围

- GameConfig 兼容增加障碍物、巡逻轴和更完整的视觉字段。
- Python Agent Prompt 与 normalizer 输出新增字段，并保留旧字段兼容。
- Phaser 3 使用 Scene、Arcade Physics、碰撞体、程序化纹理、Tween 和输入系统渲染场景。
- 障碍布局、物品、敌人、出口、颜色、标题和目标均来自当前 Artifact。
- 旧 `1.0` GameConfig 通过默认值继续运行。

## 非目标

- 不增加第二种游戏类型、联网、战斗、背包或关卡编辑器。
- 不调用图片生成服务，不把模型输出当作脚本执行。
- 不绕过 GameConfig 校验直接运行任意 AI 代码。

## 验收标准

- [ ] 运行时可证明实例化 Phaser 3 Canvas 和 Arcade Physics。
- [ ] 不同 GameConfig 会产生不同标题、配色、障碍布局、物品数量和敌人巡逻方向。
- [ ] 玩家与障碍碰撞，敌人巡逻，出口锁定并在收集完成后解锁。
- [ ] 旧 Artifact 仍可打开，非法配置安全降级。
- [ ] 桌面和 375px 视口无横向溢出。

## 验证命令

```powershell
cd python-agent
python -m pytest tests/test_game_config_contract.py -q

cd ..\frontend-vue
npm run test:unit
npm run test:runtime
npm run build
```

## 完成定义

真实 DeepSeek 生成的 GameConfig 经校验后进入 Phaser Scene，用户能够从画面与玩法中辨认出本次 Agent 输出，而不是看到固定模板换标题。
