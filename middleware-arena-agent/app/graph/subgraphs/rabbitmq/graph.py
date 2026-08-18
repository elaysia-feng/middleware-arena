"""RabbitMQ 专家 SubGraph 占位。

1. 分析生产速率、消费速率、堆积、ACK、prefetch 和重试相关证据。
2. 识别消费者不足、批量策略不合理、消息过大、确认机制开销等候选问题。
3. 输出 RabbitMQ 专属 findings，交给主图统一裁决。

TODO:
- [ ] 定义 RabbitMQ SubGraph 输入/输出。
- [ ] 接入 rabbitmq diagnosis Prompt。
- [ ] 增加队列深度、消费速率、ACK 等 Tool 数据源。
"""
