# V4-21 Python LangGraph Director

## 前置条件

V4-19 已通过人工 Review。

## 目标

使用 LangGraph 实现有界 Director 决策图：接收 Java 提供的状态快照和最近工具结果，每轮输出一个结构化决策。

## 允许修改

- `python-agent/requirements.txt`，新增并固定兼容的 LangGraph 版本
- `python-agent/app/schemas/director.py`
- `python-agent/app/services/director/**`
- `python-agent/app/prompts/director/**`
- `python-agent/app/routers/director.py`
- `python-agent/app/main.py` 的路由接线
- `python-agent/tests/test_director_*.py`

## 禁止修改

- Python 直接调用 Java 工具或数据库
- 在 Python 内保存业务权威 checkpoint
- 输出自由文本工具参数
- 修改旧生成 Agent 和 Player API 行为

## 工作内容

- 实现 goal normalization、plan、select-next-action、finalize/fail 节点；
- 工具名称和参数严格来自 Java 传入的 allowlist/schema；
- 每轮输出一个 `DirectorDecision`，包含 reason summary、decision digest 和模型证据；
- 支持 deterministic fake model 测试；
- 限制上下文、轮次、token、成本和决策超时；
- 解析失败和模型故障不得静默变成成功。

## 验收标准

- 测试覆盖调用工具、等待审批、完成、失败和预算耗尽；
- 第二轮决策确实消费第一轮工具结果；
- 未注册工具、额外参数和 Prompt injection 不能进入 ToolCall；
- 相同 mock 输入得到相同决策 digest；
- 日志不包含完整 Prompt、密钥或外部响应正文。

## 必须执行

```powershell
cd python-agent
pytest
```
