# middleware-arena-notification

通知服务（端口 `9005`），提供站内通知持久化、未读数、已读状态、RabbitMQ 实验完成事件消费和 SSE 实时推送。

## 主要接口

- `GET /notification/list`：分页查询当前用户通知。
- `GET /notification/unread-count`：查询未读数。
- `POST /notification/{id}/read`：按用户归属标记已读。
- `GET /notification/stream`：建立当前用户 SSE 连接。
- RabbitMQ：`experiment.completed.exchange` → `notification.experiment.completed.queue`。

实验完成消息需包含 `userId`、`taskId`，可选 `summary`。服务按用户、来源、任务和类型幂等落库，在线用户收到 `notification` SSE 事件，离线用户稍后从列表读取。
