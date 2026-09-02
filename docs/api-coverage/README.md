# HTTP 端点覆盖矩阵

分类事实文件为 `endpoints.json`，由 Spring MVC 控制器反射测试校验端点完整性；生产/非生产 OpenAPI 快照来自对应 Spring Boot profile 的真实 SpringDoc `/v3/api-docs` 文档，前端清单只包含 active 且非 internal 的端点。

当前统计：

- 后端唯一 HTTP 端点：75
- 已进入覆盖矩阵：75（100%）
- active：70；internal：1；deprecated：3；non_prod：1
- active 前端映射：70；planned/unknown：0
- internal/non_prod 不进入普通前端清单；deprecated 仅进入管理员诊断目录并展示替代路径

运行严格门禁：

```powershell
node tools/export-openapi-snapshots.mjs
node tools/check-api-coverage.mjs --strict
mvn -f backend-java/pom.xml -Dtest=ApiSurfaceCoverageTest test
```

## 保留但不向普通用户暴露的接口

| Lifecycle | 端点 | 所有者 | 替代/入口 |
| --- | --- | --- | --- |
| non_prod | `POST /api/demo/game/stream` | developer-experience | `/demo/play`，仅非生产 profile 且要求管理员诊断 capability |
| internal | `POST /api/projects/{projectUuid}/machine-episodes/batches` | player-runtime | `POST /api/projects/{projectUuid}/player-runs` |
| deprecated | `POST /api/promptTemplate/modify` | ai-platform | `PUT /api/promptTemplate/{templateUuid}` |
| deprecated | `POST /api/workflow/game-design/run` | workflow | `POST /api/v1/projects/{projectUuid}/workflow-runs` |
| deprecated | `GET /api/workflow/{workflowRunUuid}` | workflow | `GET /api/v1/workflow-runs/{workflowRunUuid}` |

完整保留理由、危险等级、profile 和测试所有权见 `endpoints.json`；诊断页读取同源生成的 `diagnosticCatalog.json`。
