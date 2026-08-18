package com.mware.community.biz.post;

import com.mware.community.dto.request.CreatePostRequest;
import com.mware.community.dto.response.PostResponse;

import java.util.List;

/**
 * 帖子业务接口（面向接口编程）。
 * <p>
 * 对外只暴露 DTO，domain 实体仅存在于实现内部。实现见 {@code impl/PostServiceImpl}。
 */
public interface PostService {

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
}
