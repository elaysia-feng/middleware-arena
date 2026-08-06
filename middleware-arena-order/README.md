# middleware-arena-order

订单服务（端口 9006），Seata 分布式事务实验的宿主服务（order → storage → account）。

## TODO

- [ ] Seata AT 分布式事务：创建订单 → 扣库存 → 扣余额
- [ ] Redis 缓存实验：订单详情 Cache-Aside
- [ ] RabbitMQ 异步下单实验：MQ 队列消费
- [ ] 接入 MySQL 数据源
- [ ] 接入 order.mapper、order.feign 到 biz/web 层
