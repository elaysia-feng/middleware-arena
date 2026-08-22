"""Agent 服务统一配置入口。

为什么集中到 ``Settings``：
1. 业务代码不再到处 ``os.getenv``，所有环境变量只有一个读取入口。
2. ``pydantic-settings`` 会自动把环境变量转换成 Python 类型，例如 ``AGENT_PORT=9500`` -> int。
3. 本地开发使用 ``.env``；个人密钥可以放 ``.env.local``，后者在加载顺序上覆盖前者。
4. 测试时可以构造 ``Settings(...)`` 注入，不需要真的修改系统环境变量。

TODO:
- [ ] 生产环境增加强校验：禁止默认 MA_INTERNAL_TOKEN / guest RabbitMQ。
- [ ] 后续如注册 Nacos，在这里增加 NACOS_* 配置，不要散落到业务代码。
"""

from functools import lru_cache
from pathlib import Path

from pydantic_settings import BaseSettings, SettingsConfigDict

# config.py 位于 app/core/config.py，parents[2] 正好回到 middleware-arena-agent 根目录。
# 这样无论从哪个工作目录启动 uvicorn，都能找到项目根目录下的 .env。
_ROOT_DIR = Path(__file__).resolve().parents[2]


class Settings(BaseSettings):
    """Agent 进程级配置。

    ``BaseSettings`` 和普通 ``BaseModel`` 的区别是：它会主动从环境变量/.env 中取值。
    字段名 ``rabbit_host`` 默认对应环境变量 ``RABBIT_HOST``，大小写由下面配置控制。
    """

    model_config = SettingsConfigDict(
        # 先读公共开发配置 .env，再读个人覆盖配置 .env.local。
        # 同名变量后者优先，适合把真实 API Key 放在不提交 Git 的 .env.local。
        env_file=(_ROOT_DIR / ".env", _ROOT_DIR / ".env.local"),
        env_file_encoding="utf-8",
        # .env 里出现当前代码暂时没声明的字段时不报错，便于渐进增加配置。
        extra="ignore",
        # RABBIT_HOST / rabbit_host 都可以被识别。
        case_sensitive=False,
    )

    # ------------------------------------------------------------------
    # Agent HTTP / FastAPI
    # ------------------------------------------------------------------
    # FastAPI 监听地址；0.0.0.0 表示允许容器/局域网从外部访问。
    agent_host: str = "0.0.0.0"
    # FastAPI 服务端口。
    agent_port: int = 9500
    # Gateway 转发到 Agent 时统一使用的 URL 前缀，例如 /agent/analyze。
    agent_http_prefix: str = "/agent"
    # 自动优化最多循环次数，防止 Agent 一直“修改 -> 重跑 -> 再修改”。
    agent_max_analysis_iterations: int = 3
    # 只有 Judge 达到该置信度且状态为 CONFIRMED，才允许生成候选 Patch。
    agent_patch_confidence_threshold: float = 0.75

    # ------------------------------------------------------------------
    # RabbitMQ
    # ------------------------------------------------------------------
    # 当前默认关闭自动消费；核心 LangGraph 写完后本地改成 true 即可。
    agent_mq_enabled: bool = False
    # 每个 Consumer 同时最多预取多少条未 ACK 消息；1 表示处理完一条再拿下一条。
    agent_mq_prefetch: int = 1
    # 单条分析任务在 Python Consumer 内部最多尝试次数。
    agent_mq_max_attempts: int = 3
    # 两次本地重试之间等待秒数，避免失败后立即高速重试。
    agent_mq_retry_interval_seconds: float = 1.0

    # 下面 5 个字段必须与 Java 服务连接的是同一个 RabbitMQ 实例。
    rabbit_host: str = "192.168.0.192"
    rabbit_port: int = 5672
    rabbit_user: str = "guest"
    rabbit_password: str = "guest"
    # RabbitMQ virtual host；默认 /。
    rabbit_vhost: str = "/"

    # ------------------------------------------------------------------
    # Java experiment-service 内部 HTTP API
    # ------------------------------------------------------------------
    # Agent 不直接读 Experiment MySQL，而是通过这个地址查询 task/version/result/diff/logs。
    experiment_service_url: str = "http://127.0.0.1:9003"
    # 服务间内部鉴权 token。生产环境必须改为真正安全的值。
    ma_internal_token: str = "middleware-arena-internal-token"
    # Java/Python 约定的内部鉴权 Header 名称。
    internal_token_header: str = "X-Internal-Token"
    # Agent 调 Java 内部接口的 HTTP 超时时间，单位秒。
    internal_http_timeout_seconds: float = 10.0

    # ------------------------------------------------------------------
    # LLM（MiniMax Anthropic-compatible API）
    # ------------------------------------------------------------------
    # MiniMax 官方推荐的 Anthropic 兼容接口，支持思考块和 Prompt Cache。
    anthropic_base_url: str = "https://api.minimaxi.com/anthropic"
    # 不在仓库提交真实 Key；本地放 .env.local。
    anthropic_api_key: str = ""
    # 默认模型名；后续也可以按节点/Prompt 选择不同模型。
    anthropic_model: str = "MiniMax-M3[1m]"

    # ------------------------------------------------------------------
    # Langfuse
    # ------------------------------------------------------------------
    # public/secret 任意一个为空时，clients/langfuse.py 会直接禁用 tracing，不影响主业务。
    langfuse_public_key: str = ""
    langfuse_secret_key: str = ""
    # 自托管 Langfuse 地址；如果改用 Cloud，再通过环境变量覆盖。
    langfuse_base_url: str = "http://127.0.0.1:3000"
    # Trace 环境标签，方便在 Langfuse 中区分 development / production。
    langfuse_tracing_environment: str = "development"


@lru_cache(maxsize=1)
def get_settings() -> Settings:
    """返回进程级 Settings 单例。

    为什么加 ``lru_cache(maxsize=1)``：
    ``Settings()`` 会读取并解析环境变量，没必要每个请求都重新创建。
    缓存后整个进程复用同一个对象。

    单元测试如果修改了环境变量，可调用 ``get_settings.cache_clear()`` 强制重新加载。
    """
    return Settings()
