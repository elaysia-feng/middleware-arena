package com.mware.community.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 点赞事实实体（@TableName = post_like）。
 * <p>
 * <b>最终事实唯一来源</b>：点赞 / 取消点赞 = 插入 / 删除一行，post_id + user_id 唯一。
 * 点赞数本身不是事实，是 {@code community_post.like_count} 的异步聚合结果（最终一致）。
 * <p>
 * ★ 分库分表目标表：2 库 × 4 表，按 post_id 哈希路由。
 * 路由配置见 {@code application-sharding.yml}；MyBatis-Plus 只感知逻辑表名 post_like，
 * 物理表 post_like_0..3 的路由由 ShardingSphere-JDBC 拦截改写。
 */
@Data
@TableName("post_like")
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostLike {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 帖子 ID（分片键，路由依据） */
    private Long postId;

    /** 点赞用户 ID */
    private Long userId;

    private LocalDateTime createdAt;
}
