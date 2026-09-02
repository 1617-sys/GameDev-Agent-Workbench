# expose-backend-capabilities-with-role-based-frontend 验证记录

验证时间：2026-09-02（Asia/Shanghai）
分支：`codex/role-based-frontend`
基线提交：`7a31479`

## 工具版本

- Node.js `v24.14.1`，npm `11.11.0`
- Java `21.0.8`，Maven `3.9.11`
- Cocos Creator Product/File Version `3.8.8`

## 自动验证结果

| 范围 | 命令 | 结果 |
| --- | --- | --- |
| 前端全部单元测试 | `npm run test:unit` | 119 passed，0 failed |
| 前端生产构建 | `npm run build` | 成功；Vite 仅报告既有大 chunk 警告 |
| 角色与 V4/V5 浏览器 E2E | `npx playwright test tests/roleAccess.browser.e2e.js tests/v4-v5-surface.browser.e2e.js` | 5 passed，0 failed |
| Java 全量测试 | `mvn test` | 259 passed，0 failed，0 skipped |
| V5/V4/Cocos 相关 Java 回归 | `mvn "-Dtest=GameSpecCompilerTest,CocosBuildWorkerTest,PlayableArtifactAssemblerTest,GenerationRunServiceTest,GenerationPrototypeBridgeServiceTest" test` | 21 passed，0 failed |
| API 严格覆盖门禁 | `node tools/export-openapi-snapshots.mjs; node tools/check-api-coverage.mjs --strict` | 75/75，planned/unknown=0 |

覆盖矩阵的 75 个端点均有 audience、lifecycle、owner、危险级别和可执行测试归属；其中 audience 成员计数为 anonymous 3、普通用户 17、项目高级用户 56、管理员/诊断 11（同一端点可属于多个受众）。lifecycle 为 active 70、deprecated 3、internal 1、non-prod 1。70 个 active 端点映射到受 capability 控制的前端功能；3 个 deprecated 端点仅进入诊断目录并展示替代路径，内部 Machine Episode 批写入和 non-prod Demo 流不进入普通前端功能。

prod 与 non-prod 快照由两个真实 Spring Boot profile 的 `/v3/api-docs` 响应生成，包含 Controller 参数、请求体、响应和 DTO schema；Java 测试会逐字比较规范化后的 SpringDoc 文档，DTO 或 HTTP 契约漂移会直接失败。后端反射门禁还会验证所有非普通用户端点在 Controller 方法或类上具备 `@PreAuthorize`，因此权限不依赖前端隐藏。新增回归覆盖了 Agent Run 并发幂等冲突、Prompt Template 版本 CAS 与不可变审计、以及 V5→V4 桥接对真实来源 artifact 的校验和调用方幂等键绑定。

## 真实 Cocos 构建

使用 `C:\ProgramData\cocos\editors\Creator\3.8.8\CocosCreator.exe`，将受审查的 Runtime Shell 复制到系统临时目录并执行固定 `web-mobile` 构建：

- Creator 退出码：36（项目配置的成功码）
- `index.html`：1
- 文件数：29
- 总字节数：3,465,719
- 临时证据目录：`C:\Users\MECHREVO\AppData\Local\Temp\gameworkbench-cocos-verification-20260902-012341`

服务层测试同时覆盖 GameSpec 编译、durable build claim、preview gate、人工审批、显式发布和 V5→V4 结构化兼容判断。真实 CLI 构建与状态机测试是两层证据；本次没有在连接真实数据库的常驻服务上创建生产数据。

## 已观察并保留的限制

- V5 GenerationRun 不会自动假设兼容 V4 Player。缺少 `playerBridge.contractVersion` 或 `gameConfigArtifactUuid` 时，前端显示后端返回的结构化不兼容原因。
- 全量 Java 测试中的 health 测试可能记录本机依赖健康日志，但本轮 259 项测试全部执行且通过。
- 最终真实账号逐页人工签字仍需由项目验收人员执行；自动化已覆盖三类角色导航、跨角色 URL/API 拒绝和 375px 布局规则。
