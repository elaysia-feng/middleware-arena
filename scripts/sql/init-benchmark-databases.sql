-- 压测专用数据库初始化脚本。
-- 只创建 benchmark 库，不修改 ma_order、ma_account、ma_storage 正式库。
-- 执行后，再把对应服务的 MYSQL_DATABASE 配置切换到这些库。

CREATE DATABASE IF NOT EXISTS `ma_order_benchmark`
    DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS `ma_account_benchmark`
    DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS `ma_storage_benchmark`
    DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `ma_order_benchmark`.`order` (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '订单 ID',
    user_id     BIGINT       NOT NULL COMMENT '下单用户 ID',
    order_no    VARCHAR(64)  NOT NULL COMMENT '对外订单号',
    request_id  VARCHAR(64)  NOT NULL COMMENT '客户端幂等请求标识',
    product_id  BIGINT       NOT NULL COMMENT '商品 ID',
    quantity    INT          NOT NULL COMMENT '购买数量',
    unit_price  BIGINT       NOT NULL COMMENT '单价，单位：分',
    amount      BIGINT       NOT NULL COMMENT '订单金额，单位：分',
    status      VARCHAR(32)  NOT NULL DEFAULT 'CREATE',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_no (order_no),
    UNIQUE KEY uk_user_request (user_id, request_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `ma_order_benchmark`.`undo_log` (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    branch_id     BIGINT       NOT NULL,
    xid           VARCHAR(128) NOT NULL,
    context       VARCHAR(128) NOT NULL,
    rollback_info LONGBLOB     NOT NULL,
    log_status    INT          NOT NULL,
    log_created   DATETIME     NOT NULL,
    log_modified  DATETIME     NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY ux_undo_log (xid, branch_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `ma_account_benchmark`.`account_balance` (
    id         BIGINT   NOT NULL AUTO_INCREMENT,
    user_id    BIGINT   NOT NULL,
    balance    BIGINT   NOT NULL DEFAULT 0 COMMENT '余额，单位：分',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `ma_account_benchmark`.`undo_log` (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    branch_id     BIGINT       NOT NULL,
    xid           VARCHAR(128) NOT NULL,
    context       VARCHAR(128) NOT NULL,
    rollback_info LONGBLOB     NOT NULL,
    log_status    INT          NOT NULL,
    log_created   DATETIME     NOT NULL,
    log_modified  DATETIME     NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY ux_undo_log (xid, branch_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `ma_storage_benchmark`.`stock` (
    id          BIGINT   NOT NULL AUTO_INCREMENT,
    product_id  BIGINT   NOT NULL,
    quantity    INT      NOT NULL DEFAULT 0,
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_product_id (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `ma_storage_benchmark`.`undo_log` (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    branch_id     BIGINT       NOT NULL,
    xid           VARCHAR(128) NOT NULL,
    context       VARCHAR(128) NOT NULL,
    rollback_info LONGBLOB     NOT NULL,
    log_status    INT          NOT NULL,
    log_created   DATETIME     NOT NULL,
    log_modified  DATETIME     NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY ux_undo_log (xid, branch_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 示例：用户自行准备压测数据（按需修改，不自动插入业务数据）
-- INSERT INTO ma_account_benchmark.account_balance(user_id, balance)
-- VALUES (4, 100000000) ON DUPLICATE KEY UPDATE balance = VALUES(balance);
-- INSERT INTO ma_storage_benchmark.stock(product_id, quantity)
-- VALUES (1, 1000000) ON DUPLICATE KEY UPDATE quantity = VALUES(quantity);
