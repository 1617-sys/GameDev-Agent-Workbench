# GameSpec 语言契约

> 状态：DRAFT / 待实现
> 定位：面向 Agent 和人类作者的受限游戏描述语言，不是可执行脚本

## 1. 设计原则

- 声明式：描述实体、组件、规则、事件和约束，不嵌入 JavaScript。
- 封闭世界：组件、事件和资源类型必须来自 capability registry。
- 版本化：每份规格声明 `specVersion` 和 `archetype`。
- 可定位：任何错误都能映射到稳定 JSON path。
- 可迁移：升级由显式 migration 完成，不靠模型临场猜测。
- 可编译：自然语言字段不影响运行语义和 digest。

## 2. 首版文档形态

首版使用 JSON 作为传输与存储语法，Java 映射为类型化 AST。等契约稳定后再评估更友好的 YAML/编辑器语法；不得同时维护多种事实格式。

```json
{
  "specVersion": "0.1",
  "archetype": "arcade_collect",
  "metadata": { "title": "Forest Collector" },
  "world": { "width": 960, "height": 540, "timeLimitSeconds": 90 },
  "player": { "movement": "four_way", "speed": 180, "health": 3 },
  "entities": [],
  "rules": [],
  "presentation": {
    "visualThemeId": "forest-01",
    "assetPackId": "forest-adventure-01",
    "animationProfileId": "topdown-character-01",
    "cameraProfileId": "follow-soft-01",
    "feedbackProfileId": "arcade-juice-01",
    "uiSkinId": "forest-hud-01",
    "audioProfileId": "forest-light-01"
  }
}
```

示例只说明文档轮廓，不是已冻结 schema。

## 3. 核心节点

| 节点 | 责任 | 首版限制 |
| --- | --- | --- |
| `metadata` | 标题与非运行说明 | 不进入玩法 digest |
| `world` | 尺寸、时限、边界 | 有明确数值范围 |
| `player` | 受支持的移动/生命组件 | 不允许脚本行为 |
| `entities` | 收集物、障碍、敌人、出口 | type 必须在 registry |
| `rules` | 白名单事件到白名单动作 | 不支持任意表达式 |
| `presentation` | Cocos Asset/Animation/Camera/Feedback/UI/Audio profiles | 只允许注册 id，禁止远程 URL |

## 4. 规则表达

规则使用受限的 `when / if / then` 结构。事件、条件和动作都有类型化参数；编译器负责引用解析和类型检查。

```json
{
  "when": "collectible.collected",
  "if": { "counter": "remainingCollectibles", "equals": 0 },
  "then": [{ "action": "exit.unlock" }]
}
```

首版不支持循环、递归、动态代码、网络请求、文件访问、反射或自定义表达式函数。

## 5. 身份与引用

- 所有可引用对象使用规格内唯一 `id`。
- 引用必须在同一 GameSpec 或已锁定的内置 capability 中解析。
- 禁止隐式按名称猜测、大小写模糊匹配和“找不到就创建”。
- 资源引用使用逻辑 asset id，由 manifest 解析到包内文件。
- GameSpec 不得出现 Cocos UUID、磁盘路径、scene/prefab 路径、组件类名或平台构建参数。

## 6. 诊断要求

编译错误至少覆盖：

- `GS1001_UNKNOWN_FIELD`
- `GS1101_UNKNOWN_COMPONENT`
- `GS1201_UNRESOLVED_REFERENCE`
- `GS1301_VALUE_OUT_OF_RANGE`
- `GS1401_UNSUPPORTED_CAPABILITY`
- `GS1501_REMOTE_ASSET_FORBIDDEN`
- `GS1601_UNREACHABLE_WIN_CONDITION`
- `GS1701_INCOMPATIBLE_PRESENTATION_PROFILE`

诊断 code 一旦进入 Agent 工具协议就保持兼容；消息文本可以优化，code 语义不能漂移。

## 7. 与 GameConfig 2.0 的关系

GameSpec 是 V5 作者层事实。Java将其规范化为 Cocos Runtime IR 和构建请求；为了复用 V4 确定性评测，首个 `arcade_collect` target 可以额外生成兼容的 GameConfig 2.0 simulation projection。该 projection 只服务测试和历史兼容，不是 V5 可玩 Runtime，也不能绕过 GameSpec 直接成为 V5 产物。

第二 archetype 落地前，GameSpec 只能宣称“具备可扩展结构”，不能宣称“已验证通用性”。
