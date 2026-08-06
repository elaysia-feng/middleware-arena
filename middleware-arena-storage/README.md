# middleware-arena-storage

库存服务（端口 9007），Seata AT 参与方：扣库存。

## TODO

- [ ] Seata AT 参与方：扣库存接口（被 order-service 通过 Feign 调用）
- [ ] undo_log 表（Seata AT 自动回滚）
- [ ] 接入 MySQL 数据源
- [ ] 接入 storage.mapper 到 biz/web 层
