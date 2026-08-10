-- middleware-arena-notification 初始化 SQL
-- 执行：mysql -u root -p < init.sql（接入 MySQL 时建表）
-- 字段与 Notification 实体对齐

CREATE TABLE IF NOT EXISTS `notification`
(
    id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '通知 ID',
    user_id     BIGINT       NOT NULL COMMENT '接收用户 ID',
    type        VARCHAR(32)  NOT NULL COMMENT '通知类型：experiment_done / announcement / mention',
    source_type VARCHAR(32)  NULL COMMENT '来源类型：experiment / order / system',
    source_id   BIGINT       NULL COMMENT '来源记录 ID（如实验 taskId / 订单号），便于跳转与去重',
    title       VARCHAR(128) NULL COMMENT '通知标题',
    content     TEXT         NULL COMMENT '通知内容（JSON 或纯文本）',
    is_read     TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否已读：0 未读 / 1 已读',
    read_at     DATETIME     NULL COMMENT '已读时间（is_read=1 时记录）',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_user (user_id),
    INDEX idx_user_read (user_id, is_read),
    INDEX idx_source (source_type, source_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='站内通知表';
