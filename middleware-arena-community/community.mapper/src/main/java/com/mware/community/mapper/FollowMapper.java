package com.mware.community.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mware.community.domain.Follow;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 用户关注关系 Mapper。 */
@Mapper
public interface FollowMapper extends BaseMapper<Follow> {
    /** 依赖唯一索引实现幂等关注；已存在时返回 0，不抛重复键异常。 */
    @Insert("""
            INSERT IGNORE INTO community_follow(author_id, user_id, created_at)
            VALUES(#{authorId}, #{userId}, NOW())
            """)
    int insertIgnore(@Param("authorId") Long authorId, @Param("userId") Long userId);
}
