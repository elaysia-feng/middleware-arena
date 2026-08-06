package com.mware.notification.domain;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 通知占位类（不持久化到数据库，仅内存 / MQ 流转）。
 * <p>
 * TODO：
 *   - 完整字段：title / content / isRead / sourceType（实验完成 / 公告 / @提醒）
 *   - 如需要持久化，改加 @TableName + @TableId 并引入 mybatis-plus-annotation
 */
@Data
public class Notification {

    /** 通知唯一标识 */
    private String id;

    /** 接收用户 ID */
    private Long userId;

    /** 通知类型：experiment_done / announcement / mention */
    private String type;

    /** 通知内容（JSON 或纯文本） */
    private String content;
}
