-- Elias 订单链路 mock 数据
-- 用户：elias，固定使用 ma_auth.user.id = 4
-- 说明：只新增/幂等更新 ELIAS-MOCK 数据，不删除现有业务数据。

START TRANSACTION;
SET SESSION cte_max_recursion_depth = 10000;

-- 1. 给 elias 准备充足余额（单位：分）。
INSERT INTO ma_account.account_balance (user_id, balance)
VALUES (4, 99999999)
ON DUPLICATE KEY UPDATE balance = GREATEST(balance, 99999999);

-- 2. 新增一组独立商品，避免影响原有商品 1、2。
INSERT INTO ma_product.product (id, name, price, description)
VALUES
    (1001, 'Elias Mock - Redis 缓存课程', 9900, '压测商品：Redis Cache-Aside'),
    (1002, 'Elias Mock - RabbitMQ 实战', 12900, '压测商品：RabbitMQ 异步下单'),
    (1003, 'Elias Mock - Seata 分布式事务', 15900, '压测商品：Seata AT 下单'),
    (1004, 'Elias Mock - Java 性能调优', 19900, '压测商品：JVM 与服务性能'),
    (1005, 'Elias Mock - Spring Cloud', 10900, '压测商品：Spring Cloud 微服务'),
    (1006, 'Elias Mock - MySQL 优化', 13900, '压测商品：数据库索引与事务'),
    (1007, 'Elias Mock - 分布式锁', 8900, '压测商品：Redis 分布式锁'),
    (1008, 'Elias Mock - 消息可靠性', 11900, '压测商品：消息确认与重试'),
    (1009, 'Elias Mock - 库存服务', 14900, '压测商品：库存扣减链路'),
    (1010, 'Elias Mock - 账户服务', 12900, '压测商品：余额扣减链路'),
    (1011, 'Elias Mock - 链路追踪', 7900, '压测商品：服务调用观测'),
    (1012, 'Elias Mock - 压测工具包', 6900, '压测商品：k6 与指标采集')
ON DUPLICATE KEY UPDATE
    name = VALUES(name),
    price = VALUES(price),
    description = VALUES(description);

-- 3. 每个 mock 商品准备 10000 件库存。
INSERT INTO ma_storage.stock (product_id, quantity)
VALUES
    (1001, 10000), (1002, 10000), (1003, 10000), (1004, 10000),
    (1005, 10000), (1006, 10000), (1007, 10000), (1008, 10000),
    (1009, 10000), (1010, 10000), (1011, 10000), (1012, 10000)
ON DUPLICATE KEY UPDATE quantity = GREATEST(quantity, 10000);

-- 4. 写入 30 条 elias 历史订单，覆盖 CREATE / PAID / CANCEL 状态。
-- request_id 带唯一前缀，重复执行不会产生重复订单。
INSERT INTO ma_order.`order`
    (user_id, order_no, request_id, product_id, quantity, unit_price, amount, status, created_at, updated_at)
WITH RECURSIVE seq AS (
    SELECT 1 AS n
    UNION ALL
    SELECT n + 1 FROM seq WHERE n < 30
), mock_order AS (
    SELECT
        n,
        1001 + MOD(n - 1, 12) AS product_id,
        1 + MOD(n, 3) AS quantity,
        CASE 1001 + MOD(n - 1, 12)
            WHEN 1001 THEN 9900
            WHEN 1002 THEN 12900
            WHEN 1003 THEN 15900
            WHEN 1004 THEN 19900
            WHEN 1005 THEN 10900
            WHEN 1006 THEN 13900
            WHEN 1007 THEN 8900
            WHEN 1008 THEN 11900
            WHEN 1009 THEN 14900
            WHEN 1010 THEN 12900
            WHEN 1011 THEN 7900
            WHEN 1012 THEN 6900
        END AS unit_price
    FROM seq
)
SELECT
    4,
    CONCAT('ELIAS-MOCK-', LPAD(n, 4, '0')),
    CONCAT('elias-mock-request-', LPAD(n, 4, '0')),
    product_id,
    quantity,
    unit_price,
    unit_price * quantity,
    CASE
        WHEN MOD(n, 5) = 0 THEN 'CANCEL'
        WHEN MOD(n, 2) = 0 THEN 'PAID'
        ELSE 'CREATE'
    END,
    DATE_SUB(NOW(), INTERVAL (30 - n) DAY),
    DATE_SUB(NOW(), INTERVAL (30 - n) DAY)
