"""AI 助手问答循环（只读工具 + 有界轮数）"""
import json
import time

from langchain_core.messages import AIMessage, HumanMessage, SystemMessage, ToolMessage

from app.chat_prompt import build_chat_system_prompt
from app.chat_tools import build_chat_tools
from app.config import settings
from app.errors import AgentError
from app.llm import build_llm
from app.schemas import ChatRequest, ChatResponse

MAX_TOOL_CALLS = 8


async def chat(request: ChatRequest) -> ChatResponse:
    started = time.monotonic()

    if settings.llm_mock:
        return ChatResponse(
            answer=(
                "**Mock 模式**\n\n"
                "当前 Agent 运行在 `AGENT_LLM_MOCK=true`，未调用真实模型。"
                "关闭 Mock 并配置模型后，我可以查询平台质量、风险接口、执行结果和失败原因。"
            ),
            model=settings.llm_model,
            inputTokens=0,
            outputTokens=0,
            durationMs=int((time.monotonic() - started) * 1000),
            rounds=1,
        )
    #初始化工具和大模型
    tools = build_chat_tools(request.userId)
    tools_by_name = {tool.name: tool for tool in tools}
    llm = build_llm()
    #让模型具备工具调用的能力
    bound = llm.bind_tools(tools)
    #准备发送给大模型的上下文
    messages = [SystemMessage(content=build_chat_system_prompt())]
    for message in request.history[-6:]:#-6表示只读取历史消息的最后6条
        content = message.content[:1000]  #每条历史消息最多保留1000字符
        #根据角色构建不同类型的消息
        if message.role == "user":
            messages.append(HumanMessage(content=content))
        else:
            messages.append(AIMessage(content=content))
    messages.append(HumanMessage(content=request.question))#将本次用户的问题添加到消息列表末尾

    input_tokens = 0
    output_tokens = 0
    tool_calls_count = 0
    rounds = 0
    #执行循环，settings.llm_max_rounds为最大循环次数，+ 1 是因为 Python 的 range() 不包含结束值
    for round_no in range(1, settings.llm_max_rounds + 1):
        rounds = round_no
        try:
            response = await bound.ainvoke(messages) #调用大模型
        except Exception as exc:  # noqa: BLE001
            raise AgentError(f"模型调用失败: {type(exc).__name__}: {exc}") from exc

        input_tokens, output_tokens = _accumulate_usage(response, input_tokens, output_tokens)#统计token
        tool_calls = getattr(response, "tool_calls", None) or [] #获取工具调用列表
        #模型不需要工具时直接返回答案
        if not tool_calls:
            answer = _content_to_text(response.content).strip()
            if not answer:
                raise AgentError("模型返回空回答")
            return ChatResponse(
                answer=answer,
                model=settings.llm_model,
                inputTokens=input_tokens,
                outputTokens=output_tokens,
                durationMs=int((time.monotonic() - started) * 1000),
                rounds=rounds,
            )

        messages.append(response)#保存模型的原始响应
        for call in tool_calls:
            tool_call_id = call.get("id") or f"call_{tool_calls_count}" #获取工具调用id
            if tool_calls_count >= MAX_TOOL_CALLS:
                # 每个 tool_call 都必须有对应 ToolMessage，否则下一次模型调用会因协议不完整失败
                messages.append(ToolMessage(
                    content=json.dumps(
                        {"error": "达到单次请求工具调用上限，请基于已有信息直接回答"},
                        ensure_ascii=False,
                    ),
                    tool_call_id=tool_call_id,
                ))
                continue
            messages.append(ToolMessage(
                content=await _execute_tool(call, tools_by_name),
                tool_call_id=tool_call_id,
            ))
            tool_calls_count += 1
        if tool_calls_count >= MAX_TOOL_CALLS:
            break

    # 轮数/工具次数用尽：强制一次不带工具的回答，避免直接失败
    rounds += 1
    try:
        final = await llm.ainvoke(messages)
    except Exception as exc:  # noqa: BLE001
        raise AgentError(f"模型调用失败: {type(exc).__name__}: {exc}") from exc

    input_tokens, output_tokens = _accumulate_usage(final, input_tokens, output_tokens)
    answer = _content_to_text(final.content).strip()
    if not answer:
        raise AgentError("模型返回空回答")
    return ChatResponse(
        answer=answer,
        model=settings.llm_model,
        inputTokens=input_tokens,
        outputTokens=output_tokens,
        durationMs=int((time.monotonic() - started) * 1000),
        rounds=rounds,
    )


async def _execute_tool(call: dict, tools_by_name: dict) -> str:
    """执行单个工具调用；失败返回 error JSON（不中断循环）。"""
    #根据工具名称找到对应工具
    tool = tools_by_name.get(call.get("name"))
    if tool is None:
        #将字典转换为字符串并返回错误信息，ensure_ascii=False表示保留中文字符
        return json.dumps({"error": f"未知工具: {call.get('name')}"}, ensure_ascii=False)
    try:
        #异步执行工具
        result = await tool.ainvoke(call.get("args") or {})
        return str(result)
    except Exception as exc:  # noqa: BLE001
        return json.dumps(
            {"error": f"{type(exc).__name__}: {exc}"}, ensure_ascii=False
        )


def _content_to_text(content) -> str:
    if isinstance(content, str):
        return content
    try:
        return json.dumps(content, ensure_ascii=False, default=str)
    except Exception:  # noqa: BLE001
        return str(content)


def _accumulate_usage(response, input_tokens: int, output_tokens: int) -> tuple[int, int]:
    #读取token使用信息，如果不存在就返回none，如果获取到其他值就返回空字典
    usage = getattr(response, "usage_metadata", None) or {}

    input_tokens += int(usage.get("input_tokens") or 0)
    output_tokens += int(usage.get("output_tokens") or 0)
    return input_tokens, output_tokens
