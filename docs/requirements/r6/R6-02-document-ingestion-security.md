# R6-02: Markdown/TXT/PDF 导入、解析与文件安全

> 状态：`BLOCKED`
>
> 前置任务：`R6-01`
>
> 推荐模型：`gpt-5.5`
>
> 任务类型：文件导入 / 安全边界与异步处理

## 背景

R6 的第一批知识来自项目文档、Runtime 约束与 GameConfig Schema。文件输入是不可信的：路径、伪造 MIME、超大 PDF、恶意文本指令、解析超时都不能影响应用稳定性或被误当作可执行内容。

## 目标

新增受控导入链路：

```text
authenticated project upload
-> validate file name/type/size/hash
-> safe storage reference
-> KnowledgeDocument UPLOADED
-> async parse job
-> extracted plain text + metadata
-> PARSED/FAILED (then R6-03 chunk/index)
```

支持 Markdown、TXT、PDF；将项目内置的 Schema/Runtime 文档按同一受控流程导入或以明确系统来源登记。

## 范围

允许：

- 新增上传/导入 Controller、DTO、Storage abstraction、异步解析服务、状态更新和审计日志。
- 添加 Markdown/TXT/PDF 白名单、content sniffing、文件名规范化、大小/页数/文本长度/解析超时限制、hash 计算。
- 使用安全 PDF 文本提取库或现有受控工具，只输出纯文本与有限 metadata。
- 为解析失败、重复文件、空文档、扫描/解析超时、无权限、删除竞争提供测试。
- 为内置 GameConfig/Runtime 文档定义清晰 sourceType 与版本/来源，不复制敏感配置。

## 非目标

- 不支持 DOCX、图片 OCR、压缩包、音视频、网页 URL 或任意二进制上传。
- 不执行、渲染或预览文档中的 JavaScript/HTML/命令。
- 不在上传 HTTP 请求中同步完成 Embedding/向量索引。
- 不做病毒扫描平台、云对象存储多区域复制或复杂文档编辑器。
- 不实现检索/Prompt 注入/前端知识库浏览页。

## 约束

- 服务端生成存储 key，绝不使用用户文件名作为路径；必须防路径遍历和覆盖。
- MIME、扩展名和文件魔数/内容检测共同校验，校验失败不落入可访问存储。
- 上传/解析内容视为不可信数据，只提取文本；不得作为系统 Prompt、SQL、模板表达式或脚本执行。
- 解析在受限资源/超时边界内异步运行；失败需更新状态与脱敏原因，不能卡住 HTTP 或长期 RUNNING。
- 访问原始文件/解析文本始终检查项目授权；日志只记录安全元数据与 hash。
- 删除/失效与解析并发时，解析结果不能把 DELETED 文档复活为 READY。

## 验收标准

- [ ] 有权限用户可上传限定大小的 Markdown/TXT/PDF，并产生可追溯 KnowledgeDocument。
- [ ] 非白名单、伪造类型、超限、空/无文本、路径遍历、无权限和解析超时被安全拒绝/失败。
- [ ] 解析成功只保存受控纯文本/metadata，文档代码不会执行或被当系统指令。
- [ ] 异步解析失败/删除竞争有稳定状态与审计，不会遗留无限 PARSING。
- [ ] 系统来源 Schema/Runtime 文档可记录其版本与来源，且不泄露配置 Secret。
- [ ] 文件安全、权限、状态和解析测试通过。

## 验证命令

```powershell
cd backend-java
mvn -Dtest=*KnowledgeUpload*Test,*DocumentIngestion*Test,*Pdf*Test,*KnowledgeDocument*Test test
mvn test

cd ..
.\tools\verify.ps1 -Profile quick
.\tools\verify.ps1 -Profile integration
```

## 审查清单

- 是否信任客户端 MIME/文件名或直接拼接文件路径。
- 是否同步解析大文件阻塞 HTTP/事务。
- 是否将文档 HTML/代码当作可执行/可信 Prompt 内容。
- 是否删除后解析任务把文档状态错误更新为 READY。
- 是否将原始内容、路径或敏感信息泄露到日志/错误响应。

## 完成定义

- R6 能安全、可恢复地把受支持文档转换为后续检索的受控文本来源。
- 文件导入的授权、资源和失败边界均有自动化保护。
