# middleware-arena-account

账户服务（端口 9008），Seata AT 参与方：扣余额。

## TODO

- [ ] Seata AT 参与方：扣余额接口（被 order-service 通过 Feign 调用）
- [ ] undo_log 表（Seata AT 自动回滚）
- [ ] 接入 MySQL 数据源
- [ ] 接入 account.mapper 到 biz/web 层
