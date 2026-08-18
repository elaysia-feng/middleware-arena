-- 最终版点赞物理分片 DDL：2 库 × 4 表 = 8 个物理分片。
-- 使用显式 DDL，不依赖 ma_community.post_like 与分片库位于同一个 MySQL 实例。
-- ShardingSphere 路由：post_id % 8 的 0..3 -> ds_0，4..7 -> ds_1；表号 post_id % 4。

CREATE DATABASE IF NOT EXISTS ma_community_ds0 DEFAULT CHARACTER SET utf8mb4;
CREATE DATABASE IF NOT EXISTS ma_community_ds1 DEFAULT CHARACTER SET utf8mb4;

USE ma_community_ds0;

CREATE TABLE IF NOT EXISTS post_like_0 (
    post_id    BIGINT       NOT NULL,
    user_id    BIGINT       NOT NULL,
    liked      TINYINT(1)   NOT NULL DEFAULT 0,
    version    BIGINT       NOT NULL DEFAULT 0,
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (post_id, user_id),
    KEY idx_user_liked (user_id, liked),
    KEY idx_post_liked (post_id, liked)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='点赞状态事实分片 0';

CREATE TABLE IF NOT EXISTS post_like_1 LIKE post_like_0;
CREATE TABLE IF NOT EXISTS post_like_2 LIKE post_like_0;
CREATE TABLE IF NOT EXISTS post_like_3 LIKE post_like_0;

USE ma_community_ds1;

CREATE TABLE IF NOT EXISTS post_like_0 (
    post_id    BIGINT       NOT NULL,
    user_id    BIGINT       NOT NULL,
    liked      TINYINT(1)   NOT NULL DEFAULT 0,
    version    BIGINT       NOT NULL DEFAULT 0,
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (post_id, user_id),
    KEY idx_user_liked (user_id, liked),
    KEY idx_post_liked (post_id, liked)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='点赞状态事实分片 0';

CREATE TABLE IF NOT EXISTS post_like_1 LIKE post_like_0;
CREATE TABLE IF NOT EXISTS post_like_2 LIKE post_like_0;
CREATE TABLE IF NOT EXISTS post_like_3 LIKE post_like_0;
