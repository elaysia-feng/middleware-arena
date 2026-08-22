-- 收藏链路升级：Redis Stream + RabbitMQ 异步落库所需状态与版本字段。
ALTER TABLE `community_post`
    ADD COLUMN `favorite_version` BIGINT NOT NULL DEFAULT 0
        COMMENT '最近落库的收藏事件版本' AFTER `favorite_count`;

ALTER TABLE `post_favorite`
    ADD COLUMN `favorited` TINYINT(1) NOT NULL DEFAULT 1
        COMMENT '最终收藏状态；0 为取消收藏 tombstone' AFTER `user_id`,
    ADD COLUMN `version` BIGINT NOT NULL DEFAULT 0
        COMMENT '收藏事件版本，防乱序覆盖' AFTER `favorited`,
    ADD COLUMN `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP COMMENT '状态更新时间' AFTER `created_at`;
