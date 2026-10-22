"""LangChain 模型封装（OpenAI 兼容协议）"""
from langchain_openai import ChatOpenAI

from app.config import settings
from app.errors import AgentError


def build_llm() -> ChatOpenAI:
    if not settings.llm_api_key:
        raise AgentError("未配置模型 API Key（环境变量 AGENT_LLM_API_KEY）")
    return ChatOpenAI(
        base_url=settings.llm_base_url,
        api_key=settings.llm_api_key,
        model=settings.llm_model,
        temperature=settings.llm_temperature,
        timeout=settings.llm_timeout_seconds,
    )
