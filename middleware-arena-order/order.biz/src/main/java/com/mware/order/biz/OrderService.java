package com.mware.order.biz;

/**
 * 订单业务接口。
 * <p>
 * TODO[Seata 分布式事务]：
 *   - 创建订单（@GlobalTransactional）
 *   - 调用 storage-service 扣库存（Feign）
 *   - 调用 account-service 扣余额（Feign）
 *   - Redis 缓存：订单详情读取 + 写入缓存
 *   - RabbitMQ：异步下单消息消费
 */
public interface OrderService {

}
