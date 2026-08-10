package com.mware.community.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mware.community.domain.PostLike;
import org.apache.ibatis.annotations.Mapper;

/**
 * 点赞事实 Mapper（逻辑表 post_like，物理分片由 ShardingSphere 路由）。
 */
@Mapper
public interface PostLikeMapper extends BaseMapper<PostLike> {
}
