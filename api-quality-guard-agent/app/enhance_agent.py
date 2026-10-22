"""AI 用例增强执行器：单轮模型调用 + 结构化校验 + 一次纠正重试（无工具循环）"""
import json
import time

from langchain_core.messages import HumanMessage, SystemMessage

from app.config import settings
from app.errors import AgentError
from app.enhance_prompt import (
    build_enhance_context,
    build_enhance_correction,
    build_enhance_system_prompt,
)
from app.llm import build_llm
from app.schemas import EnhanceRequest, EnhanceResponse, EnhancedCase

MAX_ATTEMPTS = 2


async def enhance(request: EnhanceRequest) -> EnhanceResponse:
    started = time.monotonic()

    if settings.llm_mock:
        return _mock_response(started)

    llm = build_llm()
    messages = [
        SystemMessage(content=build_enhance_system_prompt()),
        HumanMessage(content=build_enhance_context(request)),
    ]

    input_tokens = 0
    output_tokens = 0
    raw_outputs: list[str] = []
    last_error = ""

    for attempt in range(1, MAX_ATTEMPTS + 1):
        try:
            response = await llm.ainvoke(messages)
        except Exception as exc:  # 模型调用失败（网络/限流/鉴权）
            raise AgentError(f"模型调用失败: {type(exc).__name__}: {exc}") from exc

        content = _content_to_text(response.content)
        raw_outputs.append(content)
        usage = getattr(response, "usage_metadata", None) or {}
        input_tokens += int(usage.get("input_tokens") or 0)
        output_tokens += int(usage.get("output_tokens") or 0)

        try:
            cases = _parse_cases(content)
            limited = cases[: request.count]
            if not limited:
                raise ValueError("cases 为空")
            return EnhanceResponse(
                cases=limited,
                model=settings.llm_model,
                inputTokens=input_tokens,
                outputTokens=output_tokens,
                durationMs=int((time.monotonic() - started) * 1000),
                rawOutput="\n".join(raw_outputs),
            )
        except Exception as exc:  # noqa: BLE001 —— 解析/校验失败进入纠正重试
            last_error = f"{type(exc).__name__}: {exc}"
            if attempt < MAX_ATTEMPTS:
                messages.append(response)
                messages.append(HumanMessage(content=build_enhance_correction(last_error)))

    raise AgentError(f"模型未返回合法的增强用例（最后一次错误: {last_error}）")


def _parse_cases(content: str) -> list[EnhancedCase]:
    payload = json.loads(_strip_code_fence(content))
    raw_cases = payload.get("cases") if isinstance(payload, dict) else payload
    if not isinstance(raw_cases, list):
        raise ValueError("输出缺少 cases 数组")
    return [EnhancedCase.model_validate(item) for item in raw_cases]


def _strip_code_fence(text: str) -> str:
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


def _mock_response(started: float) -> EnhanceResponse:
    return EnhanceResponse(
        cases=[
            EnhancedCase.model_validate(
                {
                    "name": "Mock 增强用例-重复提交",
                    "requestParams": {"query": {}, "body": {}},
                    "assertionRules": [{"type": "STATUS_CODE", "expected": 200}],
                    "generationNote": "Mock 模式生成：验证重复提交的幂等性（固定示例）",
                }
            )
        ],
        model=settings.llm_model,
        inputTokens=0,
        outputTokens=0,
        durationMs=int((time.monotonic() - started) * 1000),
        rawOutput="(mock)",
    )
