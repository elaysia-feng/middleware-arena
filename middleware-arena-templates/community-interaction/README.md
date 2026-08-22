# 社区点赞与收藏可靠链路模板

该模板来自已验证的 community-service 链路，用于比较“同步数据库写入”与“Redis 状态写模型 + Stream Outbox + RabbitMQ 异步持久化”。

## 可编辑白名单

- `LikeRedisStore.java` / `FavoriteRedisStore.java`：Lua 原子状态、分区数、状态分片和积压保护
- `LikeStreamRelay.java` / `FavoriteStreamRelay.java`：批量、调度间隔和 Confirm 超时
- `RabbitLikeConfig.java` / `RabbitFavoriteConfig.java`：消费者并发、prefetch、重试与 DLQ
- `application.yml`：上述参数
- `jmeter/like-stress-test.jmx`：线程数、循环次数和帖子范围

Mapper 的 version 条件更新、身份 HMAC、数据库 DDL和业务断言不可编辑。

## 默认运行参数

- 并发阶梯：`20 / 50 / 100 / 200`
- 每阶持续：`30s`
- Redis Stream 分区：`64`
- 用户状态 Hash 分片：`256`
- relay batch：`200`
- 消费者并发：`2~8`
- 最终一致等待：`10s`

## 验收条件

- PUT/DELETE 重复请求不重复增减计数
- Redis 状态、聚合计数和 MySQL version 最终一致
- Publisher Confirm 成功后才删除 Stream entry
- 三个业务队列最终归零，DLQ 为 0
- 服务日志无映射、SQL、序列化和消息转换错误
