package com.mware.community.dto.response;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Redis 实时收藏状态；MySQL 为 RabbitMQ 异步持久化层。 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FavoriteStatusResponse {
    private Long postId;
    private Boolean favorited;
    private Long favoriteCount;
}
