package com.mware.community.domain;

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
 * 消费者幂等实体（@TableName = consumer_event）。
 * <p>
 * event_id + consumer_name 唯一。RabbitMQ 可能重复投递（at-least-once），
 * 消费者先写本表：唯一键冲突 = 已消费过 → 跳过业务处理。避免"点赞数又 +1"。
 * <p>
 * consumer_name 取值与三个消费队列对齐：count / cache / statistics。
 */
@Data
@TableName("consumer_event")
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConsumerEvent {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 事件 ID（对齐 event_outbox.event_id） */
    private String eventId;

    /** 消费者名称：count / cache / statistics */
    private String consumerName;

    private LocalDateTime processedAt;
}
