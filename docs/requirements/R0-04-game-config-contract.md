# R0-04：GameConfig 契约测试

> 状态：`READY_AFTER_R0-01`
>
> 前置任务：`R0-01`
>
> 推荐模型：`gpt-5.4`
>
> 任务类型：跨 AI 输出与 Phaser Runtime 的数据契约

## 背景

GameConfig 是 AI 输出进入 Phaser Runtime 前的安全边界。当前已有：

- `defaultGameConfig`
- `extractGameConfig`
- `normalizeGameConfig`
- `validateGameConfig`
- `docs/game-config-schema.md`

但没有自动化测试，而且文档与实际实现存在字段差异，例如：

- 文档使用 `collectibles`，实际默认配置使用 `items`。
- 文档使用 `winCondition`，实际实现使用 `rules`。
- normalize 会填充默认值，可能让缺失必填字段的原始输出看起来合法。

本任务建立可执行契约，并仅修复测试直接证明的最小验证问题。

## 目标

确保只有满足当前 Phaser Runtime 契约的配置被判定为有效：

```text
原始 AI 输出
-> 提取 GameConfig
-> 校验必填结构和类型
-> 合法时 normalize
-> 交给 Phaser Runtime
```

## 代码入口

- `frontend-vue/src/game/gameConfig.js`
- `frontend-vue/src/game/defaultGameConfig.js`
- `frontend-vue/src/game/topDownCollectRuntime.js`
- `frontend-vue/src/game/GameDemoPage.vue`
- `frontend-vue/src/components/PhaserGamePreview.vue`
- `frontend-vue/package.json`
- `docs/game-config-schema.md`
- `python-agent/app/services/langchain_agent.py`

## 范围

允许：

- 使用 Node 内置 test runner 新增 GameConfig 单元测试。
- 为 `frontend-vue/package.json` 增加测试脚本。
- 增加合法与非法 JSON fixture。
- 固定实际支持的 `top_down_collect` 契约。
- 修复由测试暴露的最小 validator/extractor 问题。
- 同步 `docs/game-config-schema.md` 与真实字段。

## 非目标

- 不新增 Vitest、Jest 或其他测试依赖。
- 不重写 Phaser Runtime。
- 不增加第二种游戏玩法。
- 不修改 UI 设计。
- 不修改 Workflow 或 Redis。
- 不接入浏览器 E2E。
- 不让 LLM 参与确定性 Schema 判断。
- 不扩大为完整 JSON Schema 平台。

## 约束

- 契约以当前 Runtime 实际可消费字段为准。
- 原始缺失必填字段不能仅靠默认值伪装成合法 AI 输出。
- normalize 可以用于兼容可选字段，但不能绕过关键结构校验。
- 非法 JSON 必须安全失败，不能抛出未捕获异常。
- 不执行模型输出中的脚本或函数字符串。
- 测试可离线运行。

## 必测场景

### 合法配置

- `defaultGameConfig` 校验通过。
- 合法 JSON 字符串可提取。
- `game_config`、`gameConfig`、`data` 包装结构可提取。
- Artifact 列表优先选择 GameConfig 类型。

### 非法配置

- 非法 JSON 返回无效结果。
- 不支持的 `gameType` 被拒绝。
- `world.width/height` 非数字被拒绝。
- `player.x/y` 非数字被拒绝。
- `items` 或 `enemies` 非数组被拒绝。
- `exit.x/y` 非数字被拒绝。
- 原始输入完全缺少关键结构时不能因为默认值而通过。

### 兼容与归一化

- `game_type` 可以归一化为 `gameType`。
- 已确认的历史字段别名有测试后才能保留。
- 文档示例必须能通过同一测试。

## 验收标准

- [ ] 前端拥有无需新增依赖的 GameConfig 测试命令。
- [ ] `defaultGameConfig` 通过。
- [ ] 包装结构提取有测试。
- [ ] 非法 JSON 和关键字段类型错误被拒绝。
- [ ] 不支持的游戏类型被拒绝。
- [ ] 空对象或无关键结构对象不能伪装成有效 AI 输出。
- [ ] 文档字段与 Runtime 字段一致。
- [ ] 只做测试证明所需的最小实现修改。
- [ ] GameConfig 测试、Vue build 和 quick Harness 通过。

## 验证命令

具体 npm script 名称在实现时确定，建议：

```powershell
cd frontend-vue
npm run test:game-config
npm run build

cd ..
.\tools\verify.ps1 -Profile quick
```

## 审查清单

- 测试是否先验证原始结构，再接受默认值。
- 文档与 `defaultGameConfig` 是否使用同一字段。
- 是否错误拒绝已有合法 Artifact。
- 是否新增未经需求确认的玩法字段。
- 是否引入额外测试依赖。
- 是否触碰 Phaser 玩法实现。

## 完成定义

- 所有验收标准通过。
- 测试和 build 可离线重复运行。
- quick Harness 返回 0。
- 文档示例与测试 fixture 一致。
- diff 只涉及 GameConfig 契约、测试、必要 npm script 和对应文档。
- 你能解释“提取、校验、归一化”三步为什么不能混为一谈。

