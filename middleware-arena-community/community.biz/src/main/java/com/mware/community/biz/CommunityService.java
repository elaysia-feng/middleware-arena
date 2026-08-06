package com.mware.community.biz;

/**
 * 社区业务接口。
 * <p>
 * TODO：
 *   - 帖子 CRUD：发布 / 编辑 / 删除 / 分页列表
 *   - 评论：一级评论 + 回复，支持分页
 *   - 点赞 / 收藏 / 关注：Redis 计数 + 异步持久化
 *   - ES 全文搜索：帖子标题 + 内容索引，支持关键词高亮
 *   - 接入时需引入 community.mapper 依赖并启用数据源
 */
public interface CommunityService {

}
