package com.mware.community.biz.search.impl;

import com.mware.community.biz.search.SearchService;
import com.mware.community.dto.response.PostResponse;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 全文搜索业务实现（骨架占位）。
 * <p>
 * TODO[社区]：ES 依赖未接入（biz pom 暂无 spring-data-elasticsearch），实现留待接入 ES 后补齐。
 */
@Service
public class SearchServiceImpl implements SearchService {

    @Override
    public List<PostResponse> search(String keyword, int page, int size) {
        // TODO[社区]：ES 全文搜索
        //   1. ES 查询 community_post 索引：title + content 字段 matchQuery，关键词高亮
        //   2. 分页返回命中结果
        //   3. 兜底方案：ES 不可用时降级为 MySQL LIKE（title / content contains keyword）
        return null;
    }
}
