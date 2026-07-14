# V3-01：GameConfig 2.0 契约统一

> 状态：`TODO`
>
> 前置任务：`V3-00`
>
> 推荐模型：`gpt-5.4` 实现，`gpt-5.5` 契约审查
>
> 预计工时：8-12 小时

## 目标

让 Python Agent、Java 校验器、Vue 提取/归一化逻辑、Phaser Runtime 和文档使用同一份 GameConfig 2.0 契约，并为 1.0 历史 Artifact 提供受控迁移。

## 范围

- 更新 Python Prompt 和结构化输出，使其只生成 RFC 允许字段。
- 更新 Java Schema、规则和 Runtime capability 校验。
- 更新前端提取、迁移、校验和归一化逻辑。
- 提供共享 fixture 或机械同步机制，减少多语言示例漂移。
- 增加合法、缺字段、越界、非法资源、历史 1.0 和不支持模板测试。
- 校验失败时保存可读错误，不使用默认配置伪装成功。

## 非目标

- 不实现新的 Phaser 玩法能力。
- 不实现版本管理、Telemetry 和导出。
- 不调用模型执行在线测试，契约测试必须离线运行。
- 不接受任意 alias 作为长期兼容方案。

## 约束

- 原始输入先校验，合法后才能归一化可选字段。
- 迁移结果统一持久化为 `2.0` 和 `arcade_collect`。
- Python Prompt 示例必须能通过 Java 和前端同一组契约测试。
- 任意脚本、HTML、远程 URL、路径穿越和未知模板必须拒绝。
- 不破坏已有合法 1.0 Artifact 的只读预览。

## 验收标准

- [ ] Python 新输出、Java 校验和前端 Runtime 字段完全一致。
- [ ] 当前 `theme`、`patrolDistance/range`、`controlHint/controls`、`rules` 等错位消失。
- [ ] 合法 1.0 fixture 可以确定性迁移，非法输入不能靠默认值通过。
- [ ] 2.0 边界值、资源白名单和模板类型有自动化测试。
- [ ] 文档示例通过同一契约测试。

## 验证命令

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

## 审查清单

- 是否让默认值掩盖 AI 缺失的必需字段。
- 是否只更新 Prompt 而遗漏 Java 或前端消费者。
- 是否保留未经测试的历史 alias。
- 是否把不可信内容传给 Runtime。

## 完成定义

- 所有契约测试和构建通过。
- GameConfig 2.0 成为唯一新写入格式。
- diff 不包含 Runtime 功能扩张和任务外重构。

