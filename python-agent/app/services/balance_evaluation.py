import json
import os
import time
from langchain_core.output_parsers import StrOutputParser
from langchain_core.prompts import ChatPromptTemplate
from app.schemas.agent import AgentMockRequest, AgentMockResult
from app.schemas.game_config import validate_game_config_v2
from app.services.langchain_agent import build_chat_model, explicit_mock_mode

METRIC_KEYS = {"prototypeVersionUuid", "sampleSize", "sufficientForAi", "winRate", "averageDurationMs", "averageScore", "averageHitCount", "averageCollectedCount", "averageRestartCount", "failures", "snapshotAt"}

def validated_inputs(payload: AgentMockRequest):
    config = validate_game_config_v2(json.loads(payload.content))
    metrics = json.loads(payload.context or "{}")
    if not isinstance(metrics, dict) or set(metrics) - METRIC_KEYS:
        raise ValueError("Balance metrics contain unknown fields")
    if not isinstance(metrics.get("sampleSize"), int) or metrics["sampleSize"] < 5:
        raise ValueError("At least five ended sessions are required")
    if metrics.get("prototypeVersionUuid") is None or metrics.get("snapshotAt") is None:
        raise ValueError("Traceable version and snapshot are required")
    return config, metrics

async def run_balance_evaluation(payload: AgentMockRequest) -> AgentMockResult:
    config, metrics = validated_inputs(payload)
    if explicit_mock_mode():
        recommendation = (f"基于 {metrics['sampleSize']} 个已结束会话的暂定建议："
                          f"当前通关率为 {metrics.get('winRate', 0):.0%}。"
                          "仅调整白名单参数并创建新版本验证，不覆盖当前版本；样本仍有限，不能视为确定结论。")
        return AgentMockResult(agent_type="BALANCE_EVALUATION", title=payload.title,
            summary="Traceable balance evaluation generated", content=recommendation,
            key_points=["aggregate-only input", "immutable follow-up version"], suggestions=[], model="mock",
            time_taken_ms=0, raw_result={"mode":"mock", "source":"aggregate-snapshot"})
    prompt = ChatPromptTemplate.from_messages([
        ("system", "You are a cautious game balance analyst. Use only the supplied immutable GameConfig and aggregate metrics. Never claim certainty from a small sample. Recommend only the tuning whitelist; never auto-modify or publish a version. Output concise Chinese plain text without HTML, scripts, URLs, paths, prompts, tokens, or user data."),
        ("human", "GameConfig:\n{config}\n\nAggregate snapshot:\n{metrics}\n\nState sample size, version UUID, evidence, and tentative recommendations.")])
    start=time.perf_counter(); result=await (prompt|build_chat_model()|StrOutputParser()).ainvoke({
        "config":json.dumps(config,ensure_ascii=False,separators=(",",":")),
        "metrics":json.dumps(metrics,ensure_ascii=False,separators=(",",":"))})
    if not result.strip() or len(result)>5000 or any(marker in result.lower() for marker in ("<script", "http://", "https://")):
        raise ValueError("Unsafe balance evaluation output")
    return AgentMockResult(agent_type="BALANCE_EVALUATION",title=payload.title,summary="Traceable balance evaluation generated",
        content=result.strip(),key_points=["aggregate-only input","immutable follow-up version"],suggestions=[],
        model=os.getenv("LLM_MODEL","deepseek-chat"),time_taken_ms=int((time.perf_counter()-start)*1000),raw_result={"source":"aggregate-snapshot"})
