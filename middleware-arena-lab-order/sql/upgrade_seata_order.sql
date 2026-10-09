-- 已有 ma_order 数据库升级脚本；全新环境直接执行 init.sql。
ALTER TABLE `order`
    ADD COLUMN request_id VARCHAR(64) NULL COMMENT '客户端幂等请求标识' AFTER order_no;

-- 旧订单没有 request_id，先用订单号回填，再收紧非空和唯一约束。
UPDATE `order` SET request_id = order_no WHERE request_id IS NULL;
ALTER TABLE `order`
    MODIFY COLUMN request_id VARCHAR(64) NOT NULL COMMENT '客户端幂等请求标识',
    ADD UNIQUE KEY uk_user_request (user_id, request_id);

CREATE TABLE IF NOT EXISTS `undo_log` (
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
