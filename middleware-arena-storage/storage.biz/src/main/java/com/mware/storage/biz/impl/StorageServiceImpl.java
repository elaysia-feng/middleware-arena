package com.mware.storage.biz.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.mware.common.web.ApiException;
import com.mware.common.web.ErrorCode;
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
        // 1. 参数校验(> 0 才合法)
        if (productId == null || quantity == null || quantity <= 0) {
            throw new ApiException(ErrorCode.PARAM_INVALID);
        }

        // 2. 校验商品存在(按 product_id 查)
        Stock stock = stockMapper.selectOne(new LambdaQueryWrapper<Stock>()
                .eq(Stock::getProductId, productId));
        if (stock == null) {
            throw new ApiException(ErrorCode.NOT_FOUND);
        }

        // 3. 原子扣减:WHERE quantity >= ? 命中才扣,防超卖
        int rows = stockMapper.update(null, new LambdaUpdateWrapper<Stock>()
                .eq(Stock::getProductId, productId) // 定位商品
                .ge(Stock::getQuantity, quantity) // 够扣才执行
                .setSql("quantity = quantity - " + quantity)); // 扣减

        // 4. 0 行受影响 = 库存不足
        if (rows == 0) {
            throw new ApiException(ErrorCode.STOCK_NOT_ENOUGH);
        }
    }

    @Override
    public Stock getStock(Long productId) {
        // 1. stockMapper.selectOne(wrapper: product_id = productId)
        Stock stock = stockMapper.selectOne(new LambdaQueryWrapper<Stock>().eq(Stock::getProductId, productId));

        // 2. 商品不存在返回 null
        return stock;
    }
}
