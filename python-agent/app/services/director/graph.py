from __future__ import annotations

import hashlib
import json
from typing import Any, TypedDict

from langgraph.graph import END, StateGraph

from app.schemas.director import DirectorDecision, DirectorSnapshot


class GraphState(TypedDict, total=False):
    snapshot: DirectorSnapshot
    input_digest: str
    plan: str
    decision: dict[str, Any]


def canonical_digest(value: Any) -> str:
    if hasattr(value, "model_dump"):
        value = value.model_dump(by_alias=True, mode="json")
    encoded = json.dumps(value, sort_keys=True, separators=(",", ":"), ensure_ascii=False).encode()
    return hashlib.sha256(encoded).hexdigest()


def _normalize(state: GraphState) -> GraphState:
    snapshot = state["snapshot"]
    # Pydantic has already converted natural-language input into allowlisted metrics.
    return {**state, "input_digest": canonical_digest(snapshot)}


def _exhausted(snapshot: DirectorSnapshot) -> str | None:
    pairs = (
        (snapshot.usage.rounds, snapshot.goal.budget.max_rounds, "ROUNDS"),
        (snapshot.usage.tool_calls, snapshot.goal.budget.max_tool_calls, "TOOL_CALLS"),
        (snapshot.usage.candidates, snapshot.goal.budget.max_candidates, "CANDIDATES"),
        (snapshot.usage.episodes, snapshot.goal.budget.max_episodes, "EPISODES"),
        (snapshot.usage.tokens, snapshot.goal.budget.max_tokens, "TOKENS"),
        (snapshot.usage.cost_micros, snapshot.goal.budget.max_cost_micros, "COST"),
        (snapshot.usage.wall_clock_ms, snapshot.goal.budget.max_wall_clock_ms, "WALL_CLOCK"),
        (snapshot.usage.failures, snapshot.goal.budget.max_failures, "FAILURES"),
    )
    return next((name for used, maximum, name in pairs if used >= maximum), None)


def _plan(state: GraphState) -> GraphState:
    snapshot = state["snapshot"]
    exhausted = _exhausted(snapshot)
    if exhausted:
        plan = "FAIL"
    elif snapshot.model_mode != "AUTO":
        plan = snapshot.model_mode
    elif snapshot.target_met and snapshot.approval_required:
        plan = "APPROVAL"
    elif snapshot.target_met:
        plan = "FINISH"
    elif snapshot.allowed_tools:
        plan = "TOOL"
    else:
        plan = "FAIL"
    return {**state, "plan": plan}


def _validate_arguments(arguments: dict[str, Any], schema: dict[str, Any]) -> None:
    if schema.get("type") != "object" or schema.get("additionalProperties") is not False:
        raise ValueError("tool schema must be a closed object")
    properties = schema.get("properties", {})
    required = set(schema.get("required", []))
    if set(arguments) - set(properties):
        raise ValueError("tool arguments contain extra fields")
    if required - set(arguments):
        raise ValueError("tool arguments omit required fields")
    for name, value in arguments.items():
        expected = properties[name].get("type")
        valid = {"string": isinstance(value, str), "integer": isinstance(value, int) and not isinstance(value, bool),
                 "number": isinstance(value, (int, float)) and not isinstance(value, bool), "boolean": isinstance(value, bool)}
        if expected in valid and not valid[expected]:
            raise ValueError(f"tool argument {name} has wrong type")


def _select(state: GraphState) -> GraphState:
    snapshot, plan = state["snapshot"], state["plan"]
    base: dict[str, Any] = {
        "protocolVersion": "director/1.0", "runId": snapshot.run_id,
        "stateVersion": snapshot.state_version, "round": snapshot.usage.rounds + 1,
    }
    if plan == "TOOL":
        tools = {tool.name: tool for tool in snapshot.allowed_tools}
        name = snapshot.requested_tool or sorted(tools)[0]
        if name not in tools:
            raise ValueError("requested tool is not registered")
        tool = tools[name]
        _validate_arguments(snapshot.next_tool_arguments, tool.argument_schema)
        base.update(kind="CALL_TOOL", reasonSummary="Execute the next allowlisted read operation.", toolCall={
            "callId": f"{snapshot.run_id}:{snapshot.usage.rounds + 1}", "toolName": name,
            "toolVersion": tool.version, "idempotencyKey": f"{snapshot.run_id}:{snapshot.usage.rounds + 1}",
            "arguments": snapshot.next_tool_arguments, "dryRun": False,
        })
    elif plan == "APPROVAL":
        base.update(kind="REQUEST_APPROVAL", reasonSummary="The target has evidence and requires a human decision.",
                    approval={"type": "DRAFT_CANDIDATE", "evidenceResultRefs": [r.result_ref for r in snapshot.recent_tool_results if r.result_ref]})
    elif plan == "FINISH":
        base.update(kind="FINISH", reasonSummary="The normalized target and guardrails are satisfied.",
                    outcome={"summary": snapshot.terminal_summary or "Target met", "consumedToolResultDigests": [r.output_digest for r in snapshot.recent_tool_results]})
    return {**state, "decision": base}


def _fail(state: GraphState) -> GraphState:
    snapshot = state["snapshot"]
    exhausted = _exhausted(snapshot)
    decision = {"protocolVersion": "director/1.0", "runId": snapshot.run_id,
        "stateVersion": snapshot.state_version, "round": snapshot.usage.rounds + 1, "kind": "FAIL",
        "reasonSummary": "The run cannot safely continue.",
        "error": {"code": f"BUDGET_{exhausted}_EXHAUSTED" if exhausted else "NO_SAFE_ACTION", "retryable": False}}
    return {**state, "decision": decision}


def _finalize(state: GraphState) -> GraphState:
    decision = state["decision"]
    evidence_output = canonical_digest(decision)
    decision["modelEvidence"] = {"provider": "mock", "model": "deterministic-director",
        "promptVersion": "director/1.0", "inputDigest": state["input_digest"],
        "outputDigest": evidence_output, "tokenUsage": 0}
    decision["decisionDigest"] = canonical_digest(decision)
    return {**state, "decision": decision}


def _route(_: GraphState) -> str:
    return "finalize"


def _route_plan(state: GraphState) -> str:
    return "fail" if state["plan"] == "FAIL" else "select"


builder = StateGraph(GraphState)
builder.add_node("normalize_goal", _normalize)
builder.add_node("plan", _plan)
builder.add_node("select_next_action", _select)
builder.add_node("fail", _fail)
builder.add_node("finalize", _finalize)
builder.set_entry_point("normalize_goal")
builder.add_edge("normalize_goal", "plan")
builder.add_conditional_edges("plan", _route_plan, {"select": "select_next_action", "fail": "fail"})
builder.add_conditional_edges("select_next_action", _route, {"finalize": "finalize", "fail": "finalize"})
builder.add_edge("fail", "finalize")
builder.add_edge("finalize", END)
director_graph = builder.compile()


def decide(snapshot: DirectorSnapshot) -> DirectorDecision:
    result = director_graph.invoke({"snapshot": snapshot})
    return DirectorDecision.model_validate(result["decision"])
