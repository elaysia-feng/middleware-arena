package com.mware.product.biz;

import com.mware.product.domain.Product;

/**
 * 商品业务接口。
 * <p>
 * 方法签名已定，具体实现留待接入 product.mapper 后补齐。
 */
public interface ProductService {

    /** 查询商品（含单价 price，供 order 计算订单金额） */
    Product getProduct(Long productId);
}
