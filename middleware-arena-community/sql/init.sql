-- middleware-arena-community 初始化 DDL
-- 接入 MySQL 时执行此脚本建表（字段与 community.domain / community.dto 实体对齐）
-- 表名统一使用 community_ 前缀，避免 MySQL 保留字（like）并保持与实体 @TableName 命名一致

-- 帖子表（对应 CommunityPost 实体，@TableName = community_post）
CREATE TABLE `community_post` (
    id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    title      VARCHAR(255) NOT NULL COMMENT '帖子标题',
    content    TEXT         NOT NULL COMMENT '帖子内容',
    author_id  BIGINT       NOT NULL COMMENT '作者用户 ID',
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    INDEX idx_author (author_id),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='社区帖子表';

-- 评论表（对应 Comment DTO：postId / authorId / parentId / content / createdAt）
CREATE TABLE `community_comment` (
    id         BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    post_id    BIGINT        NOT NULL COMMENT '帖子 ID',
    author_id  BIGINT        NOT NULL COMMENT '评论作者用户 ID',
    parent_id  BIGINT        NULL COMMENT '父评论 ID；NULL 为一级评论',
    content    VARCHAR(2000) NOT NULL COMMENT '评论内容',
    created_at DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    INDEX idx_post (post_id),
    INDEX idx_parent (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='社区评论表';

-- 点赞表（post_id + user_id 唯一，点赞 / 取消点赞即插入 / 删除）
CREATE TABLE `community_like` (
    id         BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
    post_id    BIGINT   NOT NULL COMMENT '帖子 ID',
    user_id    BIGINT   NOT NULL COMMENT '点赞用户 ID',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_post_user (post_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='社区点赞表';

-- 收藏表
CREATE TABLE `community_favorite` (
    id         BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
    post_id    BIGINT   NOT NULL COMMENT '帖子 ID',
    user_id    BIGINT   NOT NULL COMMENT '收藏用户 ID',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_post_user (post_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='社区收藏表';

-- 关注表（author_id 被关注者 + user_id 关注者，唯一）
CREATE TABLE `community_follow` (
    id         BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
    author_id  BIGINT   NOT NULL COMMENT '被关注作者用户 ID',
    user_id    BIGINT   NOT NULL COMMENT '关注者用户 ID',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_author_user (author_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='社区关注表';
