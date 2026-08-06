package com.mware.order.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 调用 storage-service 的 OpenFeign 客户端（占位）。
 * <p>
 * TODO[Seata 分布式事务]：下单时调用扣库存接口，接入 Seata AT 后
 * 需把 order.feign 依赖引入 order.web / order.biz，
 * 并在启动类加 @EnableFeignClients。
 */
@FeignClient(name = "storage-service")
public interface StorageClient {

    /**
     * 扣减库存（占位）。
     *
     * @param productId 商品 ID
     * @param quantity  扣减数量
     * @return 扣减结果
     */
    @PostMapping("/storage/deduct")
    String deductStock(@RequestParam("productId") Long productId,
                       @RequestParam("quantity") Integer quantity);
}
