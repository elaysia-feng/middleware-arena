package com.mware.product.biz.impl;

import com.mware.common.web.ApiException;
import com.mware.common.web.ErrorCode;
import com.mware.product.biz.ProductService;
import com.mware.product.domain.Product;
import com.mware.product.dto.request.UpdateProductRequest;
import com.mware.product.mapper.ProductMapper;
import org.springframework.stereotype.Service;

/**
 * 商品业务实现。
 */
@Service
public class ProductServiceImpl implements ProductService {

    private final ProductMapper productMapper;

    public ProductServiceImpl(ProductMapper productMapper) {
        this.productMapper = productMapper;
    }

    @Override
    public Product getProduct(Long productId) {
        if (productId == null) {
            throw new ApiException(ErrorCode.PRODUCT_NOT_FOUND);
        }

        Product product = productMapper.selectById(productId);

        if (product == null) {
            throw new ApiException(ErrorCode.PRODUCT_NOT_FOUND);
        }

        return product;
    }

    @Override
    public Product updateProduct(Long productId, UpdateProductRequest request) {
        if (productId == null) {
            throw new ApiException(ErrorCode.PARAM_INVALID);
        }
        if (productMapper.selectById(productId) == null) {
            throw new ApiException(ErrorCode.PRODUCT_NOT_FOUND);
        }

        // null 字段不更新（MyBatis-Plus 默认字段策略 NOT_NULL）
        Product update = Product.builder()
                .id(productId)
                .name(request.getName())
                .price(request.getPrice())
                .description(request.getDescription())
                .build();
        productMapper.updateById(update);
        return productMapper.selectById(productId);
    }

    @Override
    public void deleteProduct(Long productId) {
        if (productId == null) {
            throw new ApiException(ErrorCode.PARAM_INVALID);
        }
        int rows = productMapper.deleteById(productId);
        if (rows == 0) {
            throw new ApiException(ErrorCode.PRODUCT_NOT_FOUND);
        }
    }
}
