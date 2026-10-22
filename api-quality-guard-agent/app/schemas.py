"""与主平台约定的 HTTP契约"""
from typing import Any

from pydantic import BaseModel, Field


class DiagnosisRequest(BaseModel):
    """主平台传入的诊断上下文：Agent 无状态，所需证据全部随请求携带"""

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
