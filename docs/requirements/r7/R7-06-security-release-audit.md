# R7-06: 安全、依赖、配置与数据发布审计

> 状态：`TODO`
>
> 前置任务：`R7-01`、`R7-02`、`R7-05`
>
> 推荐模型：`gpt-5.5`
>
> 任务类型：发布安全审计 / 回归门禁

## 背景

R0 已清理弱 Secret 和鉴权旁路，后续阶段又加入文件上传、SSE、MQ、Prompt、模型指标、知识库和向量检索。最终发布前需要重新检查跨项目权限、输入边界、依赖、配置和敏感数据泄露。

## 目标

完成面向发布候选的安全审计：

```text
authentication/authorization
file upload and path safety
project isolation and RAG metadata filters
SSE/query/cancel/retry ownership
Prompt/model/document/output data exposure
secrets/config/logs/images
dependency/container/migration review
-> report + automated gates + residual risks
```

## 范围

允许：

- 审查 Java/Python/Vue/Docker 配置、鉴权、CORS、上传、API/SSE、项目隔离、日志、错误、数据查询和测试入口。
- 运行 secret pattern、依赖漏洞、容器基础镜像、npm/maven/python 包和 migration 安全检查；工具结果需人工分级。
- 增加跨用户访问、UUID 猜测、上传伪造/路径遍历、Prompt injection、敏感日志和测试 endpoint 生产关闭测试。
- 清理阻断发布的真实 Secret/弱默认/调试接口/高危依赖或记录无法消除的风险与缓解。
- 生成 `docs/reports/R7-security-release-audit.md`。

## 非目标

- 不宣称通过一次扫描即达到合规认证或绝对安全。
- 不进行破坏性渗透、外部目标扫描或真实凭证攻击。
- 不无条件升级所有依赖到最新大版本。
- 不删除业务审计证据来“减少风险”。
- 不把安全修复扩大为无关架构重写。

## 约束

- 扫描输出不得把发现的 Secret 原文复制进报告/日志；只记录脱敏位置和处置。
- 所有项目数据 API/向量检索/SSE/Artifact/Metric/RetrievalRecord 都需 user/project 双重授权测试。
- 上传/文档/模型输出始终不可信，不执行脚本、SQL、模板表达式或系统命令。
- 示例/测试 profile 与生产 profile 隔离，调试/fixture/fault endpoint 默认生产关闭。
- 依赖升级按风险和回归范围分组，每组运行相关 Harness。
- migration 与数据清理只前进且可恢复，不运行破坏性回滚。

## 验收标准

- [ ] Git tracked 文件、镜像、配置、日志和报告中没有真实 Secret/弱默认凭证。
- [ ] 跨用户/项目查询、SSE、命令、Artifact、Metric、知识/向量/检索证据均被授权测试保护。
- [ ] 文件上传、路径、MIME、大小、文档 Prompt injection 和模型输出执行风险有明确门禁。
- [ ] 生产 profile 不暴露测试、mock、fixture、fault 或内部诊断能力。
- [ ] 高危依赖/镜像问题已修复或在报告中记录影响、缓解和接受理由。
- [ ] 安全审计报告与全量功能/可靠性回归通过。

## 验证命令

```powershell
git diff --check
docker compose config

cd backend-java
mvn test

cd ..\python-agent
python -m pytest

cd ..\frontend-vue
npm audit
npm run build

cd ..
.\tools\verify.ps1 -Profile quick
.\tools\verify.ps1 -Profile integration
```

## 审查清单

- 是否扫描报告泄露了 Secret 原文。
- 是否只检查 Controller，Mapper/VectorStore 仍可跨项目访问。
- 是否测试/fault endpoint 在生产 profile 可用。
- 是否为清零漏洞分数盲目升级并破坏兼容。
- 是否把“未发现”写成“绝对安全”。

## 完成定义

- 发布候选的权限、输入、依赖、配置和敏感数据风险拥有可复现审计证据。
- 剩余风险有明确影响、缓解和接受决定，而非被隐藏。
