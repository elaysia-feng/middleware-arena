package com.mware.product.biz;

import com.mware.product.domain.Product;
import com.mware.product.dto.request.UpdateProductRequest;

/**
 * 商品业务接口。
 */
public interface ProductService {

    /** 查询商品（含单价 price，供 order 计算订单金额） */
    Product getProduct(Long productId);

    /** 修改商品信息（null 字段不更新） */
    Product updateProduct(Long productId, UpdateProductRequest request);

    /** 取消商品（删除） */
    void deleteProduct(Long productId);
}
