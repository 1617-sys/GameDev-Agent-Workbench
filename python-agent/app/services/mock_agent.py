from app.clients.llm_client import LLMClient
from app.prompts.agent_prompts import (
    CORE_LOOP_DESIGN_SYSTEM_PROMPT,
    GAME_CONCEPT_SYSTEM_PROMPT,
    TASK_BREAKDOWN_SYSTEM_PROMPT,
    build_core_loop_design_user_prompt,
    build_game_concept_user_prompt,
    build_task_breakdown_user_prompt,
)
from app.schemas.agent import AgentMockRequest, AgentMockResult


llm_client = LLMClient()


def build_requirement_breakdown_result(payload: AgentMockRequest) -> AgentMockResult:
    return AgentMockResult(
        agent_type="REQUIREMENT_BREAKDOWN",
        title=payload.title,
        summary=f"已完成需求拆解：{payload.title}",
        key_points=[
            "识别核心业务目标",
            "拆分主流程和子任务",
            "标出可能的风险点",
        ],
        suggestions=[
            "优先完成主链路",
            "先定义清晰的输入输出协议",
            "后续可继续补充边界场景",
        ],
        raw_result={
            "mode": "mock",
            "source": "fastapi",
        },
        model="mock",
    )


def build_api_design_result(payload: AgentMockRequest) -> AgentMockResult:
    return AgentMockResult(
        agent_type="API_DESIGN",
        title=payload.title,
        summary=f"已输出接口设计建议：{payload.title}",
        key_points=[
            "明确资源路径",
            "统一请求模型",
            "统一响应模型",
        ],
        suggestions=[
            "接口命名保持语义一致",
            "成功失败都返回固定结构 JSON",
            "后续可直接由 Java 调用",
        ],
        raw_result={
            "mode": "mock",
            "source": "fastapi",
        },
        model="mock",
    )


def build_bug_analysis_result(payload: AgentMockRequest) -> AgentMockResult:
    return AgentMockResult(
        agent_type="BUG_ANALYSIS",
        title=payload.title,
        summary=f"已完成 Bug 分析：{payload.title}",
        key_points=[
            "先确认报错现象",
            "再缩小排查范围",
            "最后给出修复建议",
        ],
        suggestions=[
            "先检查输入参数",
            "再检查日志和请求链路",
            "必要时补充重试和兜底",
        ],
        raw_result={
            "mode": "mock",
            "source": "fastapi",
        },
        model="mock",
    )


def build_prompt_generate_result(payload: AgentMockRequest) -> AgentMockResult:
    generated_prompt = (
        f"请根据以下目标生成可执行方案：{payload.title}。"
        f"背景：{payload.content}"
    )
    if payload.context:
        generated_prompt += f"。补充上下文：{payload.context}"

    return AgentMockResult(
        agent_type="PROMPT_GENERATE",
        title=payload.title,
        summary=f"已生成 Prompt 草稿：{payload.title}",
        key_points=[
            "聚焦任务目标",
            "明确输出要求",
            "保留上下文信息",
        ],
        suggestions=[
            "后续可继续精炼成模板",
            "可按不同场景做版本管理",
            "Java 侧可直接保存和复用",
        ],
        prompt=generated_prompt,
        raw_result={
            "mode": "mock",
            "source": "fastapi",
        },
        model="mock",
    )


def build_game_concept_result(payload: AgentMockRequest) -> AgentMockResult:
    if payload.system_prompt and payload.user_prompt_template:
        # 如果请求里带了完整的 system_prompt 和 user_prompt_template，则直接使用它们进行生成
        user_prompt = payload.user_prompt_template.format(
        title=payload.title,
        content=payload.content,
        context=payload.context or "",
    )
        llm_result = llm_client.generate(payload.system_prompt, user_prompt)
    else:
      user_prompt = build_game_concept_user_prompt(payload)
      llm_result = llm_client.generate(GAME_CONCEPT_SYSTEM_PROMPT, user_prompt)

    return _build_llm_agent_result(
        agent_type="GAME_CONCEPT",
        title=payload.title,
        summary=f"已生成游戏概念方案：{payload.title}",
        content=llm_result.content,
        key_points=[
            "已基于用户输入生成游戏概念",
            "输出内容可继续传递给核心循环设计 Agent",
            "结果可保存为项目产物",
        ],
        suggestions=[
            "下一步可以运行 CORE_LOOP_DESIGN",
            "如果结果太发散，可以降低 temperature 或收紧 prompt",
            "后续可以把 prompt 模板迁移到数据库管理",
        ],
        payload=payload,
        llm_result=llm_result,
        system_prompt=payload.system_prompt,
        user_prompt=user_prompt,
    )


def build_core_loop_design_result(payload: AgentMockRequest) -> AgentMockResult:
    user_prompt = payload.user_prompt_template.format(
        title=payload.title,
        content=payload.content,
        context=payload.context or "",
    )
    llm_result = llm_client.generate(payload.system_prompt, user_prompt)

    return _build_llm_agent_result(
        agent_type="CORE_LOOP_DESIGN",
        title=payload.title,
        summary=f"已生成核心循环设计：{payload.title}",
        content=llm_result.content,
        key_points=[
            "已根据游戏概念生成核心玩法循环",
            "输出内容可继续传递给任务拆解 Agent",
            "结果可保存为核心循环设计产物",
        ],
        suggestions=[
            "下一步可以运行 TASK_BREAKDOWN",
            "如果循环过重，可以要求模型压缩到 MVP 范围",
            "后续可以继续细化数值、关卡和成长系统",
        ],
        payload=payload,
        llm_result=llm_result,
        system_prompt=payload.system_prompt,
        user_prompt=user_prompt,
    )


def build_task_breakdown_result(payload: AgentMockRequest) -> AgentMockResult:
    user_prompt = payload.user_prompt_template.format(
        title=payload.title,
        content=payload.content,
        context=payload.context or "",
    )
    llm_result = llm_client.generate(payload.system_prompt, user_prompt)

    return _build_llm_agent_result(
        agent_type="TASK_BREAKDOWN",
        title=payload.title,
        summary=f"已生成开发任务拆解：{payload.title}",
        content=llm_result.content,
        key_points=[
            "已将设计内容拆解为 MVP 开发任务",
            "任务结果可用于后续排期和开发管理",
            "结果可保存为任务拆解产物",
        ],
        suggestions=[
            "优先完成最小可演示版本",
            "把接口、数据表、联调、测试分开记录",
            "后续可以接入任务管理或甘特图视图",
        ],
        payload=payload,
        llm_result=llm_result,
        system_prompt=payload.system_prompt,
        user_prompt=user_prompt,
    )


def _build_llm_agent_result(
    agent_type: str,
    title: str,
    summary: str,
    content: str,
    key_points: list[str],
    suggestions: list[str],
    payload: AgentMockRequest,
    llm_result,
    system_prompt: str,
    user_prompt: str,
) -> AgentMockResult:
    return AgentMockResult(
        agent_type=agent_type,
        title=title,
        summary=summary,
        content=content,
        key_points=key_points,
        suggestions=suggestions,
        raw_result={
            "mode": "llm" if llm_result.model != "mock" else "mock_fallback",
            "source": "llm_client",
            "model": llm_result.model,
            "time_taken_ms": llm_result.time_taken_ms,
        },
        model=llm_result.model,
        time_taken_ms=llm_result.time_taken_ms,
    )
