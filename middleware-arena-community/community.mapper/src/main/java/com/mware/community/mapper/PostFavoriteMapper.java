package com.mware.community.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mware.community.domain.PostFavorite;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 收藏事实 Mapper。
 */
@Mapper
public interface PostFavoriteMapper extends BaseMapper<PostFavorite> {
    @Insert("""
            INSERT INTO post_favorite(post_id, user_id, favorited, version, created_at, updated_at)
            VALUES(#{postId}, #{userId}, #{favorited}, #{version}, NOW(), NOW())
            ON DUPLICATE KEY UPDATE
                favorited = IF(VALUES(version) > version, VALUES(favorited), favorited),
                updated_at = IF(VALUES(version) > version, NOW(), updated_at),
                version = GREATEST(version, VALUES(version))
            """)
    int upsertState(@Param("postId") Long postId,
                    @Param("userId") Long userId,
                    @Param("favorited") boolean favorited,
                    @Param("version") Long version);
}
