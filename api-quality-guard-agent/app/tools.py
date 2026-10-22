"""Agent 工具：回调主平台只读接口获取更多诊断证据,工具调用失败时返回 {"error": "..."} 文本而不抛异常,
把失败信息作为工具结果喂回模型，由模型基于已有证据继续决策（循环不中断）
"""
import json

import httpx
from langchain_core.tools import StructuredTool

from app.config import settings
from app.schemas import DiagnosisRequest


def build_tools(request: DiagnosisRequest) -> list[StructuredTool]:
    """按当前诊断请求构造两个工具（闭包持有 caseId / apiId）"""

    def api_detail() -> str:
        """API_DETAIL：查询当前接口的定义摘要（方法、路径、参数结构、响应结构）"""
        return _get(f"/api/agent-tools/api-detail/{request.apiId}")

    def case_history(limit: int = 5) -> str:
        """CASE_HISTORY：查询该测试用例最近若干次执行结果（状态、耗时、失败断言摘要）limit 最大 10"""
        safe_limit = max(1, min(limit, 10))
        return _get(f"/api/agent-tools/case-history/{request.caseId}?limit={safe_limit}")

    return [
        StructuredTool.from_function(
            func=api_detail,
            name="API_DETAIL",
            description="查询当前接口的定义摘要（方法、路径、参数结构、响应结构）无需参数",
        ),
        StructuredTool.from_function(
            func=case_history,
            name="CASE_HISTORY",
            description="查询该测试用例最近若干次执行结果（状态、耗时、失败断言摘要）。"
            "参数 limit 为条数（1~10，默认 5）",
        ),
    ]


def _get(path: str) -> str:
    base_url = settings.platform_base_url.rstrip("/")
    try:
        with httpx.Client(timeout=settings.platform_timeout_seconds) as client:
            response = client.get(base_url + path)
        if response.status_code // 100 != 2:
            return _error_json(f"平台工具调用失败: HTTP {response.status_code}")
        return response.text
    except Exception as exc:  # noqa: BLE001 —— 工具失败要作为结果回填，不能中断循环
        return _error_json(f"平台工具调用异常: {exc}")


def _error_json(message: str) -> str:
    return json.dumps({"error": message}, ensure_ascii=False)
