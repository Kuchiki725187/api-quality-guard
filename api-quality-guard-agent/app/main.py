"""API Quality Guard - 轻量级诊断 Agent 服务入口

接口：
- POST /agent/diagnose   诊断（失败返回 502 + {"message": "..."}）
- GET  /agent/health     健康检查
"""
import logging

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse

from app.agent import diagnose
from app.config import settings
from app.errors import AgentError
from app.schemas import DiagnosisRequest

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s - %(message)s")
logger = logging.getLogger("agent")

app = FastAPI(title="API Quality Guard - Diagnosis Agent", version="0.1.0")


@app.get("/agent/health")
def health() -> dict:
    return {"status": "UP", "mock": settings.llm_mock, "model": settings.llm_model}


@app.post("/agent/diagnose")
async def diagnose_endpoint(request: DiagnosisRequest):
    return await diagnose(request)


@app.exception_handler(RequestValidationError)
async def handle_validation_error(request: Request, exc: RequestValidationError) -> JSONResponse:
    # 把校验明细与请求体长度打到日志并返回给调用方，便于定位 422 的具体原因
    errors = exc.errors()
    raw = await request.body()
    logger.warning("请求参数校验失败: errors=%s body_length=%d", errors, len(raw))
    return JSONResponse(
        status_code=422,
        content={"message": "请求参数校验失败", "errors": errors, "bodyLength": len(raw)},
    )


@app.exception_handler(AgentError)
async def handle_agent_error(_: Request, exc: AgentError) -> JSONResponse:
    logger.warning("诊断失败: %s", exc)
    return JSONResponse(status_code=502, content={"message": str(exc)})


@app.exception_handler(Exception)
async def handle_unexpected(_: Request, exc: Exception) -> JSONResponse:
    logger.exception("Agent 内部错误")
    return JSONResponse(status_code=500, content={"message": f"Agent 内部错误: {exc}"})
