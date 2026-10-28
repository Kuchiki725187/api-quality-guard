"""主平台只读接口客户端

工具调用失败时返回 {"error": "..."} 文本，不抛异常，让模型基于已有证据继续决策
"""
import json

import httpx

from app.config import settings

MAX_TOOL_CHARS = 8000


def get_text(path: str, user_id: int | None = None) -> str:
    base_url = settings.platform_base_url.rstrip("/")
    headers = {}
    if settings.internal_token:
        headers["X-Internal-Token"] = settings.internal_token
    if user_id is not None:
        headers["X-AQG-UserId"] = str(user_id)
    try:
        with httpx.Client(timeout=settings.platform_timeout_seconds) as client:
            response = client.get(base_url + path, headers=headers)
        if response.status_code // 100 != 2:
            return error_json(
                f"平台工具调用失败: HTTP {response.status_code} {_error_message(response)}"
            )
        return response.text
    except Exception as exc:  # noqa: BLE001 —— 工具失败要作为结果回填，不能中断循环
        return error_json(f"平台工具调用异常: {exc}")


def get_limited(path: str, user_id: int | None = None,
                max_chars: int = MAX_TOOL_CHARS) -> str:
    """限制进入模型上下文的工具结果长度。"""
    text = get_text(path, user_id)
    if len(text) <= max_chars:
        return text
    return text[:max_chars] + "…(truncated)"


def error_json(message: str) -> str:
    return json.dumps({"error": message}, ensure_ascii=False)


def _error_message(response: httpx.Response) -> str:
    try:
        data = response.json()
        return str(data.get("message") or "")[:200]
    except Exception:  # noqa: BLE001
        return response.text[:200]
