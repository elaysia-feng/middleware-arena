package com.mware.community.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mware.community.domain.PostFavorite;
import org.apache.ibatis.annotations.Mapper;

/**
 * 收藏事实 Mapper。
 */
@Mapper
public interface PostFavoriteMapper extends BaseMapper<PostFavorite> {
}
