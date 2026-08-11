package com.mware.storage.biz.impl;

import com.mware.storage.biz.StorageService;
import com.mware.storage.domain.Stock;
import com.mware.storage.mapper.StockMapper;
import org.springframework.stereotype.Service;

/**
 * 库存业务实现（骨架占位）。
 * <p>
 * TODO[Seata AT]：接入 storage.mapper 后逐个实现。
 */
@Service
public class StorageServiceImpl implements StorageService {

    private final StockMapper stockMapper;

    public StorageServiceImpl(StockMapper stockMapper) {
        this.stockMapper = stockMapper;
    }

    @Override
    public void deductStock(Long productId, Integer quantity) {
        // TODO[Seata AT]：扣减库存（被 order-service 通过 Feign 调用，Seata AT 参与方）
        //   1. 校验 quantity != null 且 > 0，非法抛 ApiException(PARAM_INVALID)
        //   2. stockMapper.selectOne 按 product_id 查库存，不存在抛 ApiException(NOT_FOUND)
        //   3. 比较库存与 quantity，不足抛 ApiException(STOCK_NOT_ENOUGH) 触发全局回滚
        //   4. 扣减后 updateById（或 UpdateWrapper 原子扣减：SET quantity = quantity - ?，WHERE quantity >= ?）
    }

    @Override
    public Stock getStock(Long productId) {
        // TODO[Seata AT]：按 product_id 查询库存
        //   1. stockMapper.selectOne(wrapper: product_id = productId)
        //   2. 商品不存在返回 null
        return null;
    }
}
