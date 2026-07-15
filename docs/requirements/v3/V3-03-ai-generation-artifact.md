# V3-03：AI 生成与资源 Artifact 闭环

> 状态：`DONE`
>
> 前置任务：`V3-01`、`V3-02`
>
> 推荐模型：`gpt-5.4`
>
> 预计工时：8-12 小时

## 目标

让 Prototype Brief 通过现有异步工作流稳定产出游戏概念、核心循环、GameConfig 2.0 和资源清单，并在运行详情中打开正式化 Runtime。

## 范围

- 收敛 Prototype Brief 的主题、时长、难度、视觉主题和补充要求。
- 调整 Python Agent 各步骤的上下游输入和 Artifact 类型。
- 生成并校验 GameConfig 2.0 与内置资源 manifest 引用。
- Java 工作流保存概念、核心循环、配置、资源清单和校验报告。
- 前端从 Artifact 中选择经过验证的配置并打开试玩。
- 无效输出明确失败，支持沿用现有工作流重试机制。
- 增加 Mock Agent 下的稳定集成测试，真实模型只做人工 smoke。

## 非目标

- 不生成图片、音频或任意游戏代码。
- 不实现原型调参、试玩统计和导出压缩包。
- 不重写已有 MQ、Outbox、重试和 SSE 架构。
- 不增加 Prompt 市场和复杂工作流编辑器。

## 约束

- Runtime 只能使用通过 Java 契约和规则校验的 Artifact。
- 资源只能引用内置 manifest key。
- 模型失败、JSON 无效和 Runtime 不支持必须是可观察的失败状态。
- 真实模型测试不能成为 CI 的强依赖。
- 同一工作流重试不得产生无法辨别来源的重复有效 Artifact。

## 验收标准

- [x] Brief 可以异步生成完整且可追溯的 Artifact 集合。
- [x] GameConfig 和资源 manifest 均经过服务端校验。
- [x] 运行详情只预览已验证配置。
- [x] 无效模型输出不会回退到默认 Demo 冒充成功。
- [x] Mock 模式下主生成链路可重复验证。

## 验证命令

```powershell
cd python-agent
python -m pytest

cd ../backend-java
mvn test

cd ../frontend-vue
npm run test:unit
npm run build

cd ..
.\tools\verify.ps1 -Profile quick
```

## 完成定义

- 从 Brief 到 Phaser 试玩的 Artifact 链路可重复运行。
- 失败边界和 Artifact 来源可解释。
- diff 复用现有异步基础设施，没有平行实现第二套工作流。

