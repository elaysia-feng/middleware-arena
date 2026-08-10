package com.mware.community.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mware.community.domain.EventOutbox;
import org.apache.ibatis.annotations.Mapper;

/**
 * Outbox 事件 Mapper（逻辑表 event_outbox，与 post_like 同分片）。
 * <p>
 * 使用方：OutboxRelay（扫 PENDING → 投递 → 置 SENT）、事件回放 / 归档任务。
 */
@Mapper
public interface EventOutboxMapper extends BaseMapper<EventOutbox> {
}
