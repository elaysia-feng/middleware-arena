package com.mware.community.biz.consumer.impl;

import com.mware.community.biz.consumer.ConsumerIdempotency;
import com.mware.community.domain.ConsumerEvent;
import com.mware.community.mapper.ConsumerEventMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 消费者幂等守卫实现：先插后判（consumer_event 唯一键冲突 = 已处理过）。
 */
@Component
public class ConsumerIdempotencyImpl implements ConsumerIdempotency {

    private final ConsumerEventMapper consumerEventMapper;

    public ConsumerIdempotencyImpl(ConsumerEventMapper consumerEventMapper) {
        this.consumerEventMapper = consumerEventMapper;
    }

    @Override
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
