package com.mware.notification.domain;

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
 * 站内通知实体（持久化到 notification 表）。
 * <p>
 * 字段对齐 sql/init.sql 的 notification 表；
 * sourceType 区分来源（experiment / order / system），type 区分类型（experiment_done / announcement / mention）。
 */
@Data
@TableName("notification")
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {

    @TableId(type = IdType.AUTO)
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

    /** 是否已读：0 未读 / 1 已读 */
    private Boolean isRead;

    private LocalDateTime createdAt;
}
