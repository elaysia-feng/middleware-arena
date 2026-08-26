package com.mware.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mware.order.domain.Order;
import org.apache.ibatis.annotations.Mapper;

/**
 * 订单 Mapper（骨架占位）。
 * <p>
 * TODO[Seata 分布式事务]：接入 order.mapper 依赖到 biz 层，
 * 并在 application.yml 启用数据源后生效。
 */
@Mapper
public interface OrderMapper extends BaseMapper<Order> {
}
