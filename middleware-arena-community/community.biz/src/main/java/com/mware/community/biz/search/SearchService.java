package com.mware.community.biz.search;

import com.mware.community.dto.response.PostResponse;

import java.util.List;

/**
 * 全文搜索业务接口（面向接口编程）。
 * <p>
 * 对外只暴露 DTO，domain 实体仅存在于实现内部。实现见 {@code impl/SearchServiceImpl}。
 */
public interface SearchService {

    /** ES 全文搜索（标题 + 内容，支持关键词高亮） */
    List<PostResponse> search(String keyword, int page, int size);
}
