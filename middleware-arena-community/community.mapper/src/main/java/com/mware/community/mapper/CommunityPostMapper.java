package com.mware.community.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mware.community.domain.CommunityPost;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/** 帖子基础 CRUD 与异步聚合计数的条件更新 Mapper。 */
@Mapper
public interface CommunityPostMapper extends BaseMapper<CommunityPost> {
    /** 仅当事件版本更新时覆盖点赞数，避免 MQ 乱序导致计数回退。 */
    @Update("""
            UPDATE community_post
            SET like_count = #{likeCount}, like_version = #{version}
            WHERE id = #{postId} AND like_version < #{version}
            """)
    int updateLikeCountIfNewer(@Param("postId") Long postId,
                               @Param("likeCount") Long likeCount,
                               @Param("version") Long version);

    /** 仅当事件版本更新时覆盖收藏数，重复或旧事件不会修改帖子。 */
    @Update("""
            UPDATE community_post
            SET favorite_count = #{favoriteCount}, favorite_version = #{version}
            WHERE id = #{postId} AND favorite_version < #{version}
            """)
    int updateFavoriteCountIfNewer(@Param("postId") Long postId,
                                   @Param("favoriteCount") Long favoriteCount,
                                   @Param("version") Long version);

    /** 原子增减评论数，并用 GREATEST 防止异常重放把计数减成负数。 */
    @Update("""
            UPDATE community_post
            SET comment_count = GREATEST(comment_count + #{delta}, 0)
            WHERE id = #{postId}
            """)
    int changeCommentCount(@Param("postId") Long postId, @Param("delta") long delta);
}
