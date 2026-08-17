"""RabbitMQ 连接与拓扑声明占位。

1. 从环境变量读取 RABBIT_HOST / RABBIT_PORT / RABBIT_USER / RABBIT_PASSWORD，与 Java application.yml 同源。
2. 声明 durable direct exchange、durable queue、DLX，并确保参数与 Java AgentRabbitConfig 完全一致。
3. 提供 FastAPI lifespan 可复用的 robust connection/channel，避免每条消息重新建连接。

TODO:
- [ ] 使用 aio-pika connect_robust 建立长连接。
- [ ] channel.set_qos(prefetch_count=1)，与当前 Runner 的 prefetch=1 保持一致的保守策略。
- [ ] declare_queue 时设置 x-dead-letter-exchange=agent.analysis.dlx、x-dead-letter-routing-key=dead。
- [ ] 应用关闭时优雅关闭 channel / connection。
"""
