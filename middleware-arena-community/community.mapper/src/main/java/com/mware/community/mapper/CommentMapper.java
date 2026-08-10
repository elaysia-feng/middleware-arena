package com.mware.community.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mware.community.domain.Comment;
import org.apache.ibatis.annotations.Mapper;

/**
 * 评论 Mapper（@TableName = community_comment）。
 */
@Mapper
public interface CommentMapper extends BaseMapper<Comment> {
}
