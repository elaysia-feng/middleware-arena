# middleware-arena-storage

库存服务（端口 `9007`），是 Seata AT 下单事务的库存分支。

- `POST /storage/deduct` 使用 `product_id + quantity >= 扣减量` 条件原子扣减，避免并发超卖。
- `GET /storage/stock/{productId}` 查询当前库存。
- `sql/init.sql` 同时创建 `stock` 和 Seata `undo_log` 表。

完整调用和回滚图见 [订单服务 README](../middleware-arena-order/README.md)。
