-- middleware-arena-account 初始化 DDL
-- 接入 MySQL 时执行此脚本建表

CREATE TABLE `account_balance` (
    id         BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id    BIGINT         NOT NULL COMMENT '用户 ID',
    balance    DECIMAL(12,2)  NOT NULL DEFAULT 0.00 COMMENT '账户余额',
    created_at DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='账户余额表';

-- Seata AT 模式必须的 undo_log 表（每个业务库都需要）
CREATE TABLE `undo_log` (
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Seata AT undo_log';
