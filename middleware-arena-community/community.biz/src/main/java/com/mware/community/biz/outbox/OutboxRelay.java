package com.mware.community.biz.outbox;

/**
 * 事务性 Outbox Relay：扫描 event_outbox 中 PENDING 的事件 → 投递 RabbitMQ → 置 SENT。
 * <p>
 * <b>为什么能可靠投递</b>：点赞写库与 outbox 写入同事务（见 like/impl/LikeServiceImpl.like），
 * 本地事务提交后事件已落库；本组件定时重扫，天然补偿"DB 成功但 MQ 失败/未确认"的场景。
 * <pre>
 *   查 PENDING(outbox) → convertAndSend(fanout) → 等 Publisher Confirm → SENT / FAILED
 * </pre>
 * <ul>
 *   <li><b>Publisher Confirm</b>：等待 Broker 持久化确认后再置 SENT，避免"发出即删"丢事件。
 *       依赖 {@code spring.rabbitmq.publisher-confirm-type=correlated}（application.yml，默认注释）。</li>
 *   <li><b>Mandatory/Return</b>：fanout 交换机不可路由场景少；不可路由消息触发 ReturnsCallback 记录。</li>
 *   <li><b>重复投递</b>：confirm 超时被标记 FAILED → 下次重扫重投，可能重复投递；
 *       由消费者 consumer_event 幂等兜底。</li>
 *   <li><b>事件回放</b>：event_outbox 保留不物理删除，Redis 全丢可据此重建；超期归档 MinIO（冷热分层）。</li>
 * </ul>
 * TODO[高并发]：本组件单实例扫描。多实例需先 SELECT ... FOR UPDATE SKIP LOCKED 或条件更新
 *   （UPDATE event_outbox SET status='SENDING' WHERE id=? AND status='PENDING'）防重复投递；
 *   分库分表后需按分片扫描（aggregate_id 分片）。
 */
public interface OutboxRelay {

    /** 扫描 PENDING 事件 → 投递 → 置 SENT（@Scheduled 触发） */
    void relay();
}
