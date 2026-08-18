package com.mware.community.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mware.community.domain.CommunityPost;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface CommunityPostMapper extends BaseMapper<CommunityPost> {
    @Update("""
            UPDATE community_post
            SET like_count = #{likeCount}, like_version = #{version}
            WHERE id = #{postId} AND like_version < #{version}
            """)
    int updateLikeCountIfNewer(@Param("postId") Long postId,
                               @Param("likeCount") Long likeCount,
                               @Param("version") Long version);
}
