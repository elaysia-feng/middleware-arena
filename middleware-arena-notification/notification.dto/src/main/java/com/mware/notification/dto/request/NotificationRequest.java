package com.mware.notification.dto.request;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 创建 / 推送站内通知的请求。
 * <p>
 * 接收者 userId 不信任客户端：由服务端从 {@code UserContext} 取（见 NotificationServiceImpl）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationRequest {

    /** 通知类型：experiment_done / announcement / mention */
    private String type;

    /** 来源类型：experiment / order / system */
    private String sourceType;

    /** 来源记录 ID（如实验 taskId / 订单号），便于跳转与去重 */
    private Long sourceId;

    /** 通知标题 */
    private String title;

    /** 通知内容（JSON 或纯文本） */
    private String content;
}
