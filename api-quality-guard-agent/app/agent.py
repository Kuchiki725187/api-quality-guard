"""诊断 Agent 核心循环

数据流：
    DiagnosisRequest
        → PromptBuilder（prompt.py）
        → ChatOpenAI.bind_tools(tools)
        → 循环：模型请求工具 → 执行工具 → 回填 ToolMessage → 再调用
        → 最终 JSON 输出 → 解析 + Pydantic 校验 DiagnosisPayload（失败则追加纠正消息重试）
        → DiagnosisResponse（含 rounds / model / token / 耗时 / rawOutput）

实现约定：
- 使用 LangChain 消息体系：SystemMessage / HumanMessage / AIMessage / ToolMessage
- 模型调用：llm.ainvoke(messages)；工具调用意图来自 AIMessage.tool_calls
- 单轮工具执行失败不中断循环，把错误文本写回工具结果
- 轮数上限取配置 settings.llm_max_rounds（默认 3）
- 任意可预期失败统一抛 AgentError（Controller 转 502）
"""
import json
import time

from langchain_core.messages import HumanMessage, SystemMessage, ToolMessage

from app.config import settings
from app.errors import AgentError
from app.llm import build_llm
from app.prompt import build_context_message, build_correction_message, build_system_prompt
from app.schemas import DiagnosisPayload, DiagnosisRequest, DiagnosisResponse
from app.tools import build_tools


async def diagnose(request: DiagnosisRequest) -> DiagnosisResponse:
    started = time.monotonic()

    if settings.llm_mock:
        return _mock_response(started)

    tools = build_tools(request)
    tools_by_name = {tool.name: tool for tool in tools}
    llm = build_llm().bind_tools(tools)

    messages = [
        SystemMessage(content=build_system_prompt()),
        HumanMessage(content=build_context_message(request)),
    ]

    input_tokens = 0
    output_tokens = 0
    raw_outputs: list[str] = []

    for round_no in range(1, settings.llm_max_rounds + 1):
        try:
            response = await llm.ainvoke(messages)
        except Exception as exc:  # 模型调用失败（网络/限流/鉴权）
            raise AgentError(f"模型调用失败: {type(exc).__name__}: {exc}") from exc

        content = _content_to_text(response.content)
        raw_outputs.append(content)
        input_tokens, output_tokens = _accumulate_usage(response, input_tokens, output_tokens)

        # 模型请求工具：执行并回填 ToolMessage，继续下一轮
        tool_calls = getattr(response, "tool_calls", None) or []
        if tool_calls:
            messages.append(response)
            for call in tool_calls:
                messages.append(ToolMessage(
                    content=_execute_tool(call, tools_by_name),
                    tool_call_id=call.get("id"),
                ))
            continue

        # 无工具调用：这是最终输出，解析并校验
        error = _try_parse(content)
        if error is None:
            payload = DiagnosisPayload.model_validate_json(_strip_code_fence(content))
            return DiagnosisResponse(
                diagnosis=payload,
                rounds=round_no,
                model=settings.llm_model,
                inputTokens=input_tokens,
                outputTokens=output_tokens,
                durationMs=int((time.monotonic() - started) * 1000),
                rawOutput="\n".join(raw_outputs),
            )

        # 输出不合法：追加纠正消息重试（计入轮数）
        messages.append(response)
        messages.append(HumanMessage(content=build_correction_message(error)))

    raise AgentError(f"模型在 {settings.llm_max_rounds} 轮内未给出合法诊断")


def _execute_tool(call: dict, tools_by_name: dict) -> str:
    """执行单个工具调用；失败返回 error JSON（不中断循环）。"""
    tool = tools_by_name.get(call.get("name"))
    if tool is None:
        return json.dumps({"error": f"未知工具: {call.get('name')}"}, ensure_ascii=False)
    try:
        result = tool.invoke(call.get("args") or {})
        return str(result)
    except Exception as exc:  # noqa: BLE001
        return json.dumps(
            {"error": f"{type(exc).__name__}: {exc}"}, ensure_ascii=False
        )


def _try_parse(content: str) -> str | None:
    """校验最终输出：可解析 JSON 且通过 DiagnosisPayload 约束时返回 None，否则返回错误说明。"""
    try:
        DiagnosisPayload.model_validate_json(_strip_code_fence(content))
        return None
    except Exception as exc:  # noqa: BLE001
        return f"{type(exc).__name__}: {exc}"


def _strip_code_fence(text: str) -> str:
    """模型偶尔会用 Markdown 代码块包裹 JSON，剥离围栏后再解析。"""
    stripped = text.strip()
    if stripped.startswith("```"):
        lines = stripped.splitlines()
        if len(lines) >= 2:
            body = lines[1:]
            if body and body[-1].strip().startswith("```"):
                body = body[:-1]
            return "\n".join(body).strip()
    return stripped


def _content_to_text(content) -> str:
    if isinstance(content, str):
        return content
    try:
        return json.dumps(content, ensure_ascii=False, default=str)
    except Exception:  # noqa: BLE001
        return str(content)


def _accumulate_usage(response, input_tokens: int, output_tokens: int) -> tuple[int, int]:
    usage = getattr(response, "usage_metadata", None) or {}
    input_tokens += int(usage.get("input_tokens") or 0)
    output_tokens += int(usage.get("output_tokens") or 0)
    return input_tokens, output_tokens


def _mock_response(started: float) -> DiagnosisResponse:
    return DiagnosisResponse(
        diagnosis=DiagnosisPayload.model_validate(
            {
                "conclusion": "Mock 模式：这是固定诊断结果，仅用于链路联调",
                "causes": [
                    {
                        "cause": "示例原因：响应内容与断言期望不一致",
                        "confidence": 0.6,
                        "evidence": ["由 Mock 模式生成，未进行真实推理"],
                    }
                ],
                "suggestions": ["配置 AGENT_LLM_* 环境变量并关闭 AGENT_LLM_MOCK 后重新诊断"],
            }
        ),
        rounds=1,
        model=settings.llm_model,
        inputTokens=0,
        outputTokens=0,
        durationMs=int((time.monotonic() - started) * 1000),
        rawOutput="(mock)",
    )
