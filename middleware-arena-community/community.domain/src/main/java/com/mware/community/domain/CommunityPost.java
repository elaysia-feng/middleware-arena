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
 * 社区帖子实体（骨架占位）。
 * <p>
 * TODO：
 *   - 字段与 sql/init.sql 同步
 *   - 接入 ES 后增加 @Document 注解及索引映射
 *   - content 字段考虑富文本 / Markdown 存储
 */
@Data
@TableName("community_post")
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CommunityPost {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    private String content;

    private Long authorId;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
