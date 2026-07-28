# V4-15 Python Player API

## 前置条件

V4-14 已通过人工 Review。

## 目标

向 Java 后端提供受保护的 Player Episode 单局/批量执行 API，不承担项目权限和最终持久化。

## 允许修改

- `python-agent/app/routers/player.py`
- `python-agent/app/main.py`
- `python-agent/app/schemas/player.py`
- `python-agent/app/services/player/**`
- `python-agent/tests/test_player_api.py`

## 禁止修改

- 公开匿名访问
- Python 回调 Java 持久化
- 在请求中接受任意 Prompt、URL 或可执行代码
- 修改旧 Agent API 行为

## 工作内容

- 提供内部单局和批量端点；
- 只接受注册的 policy/persona/version；
- 限制 batch、并发、步数、模型调用和请求体；
- 返回 Episode Protocol 结果及部分失败；
- 传播 trace ID，支持客户端取消并关闭环境会话。

## 验收标准

- 内部 token、协议校验、限额和取消测试通过；
- 同批次单项失败不丢弃成功结果；
- API 返回可直接映射到 Java V4-08 持久化 DTO；
- mock 与真实 LLM 结果不会混淆。

## 必须执行

```powershell
cd python-agent
pytest
```
