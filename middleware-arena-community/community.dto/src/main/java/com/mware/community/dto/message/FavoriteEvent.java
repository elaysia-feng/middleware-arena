package com.mware.community.dto.message;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 收藏状态变更事件。
 * <p>
 * 事件由 Redis Lua 在修改收藏状态时写入 Stream，再由 Relay 投递 RabbitMQ；
 * {@code version} 用于数据库消费者抵御重复和乱序消息。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FavoriteEvent {
    /** 全链路幂等事件 ID。 */
    private String eventId;
    private Long postId;
    private Long userId;
    /** 目标收藏状态，true=收藏，false=取消收藏。 */
    private Boolean favorited;
    /** 同一帖子收藏事件的单调递增版本。 */
    private Long version;
    /** Redis Lua 执行后的实时收藏总数。 */
    private Long favoriteCount;
    private Long timestamp;
}
