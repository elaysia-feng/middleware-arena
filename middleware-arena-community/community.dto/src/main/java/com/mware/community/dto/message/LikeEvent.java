package com.mware.community.dto.message;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LikeEvent {
    public static final String ACTION_LIKE = "LIKE";
    public static final String ACTION_UNLIKE = "UNLIKE";
    private String eventId;
    private Long postId;
    private Long userId;
    private String action;
    private Boolean liked;
    private Integer delta;
    private Long version;
    private Long likeCount;
    /** epoch millis */
    private Long timestamp;
}
