"""与主平台约定的 HTTP契约"""
from typing import Any, Literal

from pydantic import BaseModel, Field


class DiagnosisRequest(BaseModel):
    """主平台传入的诊断上下文：Agent 无状态，所需证据全部随请求携带"""

    userId: int | None = None
    caseId: int
    apiId: int
    caseName: str | None = None
    apiMethod: str | None = None
    apiPath: str | None = None
    apiBaseUrl: str | None = None
    apiDescription: str | None = None
    apiRequestSchema: Any | None = None
    apiResponseSchema: Any | None = None
    requestParams: Any | None = None
    requestSnapshot: Any | None = None
    responseSnapshot: Any | None = None
    assertionResults: list[dict[str, Any]] | None = None
    errorMessage: str | None = None
    extraContext: str | None = None


class DiagnosisCause(BaseModel):
    """单个候选原因；约束在字段上，反序列化即完成结构校验"""

    cause: str
    confidence: float = Field(ge=0, le=1)
    evidence: list[str] = Field(min_length=1)


class DiagnosisPayload(BaseModel):
    """结构化诊断输出"""

    conclusion: str = Field(min_length=1)
    causes: list[DiagnosisCause] = Field(min_length=1, max_length=5)
    suggestions: list[str] = Field(default_factory=list)


class DiagnosisResponse(BaseModel):
    """Agent 返回给主平台的诊断结果,可观测性信息"""

    diagnosis: DiagnosisPayload
    rounds: int
    model: str
    inputTokens: int | None = None
    outputTokens: int | None = None
    durationMs: int
    rawOutput: str


# ================= AI 用例增强 =================


class EnhanceRequest(BaseModel):
    """AI 用例增强请求"""

    apiId: int
    apiName: str | None = None
    apiMethod: str | None = None
    apiPath: str | None = None
    apiDescription: str | None = None
    apiRequestSchema: Any | None = None
    apiResponseSchema: Any | None = None
    extraContext: str | None = None
    count: int = Field(default=3, ge=1, le=10)


class EnhancedCase(BaseModel):
    """单条 AI 候选用例（落库前由主平台校验并转换为 test_case）"""

    name: str = Field(min_length=1)
    requestParams: dict[str, Any]
    assertionRules: list[dict[str, Any]] = Field(min_length=1)
    generationNote: str = Field(min_length=1)


class EnhanceResponse(BaseModel):
    """AI 用例增强响应"""

    cases: list[EnhancedCase]
    model: str
    inputTokens: int | None = None
    outputTokens: int | None = None
    durationMs: int
    rawOutput: str


# ================= AI 助手问答 =================


class ChatMessage(BaseModel):
    """历史消息（只含最终 user/assistant 内容，不含工具轨迹）"""

    role: Literal["user", "assistant"]
    content: str = Field(min_length=1, max_length=4000)


class ChatRequest(BaseModel):
    """自然语言问答请求"""

    userId: int | None = None
    question: str = Field(min_length=1, max_length=2000)
    history: list[ChatMessage] = Field(default_factory=list, max_length=12)


class ChatResponse(BaseModel):
    """问答响应，answer 为 Markdown 文本"""

    answer: str
    model: str
    inputTokens: int | None = None
    outputTokens: int | None = None
    durationMs: int
    rounds: int
