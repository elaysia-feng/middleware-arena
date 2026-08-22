# Seata AT 分布式事务实验

调用链：order-service 创建订单 → storage-service 扣库存 → account-service 扣余额。

## 对照组

- 基线组：普通本地事务与 Feign 调用，注入余额扣减失败，观察订单/库存不一致
- 实验组：订单入口使用 `@GlobalTransactional`，三个数据源启用 Seata AT 和 `undo_log`

## 可编辑白名单

- `order.biz/.../OrderServiceImpl.java`
- `storage.biz/.../StorageServiceImpl.java`
- `account.biz/.../AccountServiceImpl.java`
- 三个服务的 `application.yml`

Controller、Feign 契约、条件扣减 SQL、错误码和 `undo_log` DDL 不允许编辑。

## 默认故障与压测参数

- 并发阶梯：`5 / 20 / 50`
- 每阶持续：`30s`
- 故障注入：余额不足 20%、库存不足 10%、account 超时 5%
- 全局事务超时：`60s`
- 请求超时：`10s`

## 指标与验收

- 全局事务成功率、回滚率、P95、吞吐量
- 锁等待时间、分支事务耗时、`undo_log` 清理延迟
- 订单、库存和余额账实一致率
- 验收要求：实验组在注入失败后无半提交；基线组的不一致必须被报告识别，不能通过吞异常伪造成功
