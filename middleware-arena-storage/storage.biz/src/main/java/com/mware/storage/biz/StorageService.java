package com.mware.storage.biz;

import com.mware.storage.domain.Stock;

/**
 * 库存业务接口。
 * <p>
 * 方法签名已定，具体实现留待接入 storage.mapper / Seata AT 后补齐。
 */
public interface StorageService {

    /** 扣减库存（被 order-service 通过 Feign 调用，Seata AT 参与方） */
    void deductStock(Long productId, Integer quantity);

    /** 查询库存 */
    Stock getStock(Long productId);
}
