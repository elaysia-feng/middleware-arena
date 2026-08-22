# RabbitMQ 异步削峰实验

对比请求线程同步完成业务与请求先写消息、消费者异步处理两种模式。

## 宿主与场景

- 默认宿主：`middleware-arena-community`
- 已验证链路：Redis Stream Outbox → RabbitMQ → 点赞/收藏 MySQL 持久化
- 可替换宿主：`middleware-arena-order` 的同步/异步下单场景
- 基线组：请求线程同步写数据库
- 实验组：请求写可靠事件后立即返回，消费者异步落库

## 可编辑白名单

- `community.biz/.../LikeStreamRelay.java`
- `community.biz/.../FavoriteStreamRelay.java`
- `community.biz/.../RabbitLikeConfig.java`
- `community.biz/.../RabbitFavoriteConfig.java`
- `community.web/src/main/resources/application.yml`

事件 DTO、Mapper 幂等 SQL、鉴权和数据库表结构不可编辑，避免绕过确认或幂等条件。

## 默认压测参数

- 生产并发：`50 / 100 / 200`
- 每阶持续：`30s`
- 消费者并发：`2~8`
- prefetch：`50`
- Publisher Confirm 超时：`3s`
- 压测结束等待最终一致：`10s`

## 指标与验收

- 接口 P95、生产/消费速率、Ready/Unacked、最大积压和排空时间
- Publisher ACK/NACK、Return、消费重试、DLQ 数量
- MySQL 最终行数、计数与 Redis 状态一致
- 验收要求：业务错误率为 0；压测结束后队列归零；重复消息不造成重复计数
