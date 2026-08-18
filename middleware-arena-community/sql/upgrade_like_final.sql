-- 最终版点赞数据库升级：Redis 同步写模型，RabbitMQ 异步落 MySQL，version 抵抗重复/乱序。
USE ma_community;
ALTER TABLE community_post ADD COLUMN like_version BIGINT NOT NULL DEFAULT 0 COMMENT '最近已持久化点赞事件版本' AFTER like_count;
DROP TABLE IF EXISTS post_like;
CREATE TABLE post_like (
    post_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    liked TINYINT(1) NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (post_id, user_id),
    KEY idx_user_liked (user_id, liked),
    KEY idx_post_liked (post_id, liked)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='点赞状态事实表（单库模板）';
