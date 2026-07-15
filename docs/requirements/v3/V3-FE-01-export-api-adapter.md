# V3-FE-01：导出 API 与二进制下载适配

> 状态：`DONE`
>
> 前置任务：后端 `V3-06` 已部署，V32 migration 已生效
>
> 执行方式：`VIBE`（契约提示词驱动，小步生成，测试反馈裁决）
>
> 预计工时：30-45 分钟

## 目标

让 Vue 前端完整理解 Prototype Export 后端契约，能够创建、查询、重试导出作业，并安全下载 ZIP，而不改变任何后端接口。

## 冻结输入

| 方法 | 路径 | 响应 |
| --- | --- | --- |
| `POST` | `/api/projects/{projectUuid}/prototype-versions/{versionUuid}/exports` | `PrototypeExportJobVO` |
| `GET` | `/api/projects/{projectUuid}/exports/{jobUuid}` | `PrototypeExportJobVO` |
| `POST` | `/api/projects/{projectUuid}/exports/{jobUuid}/retry` | `PrototypeExportJobVO` |
| `GET` | `/api/projects/{projectUuid}/exports/{jobUuid}/download` | `application/zip` |

`PrototypeExportJobVO` 冻结字段：`jobUuid`、`prototypeVersionUuid`、`status`、`packageName`、`packageDigest`、`packageSize`、`attemptCount`、`errorCode`、`createdAt`、`completedAt`、`reused`。

## 范围

- 新增独立 export API adapter，不把导出逻辑塞进版本或 telemetry adapter。
- 所有动态路径段执行 `encodeURIComponent`。
- 创建请求携带 `Idempotency-Key`，重试绑定已有 `jobUuid`。
- HTTP 层支持带 JWT 的 Blob 下载。
- 从 `Content-Disposition` 安全解析 UTF-8 文件名，缺失时使用稳定默认名。
- ZIP 下载失败时优先解析后端 JSON 错误，不把错误响应保存成 ZIP。
- 增加 API URL、请求头、下载文件名与错误映射单元测试。

## 非目标

- 不新增页面按钮或视觉样式。
- 不修改后端 Controller、VO、错误码或数据库。
- 不引入 Axios、下载库或新的状态管理框架。

## Vibe 执行约束

1. 首个 Prompt 只提供上述四个 API、VO 字段、现有 `http.js` 和相邻 adapter 范例。
2. 每次生成限制在一个 adapter 或一个 HTTP helper，生成后立即运行对应测试。
3. 测试失败时把真实错误输出回灌给模型，禁止凭感觉重写整个 HTTP 层。
4. 人工检查 JWT、错误响应和对象 URL 是否泄漏；`URL.revokeObjectURL` 必须执行。

## 验收标准

- [x] 四个 API 的方法、路径和项目作用域正确。
- [x] 创建请求携带调用方提供的幂等键。
- [x] 下载请求携带 JWT，并返回 `{blob, filename}`。
- [x] 401 继续触发现有未授权处理逻辑。
- [x] JSON 错误响应不会被保存为文件。
- [x] adapter 单元测试和现有前端单元测试通过。

## 验证命令

```powershell
cd frontend-vue
npm run test:unit
npm run build

cd ..
git diff --check
```

## 完成定义

- 页面层可以只通过 export adapter 完成全部导出调用。
- 没有后端修改、依赖扩张或重复 HTTP 实现。
