package com.mware.storage.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mware.storage.domain.Stock;
import org.apache.ibatis.annotations.Mapper;

/**
 * 库存 Mapper（骨架占位）。
 * <p>
 * TODO[Seata AT 参与方]：接入 storage.mapper 依赖到 biz 层，
 * 并在 application.yml 启用数据源后生效。
 */
@Mapper
public interface StockMapper extends BaseMapper<Stock> {
}