FROM mock_order
ON DUPLICATE KEY UPDATE
    product_id = VALUES(product_id),
    quantity = VALUES(quantity),
    unit_price = VALUES(unit_price),
    amount = VALUES(amount),
    status = VALUES(status),
    updated_at = VALUES(updated_at);

-- 5. 扩充大数据量：新增 10000 个商品和库存记录。
-- 商品 ID 使用 2001-12000，避免影响前面的 1001-1012 mock 商品。
INSERT INTO ma_product.product (id, name, price, description)
WITH RECURSIVE seq AS (
    SELECT 1 AS n
    UNION ALL
    SELECT n + 1 FROM seq WHERE n < 10000
)
SELECT
    2000 + n,
    CONCAT('Elias Large Mock 商品-', LPAD(n, 5, '0')),
    1000 + MOD(n, 100) * 100,
    '大数据量压测商品，用于 Redis 与 MySQL 缓存对比'
FROM seq
ON DUPLICATE KEY UPDATE
    name = VALUES(name),
    price = VALUES(price),
    description = VALUES(description);

INSERT INTO ma_storage.stock (product_id, quantity)
WITH RECURSIVE seq AS (
    SELECT 1 AS n
    UNION ALL
    SELECT n + 1 FROM seq WHERE n < 10000
)
SELECT 2000 + n, 10000
FROM seq
ON DUPLICATE KEY UPDATE quantity = GREATEST(quantity, 10000);

-- 6. 扩充 10000 条 elias 订单，覆盖不同商品、数量、状态和创建时间。
-- request_id 唯一且带固定前缀，脚本重复执行不会重复插入。
INSERT INTO ma_order.`order`
    (user_id, order_no, request_id, product_id, quantity, unit_price, amount, status, created_at, updated_at)
WITH RECURSIVE seq AS (
    SELECT 1 AS n
    UNION ALL
    SELECT n + 1 FROM seq WHERE n < 10000
)
SELECT
    4,
    CONCAT('ELIAS-LARGE-', LPAD(n, 6, '0')),
    CONCAT('elias-large-request-', LPAD(n, 6, '0')),
    2000 + n,
    1 + MOD(n, 5),
    1000 + MOD(n, 100) * 100,
    (1000 + MOD(n, 100) * 100) * (1 + MOD(n, 5)),
    CASE
        WHEN MOD(n, 10) = 0 THEN 'CANCEL'
        WHEN MOD(n, 3) = 0 THEN 'PAID'
        ELSE 'CREATE'
    END,
    DATE_SUB(NOW(), INTERVAL MOD(n, 365) DAY),
    DATE_SUB(NOW(), INTERVAL MOD(n, 365) DAY)
FROM seq
ON DUPLICATE KEY UPDATE
    product_id = VALUES(product_id),
    quantity = VALUES(quantity),
    unit_price = VALUES(unit_price),
    amount = VALUES(amount),
    status = VALUES(status),
    updated_at = VALUES(updated_at);

COMMIT;

-- 验证结果
SELECT 'elias account' AS item, user_id, balance
FROM ma_account.account_balance
WHERE user_id = 4;

SELECT 'elias mock orders' AS item, COUNT(*) AS total
FROM ma_order.`order`
WHERE user_id = 4 AND request_id LIKE 'elias-mock-request-%';

SELECT 'elias large mock orders' AS item, COUNT(*) AS total
FROM ma_order.`order`
WHERE user_id = 4 AND request_id LIKE 'elias-large-request-%';

SELECT product_id, quantity
FROM ma_storage.stock
WHERE product_id BETWEEN 1001 AND 1012
ORDER BY product_id;
