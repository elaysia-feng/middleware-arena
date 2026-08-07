package com.mware.notification.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mware.notification.domain.Notification;
import org.apache.ibatis.annotations.Mapper;

/**
 * 站内通知 Mapper（骨架占位）。
 * <p>
 * TODO：接入数据源时在 application.yml 启用数据源后生效；
 * 配合 markRead / pageNotifications / unreadCount 实现持久化查询。
 */
@Mapper
public interface NotificationMapper extends BaseMapper<Notification> {
}
