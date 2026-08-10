package com.mware.community.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mware.community.domain.ConsumerEvent;
import org.apache.ibatis.annotations.Mapper;

/**
 * 消费者幂等 Mapper（event_id + consumer_name 唯一键）。
 * <p>
 * 使用方：ConsumerIdempotency（先插后判，冲突即重复投递）。
 */
@Mapper
public interface ConsumerEventMapper extends BaseMapper<ConsumerEvent> {
}
