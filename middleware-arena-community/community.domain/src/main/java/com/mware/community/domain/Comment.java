package com.mware.community.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 社区评论实体（字段与 sql/init.sql 的 community_comment 表对齐）。
 * <p>
 * 一级评论 + 回复共用：parentId 为空表示一级评论，非空表示回复。
 */
@Data
@TableName("community_comment")
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Comment {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long postId;

    /** 评论作者用户 ID（表列 author_id） */
    private Long authorId;

    /** 父评论 ID；空 = 一级评论，非空 = 回复 */
    private Long parentId;

    private String content;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
