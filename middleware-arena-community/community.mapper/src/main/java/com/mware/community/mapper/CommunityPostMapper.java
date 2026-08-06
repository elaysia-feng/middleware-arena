package com.mware.community.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mware.community.domain.CommunityPost;
import org.apache.ibatis.annotations.Mapper;

/**
 * 帖子 Mapper（骨架占位）。
 * <p>
 * TODO：接入数据源时把 community.mapper 依赖引入 community.biz，
 * 并在 application.yml 启用数据源后生效。
 */
@Mapper
public interface CommunityPostMapper extends BaseMapper<CommunityPost> {
}
