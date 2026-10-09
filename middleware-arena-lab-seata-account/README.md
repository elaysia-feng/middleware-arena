# middleware-arena-lab-seata-account

Seata 余额实验参与方（注册名 `lab-seata-account-participant`，端口 `9008`），模拟下单事务中的余额分支，按实验需要启动。平台登录账号由 auth-service 管理。

- `POST /account/deduct` 使用 `user_id + balance >= 扣减金额` 条件原子扣减，避免并发超扣。
- `GET /account/balance/{userId}` 只允许当前用户查询自己的余额。
- `sql/init.sql` 同时创建 `account_balance` 和 Seata `undo_log` 表。

完整调用和回滚图见 [订单服务 README](../middleware-arena-lab-order/README.md)。
