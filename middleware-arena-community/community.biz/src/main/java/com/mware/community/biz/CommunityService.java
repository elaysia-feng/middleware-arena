package com.mware.community.biz;

import com.mware.community.dto.request.CommentRequest;
import com.mware.community.dto.request.CreatePostRequest;
import com.mware.community.dto.response.CommentResponse;
import com.mware.community.dto.response.LikeStatusResponse;
import com.mware.community.dto.response.PostResponse;

import java.util.List;

/**
 * 社区业务接口。
 * <p>
 * 方法签名已定，具体实现留待接入 community.mapper / Redis / ES 后补齐。
 * 对外只暴露 DTO（Request / Response），domain 实体仅存在于 Service / mapper 内部。
 */
public interface CommunityService {

    /** 发布帖子 */
    PostResponse createPost(CreatePostRequest request);

    /** 编辑帖子（postId 走路径，防伪造） */
    PostResponse updatePost(Long postId, CreatePostRequest request);

    /** 删除帖子 */
    void deletePost(Long postId);

    /** 帖子详情 */
    PostResponse getPost(Long postId);

    /** 帖子分页列表 */
    List<PostResponse> pagePosts(int page, int size);

    /** 发表评论（postId 走路径，防伪造；parentId 为空则为一级评论，非空为回复） */
    CommentResponse addComment(Long postId, CommentRequest request);

    /** 帖子评论分页 */
    List<CommentResponse> pageComments(Long postId, int page, int size);

    /** 点赞 / 取消点赞（事务性 Outbox：post_like 事实 + event_outbox 事件同事务双写，异步聚合计数） */
    void like(Long postId, Long userId);

    /** 点赞状态（liked 读 Redis 集合 / Bitmap，likeCount 读 Redis 缓存，最终一致） */
    LikeStatusResponse likeStatus(Long postId);

    /** 收藏 / 取消收藏 */
    void favorite(Long postId, Long userId);

    /** 关注 / 取消关注 */
    void follow(Long authorId, Long userId);

    /** ES 全文搜索（标题 + 内容，支持关键词高亮） */
    List<PostResponse> search(String keyword, int page, int size);
}
