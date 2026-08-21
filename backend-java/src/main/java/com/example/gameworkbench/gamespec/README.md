# GameSpec 模块

## 职责

GameSpec 是模型与运行时之间的受约束契约。模型可以提出候选规格，但 Java 编译器拥有最终接纳权。

```text
自然语言创意
  -> SpringAiSpecAuthorModel 生成结构化候选
  -> GameSpecCompiler 封闭字段与语义校验
  -> 诊断回灌模型（最多三轮）
  -> canonical GameSpec
  -> Runtime IR
  -> frozen Build Request
```

## 必须保持的约束

- 当前只支持 `arcade_collect`，未知 archetype、字段、组件和动作必须拒绝。
- JSON 结构合法不代表游戏语义合法；实体边界、类型相关字段和胜利条件由 Java 做跨字段校验。
- 对象字段排序后计算摘要，数组顺序保留。
- `Runtime IR.sourceDigest` 必须绑定 canonical GameSpec。
- `Build Request.runtimeIrDigest` 必须绑定确定的 Runtime IR。
- Prompt、Advisor 和结构化输出都是软约束，不能替代编译器复验。

## 当前限制

- 编译器主要是手写规则，增加 archetype 时需要拆分规则或引入结构 Schema + 语义编译的组合。
- Author 的逐轮尝试目前随 API 响应返回，没有作为独立运行记录持久化。
- GameSpec 不允许模型生成或执行任意代码。
