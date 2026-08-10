-- middleware-arena-order 初始化 DDL
-- 执行：mysql -u root -p < init.sql（接入 MySQL 时建表）

CREATE TABLE `order` (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '订单 ID',
    user_id     BIGINT       NOT NULL COMMENT '下单用户 ID',
    order_no    VARCHAR(64)  NOT NULL COMMENT '对外订单号（IdWorker 生成）',
    product_id  BIGINT       NOT NULL COMMENT '商品 ID',
    quantity    INT          NOT NULL COMMENT '购买数量',
    unit_price  BIGINT       NOT NULL COMMENT '下单时商品单价快照（单位：分），商品改价不影响历史订单',
    amount      BIGINT       NOT NULL COMMENT '总金额（单位：分）= unit_price × quantity',
    status      VARCHAR(32)  NOT NULL DEFAULT 'CREATE' COMMENT '订单状态: CREATE/PAID/CANCEL（见 OrderStatus 枚举）',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    -- 幂等：第一版仅靠 Redis SETNX（request_id 不落库）；数据库幂等兜底实验时再加 request_id 列 + 唯一索引
    UNIQUE KEY uk_order_no (order_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单表';
