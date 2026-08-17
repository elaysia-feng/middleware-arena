"""Agent 服务配置占位。

1. 统一读取 LLM、Langfuse、RabbitMQ、experiment-service 内部地址等环境变量。
2. RabbitMQ 默认值与 Java 端 application.yml 保持一致，避免开发环境出现两套地址。
3. 业务代码禁止直接 os.getenv，后续全部从 Settings 注入，便于测试和 Docker 覆盖。

TODO:
- [ ] 引入 pydantic-settings，定义 Settings 单例。
- [ ] 增加启动时配置校验：生产环境禁止默认 internal token / guest RabbitMQ。
- [ ] 增加 AGENT_HTTP_PREFIX=/agent，用于 Gateway 对外路由。
"""

# RabbitMQ defaults aligned with experiment-service / runner-service.
RABBIT_HOST_DEFAULT = "192.168.0.192"
RABBIT_PORT_DEFAULT = 5672
RABBIT_USER_DEFAULT = "guest"
RABBIT_PASSWORD_DEFAULT = "guest"

# Internal Java service call defaults.
EXPERIMENT_SERVICE_URL_DEFAULT = "http://127.0.0.1:9003"
INTERNAL_TOKEN_DEFAULT = "middleware-arena-internal-token"
