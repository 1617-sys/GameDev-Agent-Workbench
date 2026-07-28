# V4-11 Python Environment Client

## 前置条件

V4-10 已通过人工 Review。

## 目标

在 Python Agent 服务中建立类型化、可超时、可关闭的 Simulation Environment Client。

## 允许修改

- `python-agent/app/clients/simulation_client.py`
- `python-agent/app/schemas/player.py`
- `python-agent/app/services/player/**`
- `python-agent/tests/test_simulation_client.py`
- Python/Compose 环境变量的最小调整

## 禁止修改

- 现有生成 Agent 接口
- Node Simulation Core、Java 和前端页面
- 在 Client 中实现玩法策略

## 工作内容

- 映射 create/observe/step/close；
- 校验协议版本和响应结构；
- 设置 connect/read/total timeout 与有限重试；
- 使用 correlation/episode ID 和内部 token；
- async context manager 保证成功、异常和取消时关闭会话；
- 对外暴露领域错误，不泄露 HTTP 响应正文。

## 验收标准

- mock server 覆盖正常、401、超时、错误 JSON、版本不兼容和 close 失败；
- 重试不重复执行不安全 step；
- FastAPI event loop 不被同步 HTTP 调用阻塞。

## 必须执行

```powershell
cd python-agent
pytest
```
