package com.mware.community.biz.comment;

import com.mware.community.dto.request.CommentRequest;
import com.mware.community.dto.response.CommentResponse;

import java.util.List;

/**
 * 评论业务接口（面向接口编程）。
 * <p>
 * 对外只暴露 DTO，domain 实体仅存在于实现内部。实现见 {@code impl/CommentServiceImpl}。
 * <p>
 * 一级评论 + 回复共用本服务：parentId 为空 = 一级评论，非空 = 回复。
 * 回复链路在 Service 层强制校验（必须同帖，禁止跨帖回复，禁止对回复再回复）。
 */
public interface CommentService {

    /** 发表评论（postId 走路径，防伪造；parentId 为空则为一级评论，非空为回复） */
    CommentResponse addComment(Long postId, CommentRequest request);

    /** 帖子评论分页（一级 + 回复扁平返回，按创建时间正序，UI 端按 parentId 折叠） */
    List<CommentResponse> pageComments(Long postId, int page, int size);

    /**
     * 拉取某条评论下的回复列表（分页）。
     * <p>
     * parentId 可为一级评论 id 或任意 comment id（保持接口通用，将来放宽二级回复时不用改签名）。
     * 父评论不存在时抛 404，方便前端排查；不做"必须是一级评论"的强制限制。
     */
    List<CommentResponse> pageReplies(Long parentId, int page, int size);

    /**
     * 作者删除自己的评论。
     * <p>
     * postId / commentId 都走路径，两者必须匹配（防跨帖操作）。
     * 仅作者可删（管理员删除另开方法），硬删 + 级联删回复。
     */
    void deleteComment(Long postId, Long commentId);
}