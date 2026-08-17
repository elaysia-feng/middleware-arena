"""Agent 服务统一配置。

1. 统一读取 LLM、Langfuse、RabbitMQ、Java 内部服务地址。
2. 默认值与当前 Java application.yml 保持一致，开发环境可直接启动。
3. 业务代码只依赖 Settings，不再散落 os.getenv。

TODO:
- [ ] 生产环境增加配置校验：禁止默认 MA_INTERNAL_TOKEN / guest RabbitMQ。
- [ ] 后续如注册 Nacos，在这里增加 NACOS_* 配置，不要散落到业务代码。
"""

from functools import lru_cache
from pathlib import Path

from pydantic_settings import BaseSettings, SettingsConfigDict

_ROOT_DIR = Path(__file__).resolve().parents[2]


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=(_ROOT_DIR / ".env", _ROOT_DIR / ".env.local"),
        env_file_encoding="utf-8",
        extra="ignore",
        case_sensitive=False,
    )

    # Agent HTTP
    agent_host: str = "0.0.0.0"
    agent_port: int = 9500
    agent_http_prefix: str = "/agent"
    agent_max_analysis_iterations: int = 3

    # MQ
    agent_mq_enabled: bool = False
    agent_mq_prefetch: int = 1
    agent_mq_max_attempts: int = 3
    agent_mq_retry_interval_seconds: float = 1.0
    rabbit_host: str = "192.168.0.192"
    rabbit_port: int = 5672
    rabbit_user: str = "guest"
    rabbit_password: str = "guest"
    rabbit_vhost: str = "/"

    # Java internal APIs
    experiment_service_url: str = "http://127.0.0.1:9003"
    ma_internal_token: str = "middleware-arena-internal-token"
    internal_token_header: str = "X-Internal-Token"
    internal_http_timeout_seconds: float = 10.0

    # LLM
    openai_api_base: str = "https://api.deepseek.com"
    openai_api_key: str = ""
    openai_model: str = "deepseek-v4-flash"

    # Langfuse
    langfuse_public_key: str = ""
    langfuse_secret_key: str = ""
    langfuse_base_url: str = "http://127.0.0.1:3000"
    langfuse_tracing_environment: str = "development"


@lru_cache(maxsize=1)
def get_settings() -> Settings:
    """进程级 Settings 单例。测试中可调用 get_settings.cache_clear() 后重新加载。"""
    return Settings()
