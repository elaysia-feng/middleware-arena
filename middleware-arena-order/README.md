# middleware-arena-order

订单服务（端口 `9006`），负责创建订单，并通过 Seata AT 协调库存服务和账户服务。

## 下单链路

```mermaid
sequenceDiagram
    autonumber
    actor User as 用户
    participant Order as order-service:9006
    participant Redis as Redis
    participant Product as product-service:9009
    participant DB1 as ma_order
    participant Storage as storage-service:9007
    participant DB2 as ma_storage
    participant Account as account-service:9008
    participant DB3 as ma_account
    participant TC as Seata TC:8091

    User->>Order: POST /order/create
    Order->>TC: 开启全局事务 XID
    Order->>Redis: SETNX create:order:{requestId}
    Order->>Product: 查询商品与单价
    Order->>DB1: 写入订单
    Order->>Storage: 扣减库存（携带 XID）
    Storage->>DB2: quantity = quantity - n
    DB2-->>TC: 注册 AT 分支与 undo_log
    Order->>Account: 扣减余额（携带 XID）
    Account->>DB3: balance = balance - amount
    DB3-->>TC: 注册 AT 分支与 undo_log
    Order->>TC: 提交全局事务
    Order-->>User: 返回订单
```

## 失败回滚

```mermaid
flowchart TD
    A[创建订单] --> B[扣减库存]
    B --> C{余额足够?}
    C -- 是 --> D[扣减余额]
    D --> E[Seata 全局提交]
    C -- 否 --> F[Account 返回 BALANCE_NOT_ENOUGH]
    F --> G[Order 检查 Feign 业务码并抛异常]
    G --> H[Seata 全局回滚]
    H --> I[ma_order 删除本次订单]
    H --> J[ma_storage 通过 undo_log 恢复库存]
    H --> K[ma_account 保持原余额]
    G --> L[删除 Redis 短期幂等键]
```

## 数据与配置

- `ma_order`、`ma_storage`、`ma_account` 三个数据库都必须有 `undo_log`。
- 首次建库执行各服务的 `sql/init.sql`；已有订单库执行 `sql/upgrade_seata_order.sql`。
- 三个服务使用同一个 `seata.tx-service-group=middleware_arena_tx_group`。
- `request_id` 同时受 Redis `SETNX` 和数据库唯一键保护。

## 验收条件

- 正常下单：订单新增，库存和余额按订单数量、金额扣减。
- 库存不足：三个业务库都不发生变化。
- 余额不足：已写订单和已扣库存均由 Seata 回滚。
- 同一用户重复提交相同 `requestId`：返回原订单，不重复扣减。
