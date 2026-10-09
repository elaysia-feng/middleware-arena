# middleware-arena-lab-seata-storage

Seata 库存实验参与方（注册名 `lab-seata-storage-participant`，端口 `9007`），模拟下单事务中的库存分支，按实验需要启动。

- `POST /storage/deduct` 使用 `product_id + quantity >= 扣减量` 条件原子扣减，避免并发超卖。
- `GET /storage/stock/{productId}` 查询当前库存。
- `sql/init.sql` 同时创建 `stock` 和 Seata `undo_log` 表。

完整调用和回滚图见 [订单服务 README](../middleware-arena-lab-order/README.md)。
