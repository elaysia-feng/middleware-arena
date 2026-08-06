# Seata 分布式事务实验模板（TODO）

> 调用链：order-service → storage-service 扣库存 → account-service 扣余额。

## 对比
无分布式事务 vs Seata AT 模式

## 观察指标
数据一致性 / 事务成功率 / P95 / 锁等待时间 / 吞吐量下降幅度

## 宿主服务
TODO：order / storage / account 三个服务联调，feign 接口在 `middleware-arena-order` 等仓库已占位。
