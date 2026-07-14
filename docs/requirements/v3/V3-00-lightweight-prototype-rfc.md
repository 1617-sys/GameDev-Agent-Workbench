# V3-00：轻量游戏原型 RFC

> 状态：`TODO`
>
> 前置任务：V2.1 F1-F4 验收完成
>
> 推荐模型：`gpt-5.5`
>
> 预计工时：4-6 小时

## 目标

在修改业务代码前，冻结 V3.0 的产品范围、GameConfig 2.0 字段、原型版本、试玩会话、事件协议和兼容策略，消除 Python Prompt、Java 校验器和 Phaser Runtime 的字段分歧。

## 必读上下文

- `docs/v3-lightweight-game-prototype-design.md`
- `docs/game-config-schema.md`
- 当前 Python GameConfig Prompt
- Java GameConfig 校验与 Runtime capability
- 前端 `gameConfig.js`、默认配置和 Phaser Runtime
- 与工作流 Artifact、幂等、评测直接相关的现有模型

## 范围

- 编写 V3 RFC 和 GameConfig 2.0 草案及合法/非法示例。
- 冻结 `arcade_collect` 的字段、边界值、默认值和白名单资源 key。
- 定义 `top_down_collect` 1.0 到 2.0 的迁移规则。
- 定义 PrototypeVersion、PlaytestSession、Telemetry Event 的最小领域契约。
- 定义版本创建、事件批次和导出的幂等语义。
- 建立 Python、Java、Vue、文档字段映射矩阵和任务顺序。

## 非目标

- 不实现数据库、接口、Runtime 和页面。
- 不加入 Galgame、第二游戏模板或微信原生发布。
- 不在 RFC 中提前设计所有未来游戏类型。
- 不修改 V2 现有工作流语义。

## 约束

- 每个字段必须有明确消费方；没有消费方的字段不进入 2.0。
- 默认值不能掩盖缺失的必需结构。
- AI 输出只能包含数据，不能包含脚本、HTML 和远程资源 URL。
- RFC 必须明确并发、失败、安全、兼容和废弃策略。
- 不允许 Python、Java 和 Vue 各自维护不同示例。

## 验收标准

- [ ] GameConfig 2.0 字段、类型、上下限和示例完整。
- [ ] 当前已知字段错位全部在映射矩阵中处理。
- [ ] 1.0 输入的接受、迁移、拒绝和持久化策略明确。
- [ ] 原型版本不可变和父子版本关系明确。
- [ ] Telemetry 事件、去重键、批次限制和隐私边界明确。
- [ ] 后续任务不需要重新猜测核心契约。

## 验证命令

```powershell
git diff --check
rg -n "top_down_collect|arcade_collect|GameConfig" docs python-agent backend-java frontend-vue/src/game
```

## 完成定义

- RFC 通过人工审查并标记 `ACCEPTED`。
- diff 仅包含设计、契约和必要示例文档。
- 能用一张字段矩阵解释 Python 生成、Java 校验和 Phaser 消费的一致关系。

