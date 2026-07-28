# V4-14 LLM Player Loop

## 前置条件

V4-13 已通过人工 Review。

## 目标

实现真正逐步调用工具的 LLM Player，并与确定性 Player 共用 Environment Client、Episode 和 Persona 协议。

## 允许修改

- `python-agent/app/clients/llm_client.py` 的结构化异步调用扩展
- `python-agent/app/services/player/**`
- `python-agent/app/prompts/player/**`
- `python-agent/app/schemas/player.py`
- `python-agent/tests/test_llm_player.py`

## 禁止修改

- 让模型生成完整动作序列后一次执行
- 将完整游戏内部状态注入 PERSONA Observation
- 无限重试、无限上下文或静默 mock fallback
- Java、Node 和 UI

## 工作内容

- 每一步将当前 Observation 转成受限上下文；
- 要求模型输出单个结构化 Action；
- 校验、记录并执行 Action，再将环境反馈进入下一轮；
- 设置模型调用、token、决策、重启和墙钟预算；
- 保存 model/prompt/version/digest、usage、延迟、解析错误和 Action；
- mock 仅用于测试，结果必须明确标记。

## 验收标准

- 测试证明第二步决策读取了第一步环境反馈；
- 非法 JSON、非法 Action、provider 超时和预算耗尽有稳定终止结果；
- recorded-decision replay 不重新调用模型；
- 日志和错误不包含密钥、完整 Prompt 或外部响应正文。

## 必须执行

```powershell
cd python-agent
pytest
```
