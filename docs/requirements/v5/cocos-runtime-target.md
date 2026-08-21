# Cocos Runtime Target Contract

> 状态：DRAFT / 待实现
> 引擎版本：Cocos Creator 3.8 LTS，实施时锁定具体 patch 版本与安装包 digest
> 责任：执行、渲染、表现与本地 Web Mobile 构建，不拥有业务审批和 Agent 状态

## 1. 架构边界

```text
Java GameSpec Authority
→ validated runtime IR
→ Cocos Runtime Shell
→ Local Cocos Web Mobile Package
```

Cocos Runtime Shell 是人工维护、版本化和测试的工程。Agent 不直接修改源码、场景、Prefab、meta、shader、插件或构建模板。

## 2. Runtime Shell

首版至少包含：

- Boot、Loading、Menu、Gameplay、Pause、Result 场景状态；
- 数据驱动的 archetype loader；
- 组件和事件注册表；
- 输入适配：键盘、触摸与平台输入；
- telemetry、seed、replay 和 state hash 接口；
- Asset Pack、UI Skin、Animation、Camera、Feedback、Audio profile loader；
- 本地 Web Mobile 启动、下载与遥测适配；平台 adapter 只保留扩展边界，V5 不实现。

Runtime 只接受 Java 已验证且带 digest 的 IR。遇到未知类型、缺失资源或版本不匹配必须 fail closed，不能使用默认值掩盖错误。

## 3. Agent 禁止修改的文件

- `*.scene`
- `*.prefab`
- `*.meta`
- Runtime `*.ts`
- shader、native plugin、editor extension
- `build-templates/**`
- 平台密钥和真实 AppID

这些文件只能由人工 Review 的代码任务修改。Agent 生成内容限制为 GameSpec 和对已注册 profile/asset id 的引用。

## 4. Asset 与表现 Profiles

每个资源包必须提供稳定 id、版本、许可、目标平台、内容 hash 和预览证据。表现配置至少拆为：

- `visualThemeId`
- `assetPackId`
- `animationProfileId`
- `cameraProfileId`
- `feedbackProfileId`
- `uiSkinId`
- `audioProfileId`

组合兼容性由 Java capability registry 校验，Cocos 不在运行时猜测缺失依赖。

## 5. 构建协议

Java向隔离 Build Worker 提交冻结请求：

```json
{
  "runtimeShellVersion": "...",
  "cocosCreatorVersion": "3.8.8",
  "target": "web-mobile",
  "gameSpecDigest": "sha256:...",
  "runtimeIrDigest": "sha256:...",
  "assetPackDigests": ["sha256:..."],
  "buildProfileVersion": "..."
}
```

Worker 使用固定 Cocos 安装、隔离目录和超时执行构建，返回退出码、日志 digest、文件 manifest 和产物 digest。Python Agent 不获得进程执行权限。Worker 必须按锁定版本解释退出码；Cocos 3.8 官方命令行文档以 `36` 表示构建成功，不能硬编码传统的“只有 0 才成功”假设。

Cocos 命令行构建依赖 GUI 环境，因此首版使用受控 Windows Build Worker；不得把“能执行 CLI”误写成无头 Linux CI 已支持。

V5 Build Worker 只允许 `web-mobile` target。微信、抖音、支付宝和其他小游戏平台参数必须被 Java拒绝，直到 V6 明确启用对应 capability。

## 6. Phaser 退役策略

- `v4.0.0`、现有 Phaser 源码、测试和报告继续保留；
- Phaser capability 标记为 `LEGACY_V4`，不再添加新玩法和新表现功能；
- V5 路由、产物和任务使用独立 Cocos 标识，不能覆盖 V4 evidence；
- Cocos 首个 vertical slice 通过前不删除 Phaser；
- 删除旧 Runtime 需单独任务卡、迁移清单和人工批准，不在 V5 初期顺手清理。

## 7. Conformance

V4 TypeScript Simulation 可以暂时作为 `arcade_collect` 的确定性参考，但 Cocos target 必须读取同一组冻结 fixture，验证：

- 初始实体和 seed；
- 移动边界与碰撞关键结果；
- 收集、受伤、胜负和超时；
- 终止原因与关键状态 hash。

若难以维持逐帧完全一致，必须明确划分“Java/Simulation 可验证的高层规则”和“Cocos 表现层状态”，不能悄悄接受双事实源。

## 8. Visual Quality Gate

机器 Gate 检查：

- 必需场景、Prefab、动画、UI、音频和资源引用完整；
- 不存在占位图、远程脚本和未登记资产；
- 首屏、游戏中、胜利和失败截图均可生成；
- 目标分辨率、横竖屏、安全区和触摸输入正确；
- 无致命日志，目标帧率和加载预算达到当前 fixture 门槛。

人工 Gate 检查：

- 风格统一；
- 操作反馈清楚；
- UI 不像管理后台；
- 首次游玩不依赖解释文档；
- 至少存在一个有趣、可感知的玩法变化，而非只换颜色和数值。

只有机器 Gate 和人工 Gate 都通过，才允许将 Artifact 标记为 APPROVED。
