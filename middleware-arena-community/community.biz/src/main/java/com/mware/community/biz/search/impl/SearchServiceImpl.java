package com.mware.community.biz.search.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mware.common.web.ApiException;
import com.mware.common.web.ErrorCode;
import com.mware.community.biz.search.SearchService;
import com.mware.community.domain.CommunityPost;
import com.mware.community.dto.response.PostResponse;
import com.mware.community.mapper.CommunityPostMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 帖子搜索实现。当前环境未配置 Elasticsearch，先提供可用的 MySQL 标题/正文搜索兜底。
 */
@Service
public class SearchServiceImpl implements SearchService {

    private static final int MAX_PAGE_SIZE = 50;

    private final CommunityPostMapper communityPostMapper;

    public SearchServiceImpl(CommunityPostMapper communityPostMapper) {
        this.communityPostMapper = communityPostMapper;
    }

    @Override
    public List<PostResponse> search(String keyword, int page, int size) {
        if (keyword == null || keyword.trim().isEmpty()) {
            throw new ApiException(ErrorCode.PARAM_INVALID, "搜索关键词不能为空");
        }
        if (page < 1 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new ApiException(ErrorCode.PARAM_INVALID, "分页参数非法");
        }

        String normalizedKeyword = keyword.trim();
        Page<CommunityPost> postPage = new Page<>(page, size);
        communityPostMapper.selectPage(postPage, new LambdaQueryWrapper<CommunityPost>()
                .and(wrapper -> wrapper
                        .like(CommunityPost::getTitle, normalizedKeyword)
                        .or()
                        .like(CommunityPost::getContent, normalizedKeyword))
                .orderByDesc(CommunityPost::getCreatedAt));

        return postPage.getRecords().stream().map(this::toPostResponse).toList();
    }

    private PostResponse toPostResponse(CommunityPost post) {
        return PostResponse.builder()
                .id(post.getId())
                .title(post.getTitle())
                .content(post.getContent())
                .authorId(post.getAuthorId())
                .likeCount(post.getLikeCount())
                .favoriteCount(post.getFavoriteCount())
                .commentCount(post.getCommentCount())
                .createdAt(post.getCreatedAt())
                .build();
    }
}
