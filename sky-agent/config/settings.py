from pathlib import Path

from pydantic import Field, field_validator, model_validator
from pydantic_settings import BaseSettings, SettingsConfigDict


BASE_DIR = Path(__file__).resolve().parents[1]


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=BASE_DIR / ".env",
        env_file_encoding="utf-8",
        extra="ignore",
        populate_by_name=True,
    )

    java_base_url: str = "http://127.0.0.1:8080"
    java_http_timeout: float = 10

    postgres_uri: str = (
        "postgresql://postgres:postgres@127.0.0.1:5433/sky_agent"
    )
    postgres_connect_timeout: int = 2
    postgres_pool_min_size: int = 1
    postgres_pool_max_size: int = 10
    postgres_pool_wait_timeout: int = 3
    postgres_pool_reconnect_timeout: int = 5

    deepseek_api_key: str = Field(
        validation_alias="DEEPSEEK_API_KEY",
    )
    deepseek_base_url: str = "https://api.deepseek.com"
    deepseek_model: str = "deepseek-v4-flash"
    deepseek_temperature: float = 0.2
    deepseek_timeout: float = 60
    deepseek_max_retries: int = 2

    @field_validator("java_base_url", "deepseek_base_url")
    @classmethod
    def strip_trailing_slash(cls, value: str) -> str:
        return value.rstrip("/")

    @field_validator("deepseek_api_key")
    @classmethod
    def require_deepseek_api_key(cls, value: str) -> str:
        if not value or not value.strip():
            raise ValueError("未配置 DEEPSEEK_API_KEY")
        return value.strip()

    @model_validator(mode="after")
    def validate_postgres_pool_size(self) -> "Settings":
        if self.postgres_pool_min_size > self.postgres_pool_max_size:
            raise ValueError(
                "POSTGRES_POOL_MIN_SIZE 不能大于 POSTGRES_POOL_MAX_SIZE"
            )
        return self

    def java_url(self, path: str) -> str:
        return f"{self.java_base_url}/{path.lstrip('/')}"


settings = Settings()

DEEPSEEK_API_KEY = settings.deepseek_api_key
