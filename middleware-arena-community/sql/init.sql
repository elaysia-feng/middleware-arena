-- middleware-arena-community 初始化 SQL（占位，待业务接入后补充完整 DDL）
-- TODO：根据 CommunityPost 实体字段完善建表语句，补充索引与初始数据
CREATE TABLE IF NOT EXISTS community_post
(
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    title      VARCHAR(255) NOT NULL COMMENT '帖子标题',
    content    TEXT         NOT NULL COMMENT '帖子内容',
    author_id  BIGINT       NOT NULL COMMENT '作者用户 ID',
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_author (author_id),
    INDEX idx_created_at (created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='社区帖子表';
