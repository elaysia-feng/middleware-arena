package com.mware.community.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mware.community.domain.PostLike;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PostLikeMapper extends BaseMapper<PostLike> {
    @Insert("""
            INSERT INTO post_like(post_id, user_id, liked, version, created_at, updated_at)
            VALUES(#{postId}, #{userId}, #{liked}, #{version}, NOW(), NOW())
            ON DUPLICATE KEY UPDATE
                liked = IF(VALUES(version) > version, VALUES(liked), liked),
                updated_at = IF(VALUES(version) > version, NOW(), updated_at),
                version = GREATEST(version, VALUES(version))
            """)
    int upsertState(@Param("postId") Long postId, @Param("userId") Long userId,
                    @Param("liked") boolean liked, @Param("version") Long version);
}
