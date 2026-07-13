# R6-08: 知识库与检索证据前端展示 Harness

> 状态：`BLOCKED`
>
> 前置任务：`R4-验收`、`R6-02`、`R6-06`、`R6-07`
>
> 推荐模型：`gpt-5.4`（页面搬运） / `gpt-5.5`（隔离与证据审查）
>
> 任务类型：前端可解释性 / E2E 与权限验证

## 背景

R4 的运行中心能够展示 WorkflowRun，R5 能展示评测和指标。R6 需要让用户管理项目知识并查看某次 AgentRun 使用了哪些来源，但不能把 RAG 变成“任意文档浏览器”或泄露其他项目内容。

## 目标

新增两个受控视图：

```text
Project Knowledge Library
-> upload/list/status/invalid/delete documents
-> document version/source/index status/error summary

WorkflowRun / AgentRun Retrieval Evidence
-> rag enabled flag
-> selected chunk/document source, rank, score, version
-> safe excerpt/reference, not full secret document
-> RAG-on/RAG-off comparison summary
```

## 范围

允许：

- 扩展 R4 API client/Store/Router，新增 knowledge/retrieval API 封装和视图/组件。
- 支持上传受限文件、列出本项目文档状态、显示解析/索引失败摘要、标记失效/删除（按后端 capability）。
- 在 Run/Agent 详情显示 RAG 开关、实际 RetrievalRecord、rank/score/source/version 和安全摘要。
- 显示 R6-07 对照报告的核心样本/通过率/成本/延迟变化及“样本不足/Mock/无检索”状态。
- 添加组件、权限、上传错误、状态轮询/订阅、桌面/375px E2E 和截图检查。

## 非目标

- 不在浏览器直接访问 VectorStore、执行检索、计算 score 或拼装 Prompt。
- 不提供全文下载、任意跨项目搜索、文档代码运行或敏感原文浏览器。
- 不实现复杂知识图谱、在线文档编辑、聊天式全库问答。
- 不重新实现 R5 评测/指标图表或 R4 SSE 生命周期。
- 不隐藏/篡改 mock、RAG disabled 或检索失败的真实状态。

## 约束

- 所有视图仅展示后端授权后的项目数据；路由参数/UUID 不能绕过权限。
- 文档正文默认不进入列表/事件；只展示安全 excerpt、来源、版本、hash 摘要和状态。
- 上传按钮和状态基于后端响应/能力，不能仅靠前端 MIME 校验；失败不乐观标 READY。
- Retrieval Evidence 只展示实际使用的 RetrievalRecord，不能临时重新检索或展示未选择候选。
- RAG-on/RAG-off 对比必须标注样本、版本、mock/缺失数据与统计窗口，不能形成绝对质量宣称。
- 375px 与桌面均保持文本折行、按钮可点、长文档名/violation 不溢出且无重叠。

## 验收标准

- [ ] 用户可上传/查看/失效本项目受支持知识文档，并看到真实解析/索引状态与安全错误摘要。
- [ ] 用户无法在 UI/URL 中读取或操作其他项目的 Document、Chunk、RetrievalRecord。
- [ ] Run/Agent 详情可区分 RAG-on、RAG-off、空候选、检索失败和 mock，并展示实际引用来源。
- [ ] 对照结果明确展示样本、版本、通过率、延迟/成本与缺失/Mock 状态。
- [ ] 文档/来源摘要不泄露完整 Prompt、Secret 或敏感原文，前端不执行文档内容。
- [ ] 单元/E2E、移动/桌面截图、Vue build 和 R4 回归测试通过。

## 验证命令

```powershell
cd frontend-vue
npm run test:unit
npm run test:e2e
npm run test:runtime-smoke
npm run build

cd ..
.\tools\verify.ps1 -Profile e2e
.\tools\verify.ps1 -Profile quick
```

## 审查清单

- 是否让前端直接检索向量库或根据 score 自行决定引用。
- 是否显示任意文档全文/跨项目来源而非授权后的安全摘要。
- 是否把未选择的候选伪装成实际 Agent 引用。
- 是否把 mock/无检索/检索失败画成 RAG 成功。
- 是否在移动端造成长文档名、表格或按钮重叠/溢出。

## 完成定义

- 用户可安全管理项目知识并理解每次 RAG 生成的来源与对照影响。
- R6 的可解释性从数据库证据延伸到受控运行中心体验。
