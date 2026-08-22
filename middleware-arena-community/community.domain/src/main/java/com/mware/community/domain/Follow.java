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
 * 用户关注关系实体。
 * <p>
 * {@code authorId} 是发起关注的当前用户，{@code userId} 是被关注用户；
 * 数据库唯一索引保证重复关注不会生成多条关系。
 */
@Data
@TableName("community_follow")
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Follow {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long authorId;
    private Long userId;
    private LocalDateTime createdAt;
}
