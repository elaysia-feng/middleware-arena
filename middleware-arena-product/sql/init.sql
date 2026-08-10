-- middleware-arena-product 初始化 DDL
-- 接入 MySQL 时执行此脚本建表

CREATE TABLE `product` (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    name        VARCHAR(128) NOT NULL COMMENT '商品名称',
    price       BIGINT       NOT NULL COMMENT '单价（单位：分，order 计算订单金额 amount = price × quantity 的基准）',
    description VARCHAR(512) NULL COMMENT '商品描述',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品表';

-- 示例数据（单位：分，便于联调 order 下单金额计算）
INSERT INTO product (id, name, price, description) VALUES
    (1, 'Java 核心技术', 8900, '示例商品：Java 教程'),
    (2, 'Spring 实战',   6900, '示例商品：Spring 教程');
