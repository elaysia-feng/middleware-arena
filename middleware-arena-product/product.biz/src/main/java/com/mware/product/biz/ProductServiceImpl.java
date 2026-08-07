package com.mware.product.biz;

import com.mware.product.domain.Product;
import com.mware.product.mapper.ProductMapper;
import org.springframework.stereotype.Service;

/**
 * 商品业务实现（骨架占位）。
 * <p>
 * TODO：接入 product.mapper 后实现，不存在时抛 ApiException(PRODUCT_NOT_FOUND)。
 */
@Service
public class ProductServiceImpl implements ProductService {

    private final ProductMapper productMapper;

    public ProductServiceImpl(ProductMapper productMapper) {
        this.productMapper = productMapper;
    }

    @Override
    public Product getProduct(Long productId) {
        return null; // TODO：productMapper.selectById(productId)
    }
}
