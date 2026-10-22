"""Agent 配置"""
from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=".env", env_file_encoding="utf-8", extra="ignore"
    )

    # 大模型（OpenAI 兼容接口）
    llm_base_url: str = Field(
        default="https://open.bigmodel.cn", validation_alias="AGENT_LLM_BASE_URL"
    )
    llm_model: str = Field(default="glm-5.3-flash", validation_alias="AGENT_LLM_MODEL")
    llm_api_key: str = Field(default="", validation_alias="AGENT_LLM_API_KEY")
    llm_mock: bool = Field(default=False, validation_alias="AGENT_LLM_MOCK")
    llm_max_rounds: int = Field(default=3, validation_alias="AGENT_LLM_MAX_ROUNDS")
    llm_temperature: float = Field(default=0.2, validation_alias="AGENT_LLM_TEMPERATURE")
    llm_timeout_seconds: float = Field(default=60.0, validation_alias="AGENT_LLM_TIMEOUT_SECONDS")

    # 主平台（工具回调）
    platform_base_url: str = Field(
        default="http://localhost:8080", validation_alias="AQG_PLATFORM_BASE_URL"
    )
    platform_timeout_seconds: float = Field(
        default=10.0, validation_alias="AQG_PLATFORM_TIMEOUT_SECONDS"
    )


settings = Settings()
