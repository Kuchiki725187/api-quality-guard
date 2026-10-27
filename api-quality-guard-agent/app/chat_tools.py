"""AI 助手只读工具"""
from urllib.parse import urlencode

from langchain_core.tools import StructuredTool

from app.platform_client import get_limited


def build_chat_tools() -> list[StructuredTool]:
    def platform_report() -> str:
        """获取平台总览与四维质量评分"""
        return get_limited("/api/agent-tools/platform-report")

    def search_apis(keyword: str = "", limit: int = 10) -> str:
        """按名称或路径搜索接口"""
        safe_limit = max(1, min(limit, 10))
        params = {"limit": safe_limit}
        if keyword:
            params["keyword"] = keyword[:100]
        return get_limited(f"/api/agent-tools/apis?{urlencode(params)}")

    def api_detail(apiId: int) -> str:
        """按 apiId 查询接口定义摘要"""
        return get_limited(f"/api/agent-tools/api-detail/{apiId}")

    def run_report(runId: int) -> str:
        """按 runId 查询执行报告"""
        return get_limited(f"/api/agent-tools/runs/{runId}")

    def recent_failures(limit: int = 10) -> str:
        """查询最近失败/异常的测试结果"""
        safe_limit = max(1, min(limit, 20))
        return get_limited(f"/api/agent-tools/failures?limit={safe_limit}")

    def case_history(caseId: int, limit: int = 5) -> str:
        """按 caseId 查询最近执行历史"""
        safe_limit = max(1, min(limit, 10))
        return get_limited(f"/api/agent-tools/case-history/{caseId}?limit={safe_limit}")

    return [
        StructuredTool.from_function(
            func=platform_report,
            name="PLATFORM_REPORT",
            description="获取平台总览与四维质量评分：接口/用例/执行数量、覆盖率、平均分、近14天趋势、"
            "最近5次执行、风险接口，以及规范性/覆盖率/性能/稳定性得分、总分、等级。无参数。",
        ),
        StructuredTool.from_function(
            func=search_apis,
            name="SEARCH_APIS",
            description="按名称或路径搜索接口，返回 apiId、名称、方法、路径、描述。"
            "keyword 可选（为空返回最近接口，最长100字符），limit 1~10，默认10。",
        ),
        StructuredTool.from_function(
            func=api_detail,
            name="API_DETAIL",
            description="按 apiId 查询接口摘要（方法、路径、描述、请求/响应结构）。"
            "apiId 必须来自工具结果；未知 id 先调 SEARCH_APIS。",
        ),
        StructuredTool.from_function(
            func=run_report,
            name="RUN_REPORT",
            description="按 runId 查询执行报告：状态、通过率、评分明细、失败/异常数、响应时间与来源分布。"
            "runId 必须来自工具结果。",
        ),
        StructuredTool.from_function(
            func=recent_failures,
            name="RECENT_FAILURES",
            description="查询最近 FAILED/ERROR 结果：resultId、runId、caseId、apiId、用例名、方法/路径、"
            "状态、错误信息、首个失败断言、时间。limit 1~20，默认10。",
        ),
        StructuredTool.from_function(
            func=case_history,
            name="CASE_HISTORY",
            description="按 caseId 查询最近执行记录（状态、耗时、失败断言）。"
            "caseId 必须来自工具结果；limit 1~10，默认5。",
        ),
    ]
