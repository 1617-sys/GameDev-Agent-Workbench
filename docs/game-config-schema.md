# GameConfig Contract

> 状态：ACTIVE LEGACY TARGET（V4）
> V5 关系：GameConfig 2.0 保留为 V4 Phaser Runtime 契约和 `arcade_collect` 确定性评测投影。V5 的作者层输入是 [GameSpec](requirements/v5/game-spec-language.md)，活跃 Runtime 是 [Cocos Creator Target](requirements/v5/cocos-runtime-target.md)。GameConfig 不再是 V5 的可玩产物契约。

GameConfig 是 AI 数据进入 Phaser Runtime 的唯一执行边界。当前正式契约为：

```text
schema key: game-config
schema version: 2.0
game type: arcade_collect
runtime capability: arcade-collect-runtime/1
```

完整字段、上下限、白名单、安全规则与 1.0 迁移策略以 [V3-00 RFC](requirements/v3/V3-00-lightweight-prototype-rfc.md) 为唯一事实源。规范测试数据位于 [`requirements/v3/examples/game-config-2.0`](requirements/v3/examples/game-config-2.0)：

- `valid-minimal.json`：唯一正式合法示例。
- `invalid-missing-entities.json`：缺少必需结构。
- `invalid-remote-resource.json`：非法远程资源。
- `invalid-out-of-bounds-patrol.json`：越界巡逻。
- `legacy-valid-1.0.json`：受控历史输入。
- `legacy-valid-1.0.migrated.json`：三端必须得到的确定性迁移结果。

处理顺序固定为：

```text
extract registered transport wrapper
-> validate raw declared version
-> migrate valid GameConfig 1.0 when reading history
-> validate complete GameConfig 2.0
-> canonicalize and persist as game-config/2.0
-> mount Phaser runtime
```

新工作流只接受直接的 `2.0/arcade_collect` 输出。`top_down_collect`、`items/collectibles`、`theme`、`rules`、`patrolDistance/range` 和 `controlHint/controls` 仅存在于独立的 1.0 历史迁移器，不是 2.0 alias。

原始输入必须先校验，不能用默认配置补齐缺失的必需结构。未知字段、脚本、HTML、URL、本地路径、data URL、数字字符串、非法资源 key 和越界几何均为 blocking failure。

## Verification

```powershell
cd frontend-vue
npm run test:game-config
npm run test:unit
npm run build

cd ../backend-java
mvn test

cd ../python-agent
python -m pytest
```
