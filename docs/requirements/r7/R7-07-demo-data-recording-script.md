# R7-07: Demo 数据、3-5 分钟演示与录屏脚本

> 状态：`TODO`
>
> 前置任务：`R7-01`、`R7-02`、`R7-05`
>
> 推荐模型：`gpt-5.4`
>
> 任务类型：可重复演示 / 展示资产

## 背景

面试演示时间短，现场网络和模型 Provider 不稳定。一个好的工程项目需要既能展示真实链路，也有明确标记的离线受控 Demo，避免演示时等待模型、数据缺失或无法解释页面上的指标。

## 目标

建立可重复演示包：

```text
safe demo profile + deterministic seed
-> demo user/project/knowledge/prompt/workflow fixtures
-> 3-5 minute operator script
-> optional real-provider segment clearly marked
-> screenshot/recording checklist
-> cleanup/reset command
```

演示脚本应覆盖异步提交、运行中心、并发/可靠性亮点、评测指标、RAG 引用和 Phaser Demo，而不是逐页念功能。

## 范围

允许：

- 新增 demo seed、fake/mock Provider profile、示例知识文档、固定 GameConfig/评测/指标数据和安全清理脚本。
- 编写 `docs/demo-script.md`、录屏镜头表、讲解词、预检查、失败备用路线和 3-5 分钟时间分配。
- 准备真实链路演示与离线 deterministic 演示两套模式，并显式标记 mock/真实 Provider。
- 增加 seed 幂等、重置安全、演示流程 smoke test 与录屏前检查。
- 生成不含个人隐私/密钥的截图、日志片段和报告引用清单。

## 非目标

- 不伪造真实模型结果、并发报告、性能数据或 RAG 证据。
- 不把个人 API Key、账号、聊天内容或本机绝对路径放入录屏/仓库。
- 不制作长篇营销视频、复杂视频剪辑工程或新 UI 功能。
- 不让 demo reset 默认删除开发/生产数据。
- 不用演示脚本替代 README 和自动化 E2E。

## 约束

- demo 数据可重复导入且有独立 namespace/profile；重复运行不产生无限重复记录。
- mock/fake 在 UI、脚本和口播中明确说明，不暗示为真实模型。
- 清理只作用 demo user/project/namespace，破坏性命令需确认。
- 演示前检查必须验证 Docker health、端口、seed、浏览器、模型模式和备用截图/报告。
- 讲解优先使用 workflowRunUuid/trace/指标/报告作为证据，不展示 Secret 和原始敏感 Prompt。
- 3-5 分钟脚本包含明确时间点和“网络/Provider 失败时切换离线模式”的路径。

## 验收标准

- [ ] 一条命令可准备安全、幂等的 Demo 数据与受控模型模式。
- [ ] 3-5 分钟脚本可稳定展示提交、异步执行、运行恢复、评测/RAG 和 Phaser Demo。
- [ ] mock/真实 Provider、预录/实时环节和报告数据均明确标记。
- [ ] 演示失败有备用路径，不需要修改代码或数据库救场。
- [ ] reset 只清理 Demo namespace，不影响其他项目数据。
- [ ] 录屏/截图/讲解材料不含 Secret、隐私和本机敏感路径。

## 验证命令

```powershell
.\tools\prepare-demo.ps1
.\tools\verify-demo.ps1
.\tools\reset-demo.ps1
```

## 审查清单

- 是否 mock 结果被包装成真实模型。
- 是否 seed/reset 可能污染或删除非 Demo 数据。
- 是否脚本依赖个人账号、API Key 或历史数据库。
- 是否演示只讲页面，没有工程证据和设计取舍。
- 是否没有网络/Provider 故障备用路径。

## 完成定义

- 项目可以在有限时间内稳定、诚实地展示最有价值的业务链和工程亮点。
- 演示资产可由他人复现，不依赖开发者临场操作记忆。
