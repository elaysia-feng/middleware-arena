package com.mware.notification.biz;

/**
 * 通知业务接口。
 * <p>
 * TODO：
 *   - RabbitMQ 消费：监听 experiment.completed 队列，收到消息后创建站内通知
 *   - 站内信 CRUD：创建 / 已读标记 / 分页列表 / 未读数
 *   - WebSocket / SSE 推送：实时推送新通知到前端
 *   - 通知类型：实验完成 / 系统公告 / @提醒 等
 */
public interface NotificationService {

}
