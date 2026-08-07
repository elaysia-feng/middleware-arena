package com.mware.notification.dto.response;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 站内通知响应对象（对外暴露，含展示所需字段）。
 * <p>
 * 字段对齐 {@code notification.domain.Notification} 与 sql/init.sql 的 notification 表。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationResponse {

    private Long id;

    /** 接收用户 ID */
    private Long userId;

    /** 通知类型：experiment_done / announcement / mention */
    private String type;

    /** 来源类型：experiment / order / system */
    private String sourceType;

    /** 通知标题 */
    private String title;

    /** 通知内容（JSON 或纯文本） */
    private String content;

    /** 是否已读：false 未读 / true 已读 */
    private Boolean isRead;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
