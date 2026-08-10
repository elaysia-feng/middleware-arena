package com.mware.community.biz.consumer;

import com.mware.community.domain.ConsumerEvent;
import com.mware.community.mapper.ConsumerEventMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 消费者幂等守卫（基于 consumer_event 表 event_id + consumer_name 唯一键）。
 * <p>
 * RabbitMQ 是 at-least-once：可能重复投递（网络抖动 / confirm 超时重投 / 消费者宕机重派）。
 * 三个消费者（count / cache / statistics）共用本守卫：先插后判，冲突即已处理过，跳过业务，避免"点赞数又 +1"。
 */
@Component
public class ConsumerIdempotency {

    public static final String CONSUMER_COUNT = "count";
    public static final String CONSUMER_CACHE = "cache";
    public static final String CONSUMER_STATISTICS = "statistics";

    private final ConsumerEventMapper consumerEventMapper;

    public ConsumerIdempotency(ConsumerEventMapper consumerEventMapper) {
        this.consumerEventMapper = consumerEventMapper;
    }

    /**
     * 幂等守卫。
     *
     * @return true 表示本事件首次被该消费者处理，可继续执行业务；false 表示重复投递，应跳过业务
     */
    public boolean markConsumed(String eventId, String consumerName) {
        try {
            consumerEventMapper.insert(ConsumerEvent.builder()
                    .eventId(eventId)
                    .consumerName(consumerName)
                    .processedAt(LocalDateTime.now())
                    .build());
            return true;
        } catch (DuplicateKeyException e) {
            // 唯一键 uk_event_consumer 冲突 = 该事件已被本消费者处理过
            return false;
        }
    }
}
