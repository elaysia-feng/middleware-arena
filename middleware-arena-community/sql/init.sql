-- middleware-arena-community 初始化 DDL
-- 接入 MySQL 时执行此脚本建表（字段与 community.domain / community.dto 实体对齐）
-- 表名：post_* 为业务事实表（post_like / post_favorite 分库分表），community_* 为常规业务表

-- ============================================================
-- 帖子表（对应 CommunityPost 实体，@TableName = community_post）
-- 计数列由异步聚合链路写入（最终一致）：点赞/收藏/评论走 outbox + MQ → 聚合 → 批量刷入
-- ============================================================
CREATE TABLE IF NOT EXISTS `community_post` (
    id             BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    title          VARCHAR(255)  NOT NULL COMMENT '帖子标题',
    content        TEXT          NOT NULL COMMENT '帖子内容',
    author_id      BIGINT        NOT NULL COMMENT '作者用户 ID',
    like_count     BIGINT        NOT NULL DEFAULT 0 COMMENT '点赞数（异步聚合写入，最终一致）',
    like_version   BIGINT        NOT NULL DEFAULT 0 COMMENT '最近落库的点赞事件版本',
    favorite_count BIGINT        NOT NULL DEFAULT 0 COMMENT '收藏数（异步聚合写入，最终一致）',
    favorite_version BIGINT      NOT NULL DEFAULT 0 COMMENT '最近落库的收藏事件版本',
    comment_count  BIGINT        NOT NULL DEFAULT 0 COMMENT '评论数（异步聚合写入，最终一致）',
    created_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    INDEX idx_author (author_id),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='社区帖子表';

-- ============================================================
-- 点赞事实表（对应 PostLike 实体，@TableName = post_like）
-- ★ 分库分表目标表：2 库 × 4 表，按 post_id 哈希路由（逻辑表 post_like → post_like_0..3）
--   路由配置见 community.web/src/main/resources/application-sharding.yml（接入 ShardingSphere-JDBC 后启用）
--   本 DDL 建的是"逻辑表"，单机直连也成立；启用分片后物理表为 post_like_0..3（见文末分片 DDL）
-- post_id + user_id 唯一 → 点赞/取消点赞即 插入/删除 事实行
-- ============================================================
CREATE TABLE IF NOT EXISTS `post_like` (
    id         BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
    post_id    BIGINT   NOT NULL COMMENT '帖子 ID（分片键，路由依据）',
    user_id    BIGINT   NOT NULL COMMENT '点赞用户 ID',
    liked      TINYINT(1) NOT NULL DEFAULT 1 COMMENT '最终点赞状态；0 为取消点赞 tombstone',
    version    BIGINT   NOT NULL DEFAULT 0 COMMENT '点赞事件版本，防乱序覆盖',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '点赞时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '状态更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_post_user (post_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='社区点赞事实表（分库分表）';

-- 收藏表（对应 PostFavorite 实体，@TableName = post_favorite）
CREATE TABLE IF NOT EXISTS `post_favorite` (
    id         BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
    post_id    BIGINT   NOT NULL COMMENT '帖子 ID',
    user_id    BIGINT   NOT NULL COMMENT '收藏用户 ID',
    favorited  TINYINT(1) NOT NULL DEFAULT 1 COMMENT '最终收藏状态；0 为取消收藏 tombstone',
    version    BIGINT   NOT NULL DEFAULT 0 COMMENT '收藏事件版本，防乱序覆盖',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '收藏时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '状态更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_post_user (post_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='社区收藏表';

-- ============================================================
-- 事务性 Outbox 事件表（对应 EventOutbox 实体，@TableName = event_outbox）
-- ★ 分库分表目标表：2 库 × 4 表，按 aggregate_id(post_id) 哈希路由，与 post_like 同库同片（保证同分片 Join/一致性）
-- 与业务事实表同事务双写：本地事务提交即成功，OutboxRelay 扫描 PENDING → 投递 RabbitMQ → 置 SENT
-- event_id 全局唯一（UUID），消费者据此幂等（consumer_event 表）
-- ============================================================
CREATE TABLE IF NOT EXISTS `event_outbox` (
    id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    event_id     VARCHAR(64)  NOT NULL COMMENT '事件唯一 ID（业务生成 UUID），消费者幂等依据',
    aggregate_id BIGINT       NOT NULL COMMENT '聚合根 ID（= postId，分片键，与 post_like 同片）',
    event_type   VARCHAR(32)  NOT NULL COMMENT '事件类型：LIKE / UNLIKE / FAVORITE / UNFAVORITE / COMMENT',
    payload      TEXT         NOT NULL COMMENT '事件体 JSON（LikeEvent 序列化）',
    status       VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING 待发送 / SENT 已投递 / FAILED 投递失败',
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    sent_at      DATETIME     NULL COMMENT '投递成功时间（status=SENT 时写入）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_event_id (event_id),
    INDEX idx_status (status, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='事务性 Outbox 事件表（分库分表）';

-- ============================================================
-- 消费者幂等表（对应 ConsumerEvent 实体，@TableName = consumer_event）
-- event_id + consumer_name 唯一：RabbitMQ 重复投递时，唯一键冲突 = 已消费，直接跳过
-- ============================================================
CREATE TABLE IF NOT EXISTS `consumer_event` (
    id             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    event_id       VARCHAR(64)  NOT NULL COMMENT '事件 ID（对齐 event_outbox.event_id）',
    consumer_name  VARCHAR(32)  NOT NULL COMMENT '消费者名称：count / cache / statistics',
    processed_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '处理时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_event_consumer (event_id, consumer_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消费者幂等表';

-- 评论表（对应 Comment 实体，@TableName = community_comment）
CREATE TABLE IF NOT EXISTS `community_comment` (
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

-- 关注表（author_id 被关注者 + user_id 关注者，唯一）
CREATE TABLE IF NOT EXISTS `community_follow` (
    id         BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
    author_id  BIGINT   NOT NULL COMMENT '被关注作者用户 ID',
    user_id    BIGINT   NOT NULL COMMENT '关注者用户 ID',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_author_user (author_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='社区关注表';

-- ============================================================
-- ★ 分片 DDL（接入 ShardingSphere-JDBC 后，在 2 个数据源各建 4 张物理表）
-- 库：ds_0 / ds_1（2 库）；每库 post_like_0..3、event_outbox_0..3（每库 4 表）
-- 分片键：post_id；行表达式  post_like_$->{post_id % 4}，库规则  $->{post_id % 2}
-- 与逻辑表 DDL 同构（表名不同），建表语句样例：
--
--   CREATE TABLE `post_like_0` LIKE `post_like`;   -- 以此复制 post_like_1..3 / event_outbox_0..3
--
-- 或直接：
--
--   CREATE TABLE IF NOT EXISTS `post_like_0` (
--       id BIGINT NOT NULL AUTO_INCREMENT, post_id BIGINT NOT NULL, user_id BIGINT NOT NULL,
--       created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
--       PRIMARY KEY (id), UNIQUE KEY uk_post_user (post_id, user_id)
--   ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
--
-- 路由配置见：community.web/src/main/resources/application-sharding.yml
-- ============================================================
