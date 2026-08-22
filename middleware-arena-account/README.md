# middleware-arena-account

账户服务（端口 `9008`），是 Seata AT 下单事务的余额分支。

- `POST /account/deduct` 使用 `user_id + balance >= 扣减金额` 条件原子扣减，避免并发超扣。
- `GET /account/balance/{userId}` 只允许当前用户查询自己的余额。
- `sql/init.sql` 同时创建 `account_balance` 和 Seata `undo_log` 表。

完整调用和回滚图见 [订单服务 README](../middleware-arena-order/README.md)。
