-- middleware-arena-order 初始化 DDL
-- 执行：mysql -u root -p < init.sql（接入 MySQL 时建表）

CREATE TABLE `order` (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '订单 ID',
    user_id     BIGINT       NOT NULL COMMENT '下单用户 ID',
    order_no    VARCHAR(64)  NOT NULL COMMENT '对外订单号（IdWorker 生成）',
    product_id  BIGINT       NOT NULL COMMENT '商品 ID',
    quantity    INT          NOT NULL COMMENT '购买数量',
    amount      DECIMAL(10,2) NOT NULL COMMENT '订单金额',
    status      VARCHAR(32)  NOT NULL DEFAULT 'CREATE' COMMENT '订单状态: CREATE/PAID/CANCEL（见 OrderStatus 枚举）',
    request_id  VARCHAR(64)  NOT NULL COMMENT '幂等键：前端请求标记（createOrder 落库）',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    -- 幂等兜底唯一索引：同用户同 request_id 仅生效一次（配合 createOrder 将 request_id 落库）
    UNIQUE KEY uk_order_user_request (user_id, request_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单表';
